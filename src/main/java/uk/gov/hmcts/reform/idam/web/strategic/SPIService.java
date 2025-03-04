package uk.gov.hmcts.reform.idam.web.strategic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.idam.api.internal.model.ActivationResult;
import uk.gov.hmcts.reform.idam.api.internal.model.ArrayOfServices;
import uk.gov.hmcts.reform.idam.api.internal.model.ErrorResponse;
import uk.gov.hmcts.reform.idam.api.internal.model.ForgotPasswordDetails;
import uk.gov.hmcts.reform.idam.api.internal.model.ResetPasswordRequest;
import uk.gov.hmcts.reform.idam.api.internal.model.ValidateRequest;
import uk.gov.hmcts.reform.idam.api.shared.model.User;
import uk.gov.hmcts.reform.idam.web.config.properties.ConfigurationProperties;
import uk.gov.hmcts.reform.idam.web.health.HealthCheckStatus;
import uk.gov.hmcts.reform.idam.web.model.RegisterUserRequest;
import uk.gov.hmcts.reform.idam.web.model.SelfRegisterRequest;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.netflix.zuul.constants.ZuulHeaders.X_FORWARDED_FOR;

@Slf4j
@Service
public class SPIService {

    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    private ConfigurationProperties configurationProperties;

    @Autowired
    public SPIService(RestTemplate restTemplate, ConfigurationProperties configurationProperties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.configurationProperties = configurationProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * @should call IDM with the right  body
     */
    public ResponseEntity<ActivationResult> validateActivationToken(final ValidateRequest activationJson) {
        HttpEntity<ValidateRequest> entity = new HttpEntity<>(activationJson);
        return restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getValidateActivationToken(), HttpMethod.POST, entity, ActivationResult.class);
    }

    /**
     * @should call api with the correct data
     */
    public ResponseEntity<ActivationResult> activateUser(final String activationJson) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(activationJson, headers);

        return restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getActivation(), HttpMethod.PATCH, entity, ActivationResult.class);
    }

    /**
     * @should call api with the correct data and return api response body if response code is 200
     * @should return api location in header in api response if response code is 302
     */
    public String uplift(final String username, final String password, final String jwt, final String redirectUri, final String clientId, final String state, final String scope) {
        ResponseEntity<String> response;
        long startTime = System.currentTimeMillis();

        log.warn("Uplift started");

        HttpEntity<MultiValueMap<String, String>> entity;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>(6);
        form.add("userName", username);
        form.add("password", password);
        form.add("jwt", jwt);
        form.add("redirectUri", redirectUri);
        form.add("clientId", clientId);
        form.add("state", state);
        form.add("scope", scope);

        entity = new HttpEntity<>(form, headers);

        response = restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getUplift(), HttpMethod.POST, entity,
            String.class);
        log.warn("Uplift ended at {}", System.currentTimeMillis() - startTime);

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        } else if (response.getStatusCode() == HttpStatus.FOUND) {
            return Optional.ofNullable(response.getHeaders().getLocation()).orElseThrow().toString();
        } else {
            return null;
        }
    }

    public ApiAuthResult authenticate(final String username, final String password, final String redirectUri, final String ipAddress) throws JsonProcessingException {
        return authenticate(username, password, null, redirectUri, ipAddress);
    }

    public ApiAuthResult authenticate(final String tokenId, final String redirectUri, final String ipAddress) throws JsonProcessingException {
        return authenticate(null, null, tokenId, redirectUri, ipAddress);
    }

    protected ApiAuthResult authenticate(final String username, final String password, final String tokenId, final String redirectUri, final String ipAddress) throws JsonProcessingException {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>(4);
        if (username != null) {
            form.add("username", username);
        }
        if (password != null) {
            form.add("password", password);
        }
        form.add("redirectUri", redirectUri);
        form.add("originIp", ipAddress);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(X_FORWARDED_FOR, ipAddress);
        if (tokenId != null) {
            headers.add(HttpHeaders.COOKIE, configurationProperties.getStrategic().getSession().getIdamSessionCookie() + "=" + tokenId);
        }

        final ApiAuthResult.ApiAuthResultBuilder resultBuilder = ApiAuthResult.builder();

        HttpStatus httpStatus;

        try {
            ResponseEntity<Object> response = restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" +
                configurationProperties.getStrategic().getEndpoint().getAuthorize(), HttpMethod.POST, new HttpEntity<>(form, headers), Object.class);
            httpStatus = response.getStatusCode();

            // check if already logged in or if requires MFA
            if (response.getStatusCode() == HttpStatus.OK) {
                final List<String> cookies = Optional.ofNullable(response.getHeaders().get(HttpHeaders.SET_COOKIE)).orElse(ImmutableList.of());

                final boolean requiresMfa = cookies.stream().anyMatch(cookie -> cookie.startsWith("Idam.AuthId"));

                resultBuilder.cookies(new ArrayList<>(cookies))
                    .policiesAction(requiresMfa ? EvaluatePoliciesAction.MFA_REQUIRED : EvaluatePoliciesAction.ALLOW);
            }
        } catch (HttpClientErrorException | HttpServerErrorException he) {
            httpStatus = he.getStatusCode();
            if (httpStatus == HttpStatus.FORBIDDEN || httpStatus == HttpStatus.UNAUTHORIZED || httpStatus == HttpStatus.NOT_FOUND) {
                ErrorResponse errorResponse = objectMapper.readValue(he.getResponseBodyAsString(), ErrorResponse.class);
                resultBuilder.errorCode(errorResponse.getCode());
            } else {
                throw he;
            }
        }

        resultBuilder.httpStatus(httpStatus);
        return resultBuilder.build();
    }

    /**
     * @should call api with the correct data and return location in header in api response if response code is 302
     * @should not send state and scope parameters in form if they are not send as parameter in the service
     * @should return null if api response code is not 302
     */
    public String authorize(final Map<String, String> params, final List<String> cookies) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (cookies != null) {
            headers.add(HttpHeaders.COOKIE, StringUtils.join(cookies, ";"));
        }
        addUriHeaders(headers);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>(14);
        params.forEach(form::add);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        ResponseEntity<String> response = restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic()
                .getEndpoint().getAuthorizeOauth2(),
            HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.FOUND) {
            return Optional.ofNullable(response.getHeaders().getLocation()).orElseThrow().toString();
        } else {
            return null;
        }
    }

    /**
     * @should return a set-cookie header to set Idam.Session if successful
     * @should return null if no cookie is found
     */
    public List<String> submitOtpeAuthentication(@NonNull final String authId, final String ipAddress,
                                                 @Nullable final String otp) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>(2);
        form.add("service", "otpe");
        form.add("authId", authId);

        if (otp != null) {
            form.add("otp", otp);
        }

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(X_FORWARDED_FOR, ipAddress);

        final String endpoint = String.format("%s/%s",
            configurationProperties.getStrategic().getService().getUrl(),
            configurationProperties.getStrategic().getEndpoint().getAuthorize());

        ResponseEntity<Void> response = restTemplate.exchange(endpoint, HttpMethod.POST,
            new HttpEntity<>(form, headers), Void.class);

        if (response.getHeaders().containsKey(HttpHeaders.SET_COOKIE)) {
            return new ArrayList<>(response.getHeaders().get(HttpHeaders.SET_COOKIE));
        } else {
            return null;
        }
    }

    private void addUriHeaders(HttpHeaders headers) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            final HttpServletRequest request = attributes.getRequest();
            UriComponents uriComponents = UriComponentsBuilder.
                fromUriString(request.getRequestURL().toString()).build();
            headers.add("x-forwarded-proto", uriComponents.getScheme());
            headers.add("x-forwarded-host", uriComponents.getHost());
            headers.add("x-forwarded-prefix", configurationProperties.getStrategic()
                .getService().getOidcprefix());
        }
    }

    public String loginWithPin(final String pin, final String redirectUri, final String state, final String clientId) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("pin", pin); //NOSONAR

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        StringBuilder sb = new StringBuilder(9);
        sb.append(configurationProperties.getStrategic().getService().getUrl());
        sb.append("/");
        sb.append(configurationProperties.getStrategic().getEndpoint().getLoginWithPin());
        sb.append("?redirect_uri=");
        sb.append(redirectUri);
        sb.append("&client_id=");
        sb.append(clientId);
        if (StringUtils.isNotEmpty(state)) {
            sb.append("&state=");
            sb.append(state);
        }
        String requestUrl = sb.toString();
        log.debug("Logging in with PIN to url: {}", redirectUri);

        ResponseEntity<String> response = getCustomRestTemplate().exchange(requestUrl, HttpMethod.GET, entity, String.class); // NOSONAR
        if (response.getStatusCode() == HttpStatus.FOUND) {
            return Optional.ofNullable(response.getHeaders().getLocation()).orElseThrow().toString();
        } else {
            throw new BadCredentialsException(response.getStatusCode().toString());
        }
    }

    private RestTemplate getCustomRestTemplate() {
        CloseableHttpClient httpClient = HttpClients.custom()
            .disableCookieManagement()
            .disableAuthCaching()
            .useSystemProperties()
            .setRedirectStrategy(new LaxRedirectStrategy() {
                @Override
                protected boolean isRedirectable(String method) {
                    return false;
                }
            }).build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectionRequestTimeout(5000);
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);

        return new RestTemplate(requestFactory);
    }

    /**
     * @should call api with the correct parameters
     * @should return 202 status code
     */
    public ResponseEntity<String> forgetPassword(final String email, final String redirectUri, final String clientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // need to pass locale here as the request will be executed in another thread
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, LocaleContextHolder.getLocale().toString());

        HttpEntity<ForgotPasswordDetails> entity = new HttpEntity<>(
            new ForgotPasswordDetails()
                .email(email)
                .redirectUri(redirectUri)
                .clientId(clientId),
            headers);

        CompletableFuture.supplyAsync(() -> restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getForgotPassword(), HttpMethod.POST, entity, String.class));
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    /**
     * @should call api with the correct data
     */
    public ResponseEntity<String> validateResetPasswordToken(final String token, final String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("token", token); //NOSONAR
        headers.add("code", code); //NOSONAR

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getValidateResetPasswordToken(), HttpMethod.POST, entity,
            String.class);
    }

    /**
     * @should call correct endpoint to reset password
     * @should register user with correct details
     * @should return what API call returns
     */
    public ResponseEntity<String> resetPassword(final String password, final String token, final String code) {

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setPassword(password);
        request.setToken(token);
        request.setCode(code);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ResetPasswordRequest> entity = new HttpEntity<>(request, headers);

        return restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getResetPassword(), HttpMethod.POST, entity,
            String.class);
    }

    /**
     * @should call correct endpoint to register user
     * @should register user with correct details
     * @should return what API call returns
     */
    public ResponseEntity<String> registerUser(RegisterUserRequest registerUserRequest) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        uk.gov.hmcts.reform.idam.api.shared.model.SelfRegisterRequest request = new uk.gov.hmcts.reform.idam.api.shared.model.SelfRegisterRequest();
        request.setFirstName(registerUserRequest.getFirstName());
        request.setLastName(registerUserRequest.getLastName());
        request.setEmail(registerUserRequest.getUsername());
        request.setClientId(registerUserRequest.getClient_id());
        request.setRedirectUri(registerUserRequest.getRedirect_uri());
        request.setState(registerUserRequest.getState());

        HttpEntity<uk.gov.hmcts.reform.idam.api.shared.model.SelfRegisterRequest> requestEntity = new HttpEntity<>(request, headers);
        return restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getSelfRegisterUser() + "?jwt=" + registerUserRequest.getJwt(), HttpMethod.POST, requestEntity, String.class);
    }

    /**
     * @should call api with the correct data
     */
    public ResponseEntity<String> selfRegisterUser(SelfRegisterRequest selfRegisterRequest) throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(selfRegisterRequest), headers);

        return restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getSelfRegistration(), HttpMethod.POST, entity, String.class);
    }

    /**
     * @should call api health check
     */
    public ResponseEntity<HealthCheckStatus> healthCheck() {
        return restTemplate.getForEntity(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getHealth(), HealthCheckStatus.class);
    }

    /**
     * @should call api with the correct data and return api response if status code is 200
     * @should return optional empty if status code is not 200
     * @should return optional empty if any Exception occurs
     */
    public Optional<User> getDetails(String token) {

        ResponseEntity<User> response;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("authorization", token); //NOSONAR

        HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

        try {
            response = restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getDetails(), HttpMethod.GET, entity, User.class);
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.error("Error getting User Details", e);
            return Optional.empty();
        }
    }

    /**
     * @should call api with the correct data and return the service if api response is not empty and http status code is 200
     * @should return Optional empty if api returns an http status different from 200
     * @should return Optional empty if api returns empty response body
     */
    public Optional<uk.gov.hmcts.reform.idam.api.internal.model.Service> getServiceByClientId(String clientId) {

        ResponseEntity<ArrayOfServices> response =
            restTemplate.exchange(configurationProperties.getStrategic().getService().getUrl() + "/" + configurationProperties.getStrategic().getEndpoint().getServices() + "?clientId=" + clientId, HttpMethod.GET, HttpEntity.EMPTY, ArrayOfServices.class); //NOSONAR

        final ArrayOfServices responseBody = response.getBody();
        if (responseBody != null && !responseBody.isEmpty()) {
            return Optional.of(responseBody.get(0));
        }
        return Optional.empty();
    }
}

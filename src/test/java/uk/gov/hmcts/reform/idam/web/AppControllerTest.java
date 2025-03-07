package uk.gov.hmcts.reform.idam.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.CharEncoding;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import uk.gov.hmcts.reform.idam.api.internal.model.ErrorResponse;
import uk.gov.hmcts.reform.idam.api.internal.model.ForgotPasswordDetails;
import uk.gov.hmcts.reform.idam.api.internal.model.Service;
import uk.gov.hmcts.reform.idam.web.config.properties.ConfigurationProperties;
import uk.gov.hmcts.reform.idam.web.helper.MvcKeys;
import uk.gov.hmcts.reform.idam.web.model.AuthorizeRequest;
import uk.gov.hmcts.reform.idam.web.model.RegisterUserRequest;
import uk.gov.hmcts.reform.idam.web.model.UpliftRequest;
import uk.gov.hmcts.reform.idam.web.sso.SSOService;
import uk.gov.hmcts.reform.idam.web.strategic.ApiAuthResult;
import uk.gov.hmcts.reform.idam.web.strategic.EvaluatePoliciesAction;
import uk.gov.hmcts.reform.idam.web.strategic.SPIService;
import uk.gov.hmcts.reform.idam.web.strategic.ValidationService;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.netflix.zuul.constants.ZuulHeaders.X_FORWARDED_FOR;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.AZURE_LOGIN_ENABLED;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.CONTACT_US_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.COOKIES_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.HAS_OTP_CHECK_FAILED;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.PASSWORD;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.PRIVACY_POLICY_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.STALE_USER_RESET_PASSWORD_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.TACTICAL_RESET_PWD_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.TERMS_AND_CONDITIONS_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.UPLIFT_LOGIN_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.UPLIFT_REGISTER_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.USERNAME;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.VERIFICATION_VIEW;
import static uk.gov.hmcts.reform.idam.web.helper.MvcKeys.EXPIRED_CODE_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ACTION_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.AUTHENTICATE_SESSION_COOKE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.BLANK;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.CLIENTID_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.CLIENT_ID;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.CLIENT_ID_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.CODE_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.CUSTOM_SCOPE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.DO_RESET_PASSWORD_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_BLACKLISTED_PASSWORD;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_CAPITAL;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_CONTAINS_PERSONAL_INFO_PASSWORD;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_ENTER_PASSWORD;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_INVALID_PASSWORD;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_LABEL_ONE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_LABEL_TWO;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_MESSAGE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_MSG;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_PASSWORD_DETAILS;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_PREVIOUSLY_USED_PASSWORD;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_TITLE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERROR_VIEW_NAME;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.ERR_LOCKED_FAILED_RESPONSE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.EXPIREDTOKEN_REDIRECTED_VIEW_NAME;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.EXPIRED_PASSWORD_RESET_TOKEN_VIEW_NAME;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.EXPIRED_TOKEN_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.FORGOT_PASSWORD_COMMAND_NAME;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.FORGOT_PASSWORD_SUCCESS_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.FORGOT_PASSWORD_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.FORGOT_PASSWORD_WEB_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.HAS_LOGIN_FAILED;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.HAS_LOGIN_FAILED_RESPONSE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.INDEX_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.INFORMATION_IS_MISSING_OR_INVALID;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.IS_ACCOUNT_LOCKED;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.IS_ACCOUNT_SUSPENDED;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.JWT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.JWT_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.LOGIN_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.LOGIN_LOGOUT_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.LOGIN_PIN_CODE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.LOGIN_PIN_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.LOGIN_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.LOGIN_WITH_PIN_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.LOGIN_WITH_PIN_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.LOGOUT_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.MISSING;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.NONCE_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.NONCE_VALUE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.NOT_FOUND_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PASSWORD_BLACKLISTED_RESPONSE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PASSWORD_CONTAINS_PERSONAL_INFO_RESPONSE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PASSWORD_ONE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PASSWORD_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PASSWORD_RESET_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PASSWORD_TWO;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PIN_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PIN_USER_NOT_LONGER_VALID;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PLEASE_FIX_THE_FOLLOWING;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PLEASE_TRY_AGAIN;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PROMPT_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.PROMPT_VALUE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.REDIRECTURI;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.REDIRECT_URI;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESETPASSWORD_VIEW_NAME;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESET_FORGOT_PASSWORD_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESET_INACTIVE_USER_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESET_PASSWORD_CODE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESET_PASSWORD_RESPONSE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESET_PASSWORD_SUCCESS_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESET_PASSWORD_TOKEN;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESPONSE_TYPE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.RESPONSE_TYPE_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.SCOPE_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.SECURITY_CODE_INCORRECT_ERROR;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.SELF_REGISTRATION_ENABLED;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.SORRY_THERE_WAS_AN_ERROR;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.STATE;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.STATE_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.TACTICAL_ACTIVATE_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.TACTICAL_ACTIVATE_VIEW;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.TACTICAL_RESET_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.TOKEN_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.UNUSED;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.UPLIFT_LOGIN_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.UPLIFT_REGISTER_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USERNAME_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_CREATED_VIEW_NAME;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_EMAIL;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_EMAIL_INVALID;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_EMAIL_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_FIRST_NAME;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_FIRST_NAME_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_IP_ADDRESS;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_LAST_NAME;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_LAST_NAME_PARAMETER;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.USER_PASSWORD;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.VALID_SECURITY_CODE_ERROR;
import static uk.gov.hmcts.reform.idam.web.util.TestConstants.VERIFICATION_ENDPOINT;
import static uk.gov.hmcts.reform.idam.web.util.TestHelper.anAuthorizedUser;

@RunWith(SpringRunner.class)
@WebMvcTest(AppController.class)
@TestPropertySource(properties = "testing=true")
public class AppControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SPIService spiService;

    @MockBean
    private SSOService ssoService;

    @MockBean
    private ValidationService validationService;

    @Autowired
    private ConfigurationProperties configurationProperties;

    /**
     * @verifies return index view
     * @see AppController#indexView(Map)
     */
    @Test
    public void indexView_shouldReturnIndexView() throws Exception {
        mockMvc.perform(get("/"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(INDEX_VIEW));
    }

    /**
     * @verifies put correct data in model and return login view
     * @see AppController#loginView(AuthorizeRequest, BindingResult, Model)
     */
    @Test
    public void loginView_shouldPutCorrectDataInModelAndReturnLoginView() throws Exception {

        Service service = new Service();
        service.selfRegistrationAllowed(true);

        given(spiService.getServiceByClientId(CLIENT_ID)).willReturn(Optional.of(service));

        mockMvc.perform(get(LOGIN_ENDPOINT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(model().attribute(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE))
            .andExpect(model().attribute(STATE_PARAMETER, STATE))
            .andExpect(model().attribute(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(model().attribute(REDIRECT_URI, REDIRECT_URI))
            .andExpect(view().name(LOGIN_VIEW));
    }

    /**
     * @verifies set self registration to false if disabled for the service
     * @see AppController#loginView(AuthorizeRequest, BindingResult, Model)
     */
    @Test
    public void loginView_shouldSetSelfRegistrationToFalseIfDisabledForTheService() throws Exception {

        Service service = new Service();
        service.selfRegistrationAllowed(false);

        given(spiService.getServiceByClientId(CLIENT_ID)).willReturn(Optional.of(service));

        mockMvc.perform(get(LOGIN_ENDPOINT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(SELF_REGISTRATION_ENABLED, false))
            .andExpect(view().name(LOGIN_VIEW));
    }

    /**
     * @verifies set self registration to false if the clientId is invalid
     * @see AppController#loginView(AuthorizeRequest, BindingResult, Model)
     */
    @Test
    public void loginView_shouldSetSelfRegistrationToFalseIfTheClientIdIsInvalid() throws Exception {

        given(spiService.getServiceByClientId(CLIENT_ID)).willReturn(Optional.empty());

        mockMvc.perform(get(LOGIN_ENDPOINT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(SELF_REGISTRATION_ENABLED, false))
            .andExpect(view().name(LOGIN_VIEW));
    }

    /**
     * @verifies put correct data in model and return login view
     * @see AppController#loginView(AuthorizeRequest, BindingResult, Model)
     */
    @Test
    public void loginView_shouldPutCorrectDataInModelAndReturnLoginViewWithAzureLoginEnabled() throws Exception {

        Service service = new Service();
        service.selfRegistrationAllowed(true);
        service.setSsoProviders(List.of("ejudiciary-aad"));

        //&& configurationProperties.getFeatures().isFederatedSSO()) {
        //                    model.addAttribute(AZURE_LOGIN_ENABLED, true);

        given(spiService.getServiceByClientId(CLIENT_ID)).willReturn(Optional.of(service));

        mockMvc.perform(get(LOGIN_ENDPOINT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(model().attribute(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE))
            .andExpect(model().attribute(STATE_PARAMETER, STATE))
            .andExpect(model().attribute(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(model().attribute(REDIRECT_URI, REDIRECT_URI))
            .andExpect(model().attribute(AZURE_LOGIN_ENABLED, true))
            .andExpect(view().name(LOGIN_VIEW));
    }

    /**
     * @verifies return expired token view
     * @see AppController#expiredTokenView(Map)
     */
    @Test
    public void expiredTokenView_shouldReturnExpiredTokenView() throws Exception {
        mockMvc.perform(get(EXPIRED_TOKEN_ENDPOINT))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(EXPIRED_PASSWORD_RESET_TOKEN_VIEW_NAME));
    }

    /**
     * @verifies return login with pin view
     * @see AppController#loginWithPinView(Map)
     */
    @Test
    public void loginWithPinView_shouldReturnLoginWithPinView() throws Exception {
        mockMvc.perform(get(LOGIN_PIN_ENDPOINT))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(LOGIN_WITH_PIN_VIEW));
    }

    /**
     * @verifies put right error data in model if mandatory fields are missing and return upliftUser view
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldPutRightErrorDataInModelIfMandatoryFieldsAreMissingAndReturnUpliftUserView() throws Exception {

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, MISSING)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, INFORMATION_IS_MISSING_OR_INVALID))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_FIX_THE_FOLLOWING))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));
    }

    /**
     * @verifies return upliftUser view if register user service returns http code different from 201
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldReturnUpliftUserViewIfRegisterUserServiceReturnsHttpCodeDifferentFrom201() throws Exception {

        given(spiService.registerUser(eq(aRegisterUserRequest()))).willReturn(ResponseEntity.badRequest().build());

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECTURI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENTID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));
    }

    private RegisterUserRequest aRegisterUserRequest() {
        RegisterUserRequest registerUserRequest = new RegisterUserRequest();
        registerUserRequest.setClient_id(CLIENT_ID);
        registerUserRequest.setRedirect_uri(REDIRECT_URI);
        registerUserRequest.setFirstName(USER_FIRST_NAME);
        registerUserRequest.setLastName(USER_LAST_NAME);
        registerUserRequest.setJwt(JWT);
        registerUserRequest.setUsername(USER_EMAIL);
        registerUserRequest.setState(STATE);
        return registerUserRequest;
    }

    /**
     * @verifies put email in model and return usercreated view if register user service returns http code 201
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldPutEmailInModelAndReturnUsercreatedViewIfRegisterUserServiceReturnsHttpCode201() throws Exception {
        given(spiService.registerUser(eq(aRegisterUserRequest()))).willReturn(ResponseEntity.status(HttpStatus.CREATED).build());

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(USER_EMAIL_PARAMETER, USER_EMAIL))
            .andExpect(view().name(USER_CREATED_VIEW_NAME));
    }


    /**
     * @verifies put right error data in model if register user service throws HttpClientErrorException with 404 http status code
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldPutRightErrorDataInModelIfRegisterUserServiceThrowsHttpClientErrorExceptionWith404HttpStatusCode() throws Exception {
        given(spiService.registerUser(eq(aRegisterUserRequest()))).willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, SORRY_THERE_WAS_AN_ERROR))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_TRY_AGAIN + PIN_USER_NOT_LONGER_VALID))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));

    }


    /**
     * @verifies redirects to "reset/inactive-user" on registration 404 with STALE_USER_REGISTRATION_SENT error
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_redirectToResetInactiveUserOnRegistration404WithStaleUserRegistrationSentError() throws Exception {
        byte[] staleUserErrorBytes = ErrorResponse.CodeEnum.STALE_USER_REGISTRATION_SENT.toString().getBytes(StandardCharsets.UTF_8);

        given(spiService.registerUser(eq(aRegisterUserRequest())))
            .willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND,
                HttpStatus.NOT_FOUND.getReasonPhrase(), staleUserErrorBytes, StandardCharsets.UTF_8));

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/reset/inactive-user?client_id=clientId&redirect_uri=redirect_uri&state=state+test"));

    }

    /**
     * @verifies put generic error data in model if register user service throws HttpClientErrorException an http status code different from 404
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldPutGenericErrorDataInModelIfRegisterUserServiceThrowsHttpClientErrorExceptionAnHttpStatusCodeDifferentFrom404() throws Exception {
        given(spiService.registerUser(eq(aRegisterUserRequest()))).willThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, SORRY_THERE_WAS_AN_ERROR))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_TRY_AGAIN))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));

    }

    /**
     * @verifies reject request if the username is invalid
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldRejectRequestIfTheUsernameIsInvalid() throws Exception {

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL_INVALID)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, INFORMATION_IS_MISSING_OR_INVALID))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_FIX_THE_FOLLOWING))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));

    }

    /**
     * @verifies reject request if the first name is missing
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldRejectRequestIfTheFirstNameIsMissing() throws Exception {

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, MISSING)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, INFORMATION_IS_MISSING_OR_INVALID))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_FIX_THE_FOLLOWING))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));

    }

    /**
     * @verifies reject request if the last name is missing
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldRejectRequestIfTheLastNameIsMissing() throws Exception {

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, MISSING)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, INFORMATION_IS_MISSING_OR_INVALID))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_FIX_THE_FOLLOWING))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));
    }

    /**
     * @verifies reject request if the jwt is missing
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldRejectRequestIfTheJwtIsMissing() throws Exception {

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, MISSING)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, INFORMATION_IS_MISSING_OR_INVALID))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_FIX_THE_FOLLOWING))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));
    }

    /**
     * @verifies reject request if the redirect URI is missing
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldRejectRequestIfTheRedirectURIIsMissing() throws Exception {

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECTURI, MISSING)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENTID_PARAMETER, CLIENT_ID)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, INFORMATION_IS_MISSING_OR_INVALID))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_FIX_THE_FOLLOWING))
            .andExpect(model().attribute(REDIRECTURI, MISSING))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));

    }

    /**
     * @verifies reject request if the clientId is missing
     * @see #upliftRegister(RegisterUserRequest, BindingResult, Map
     */
    @Test
    public void upliftRegister_shouldRejectRequestIfTheClientIdIsMissing() throws Exception {

        mockMvc.perform(post(UPLIFT_REGISTER_ENDPOINT).with(csrf())
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, MISSING)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(USER_FIRST_NAME_PARAMETER, USER_FIRST_NAME)
            .param(USER_LAST_NAME_PARAMETER, USER_LAST_NAME)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, INFORMATION_IS_MISSING_OR_INVALID))
            .andExpect(model().attribute(ERROR_MESSAGE, PLEASE_FIX_THE_FOLLOWING))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));
    }

    /**
     * @verifies uplift user
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldUpliftUser() throws Exception {

        given(spiService.uplift(USER_EMAIL, USER_PASSWORD, JWT, REDIRECT_URI, CLIENT_ID, STATE, CUSTOM_SCOPE)).willReturn("upliftResult");

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isFound())
            .andExpect(view().name("redirect:upliftResult"));

        verify(spiService).uplift(USER_EMAIL, USER_PASSWORD, JWT, REDIRECT_URI, CLIENT_ID, STATE, CUSTOM_SCOPE);
    }

    /**
     * @verifies reject request if username is not provided
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldRejectRequestIfUsernameIsNotProvided() throws Exception {

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, MISSING)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", USERNAME_PARAMETER));

        verify(spiService, never()).uplift(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /**
     * @verifies reject request if username is invalid
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldRejectRequestIfUsernameIsInvalid() throws Exception {

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, "inval!d@email.com")
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", USERNAME_PARAMETER));

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, "inval(d@email.com")
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", USERNAME_PARAMETER));

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, "inval)d@email.com")
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", USERNAME_PARAMETER));

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, "inval%d@email.com")
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", USERNAME_PARAMETER));

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, "inval&d@email.com")
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", USERNAME_PARAMETER));

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, "inval;d@email.com")
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", USERNAME_PARAMETER));

        verify(spiService, never()).uplift(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /**
     * @verifies reject request if password is not provided
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldRejectRequestIfPasswordIsNotProvided() throws Exception {

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, MISSING)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECTURI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENTID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", PASSWORD_PARAMETER));

        verify(spiService, never()).uplift(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /**
     * @verifies reject request if JWT is not provided
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldRejectRequestIfJWTIsNotProvided() throws Exception {

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, MISSING)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", JWT_PARAMETER));

        verify(spiService, never()).uplift(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /**
     * @verifies reject request if redirectUri is not provided
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldRejectRequestIfRedirectUriIsNotProvided() throws Exception {

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECTURI, MISSING)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENTID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", REDIRECT_URI));

        verify(spiService, never()).uplift(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /**
     * @verifies reject request if clientId is not provided
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldRejectRequestIfClientIdIsNotProvided() throws Exception {

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, MISSING))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attributeHasFieldErrors("upliftRequest", CLIENT_ID_PARAMETER));

        verify(spiService, never()).uplift(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    /**
     * @verifies return to the registration page if the credentials are invalid
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldReturnToTheRegistrationPageIfTheCredentialsAreInvalid() throws Exception {

        given(spiService.uplift(USER_EMAIL, USER_PASSWORD, JWT, REDIRECT_URI, CLIENT_ID, STATE, CUSTOM_SCOPE))
            .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(view().name(UPLIFT_LOGIN_VIEW));

        verify(spiService).uplift(USER_EMAIL, USER_PASSWORD, JWT, REDIRECT_URI, CLIENT_ID, STATE, CUSTOM_SCOPE);
    }

    /**
     * @verifies return error page view if OAuth2 details are missing
     * @see AppController#loginView(AuthorizeRequest, BindingResult, Model)
     */
    @Test
    public void loginView_shouldReturnErrorPageViewIfOAuth2DetailsAreMissing() throws Exception {
        mockMvc.perform(get(LOGIN_ENDPOINT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(CLIENT_ID_PARAMETER, MISSING))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR_MSG, "error.page.access.denied"))
            .andExpect(view().name(ERROR_VIEW_NAME));
    }

    /**
     * @verifies return to the registration page if there is an exception
     * @see AppController#upliftLogin(UpliftRequest, BindingResult, Map, ModelMap)
     */
    @Test
    public void upliftLogin_shouldReturnToTheRegistrationPageIfThereIsAnException() throws Exception {

        given(spiService.uplift(USER_EMAIL, USER_PASSWORD, JWT, REDIRECT_URI, CLIENT_ID, STATE, CUSTOM_SCOPE)).willThrow(new RuntimeException());

        mockMvc.perform(post(UPLIFT_LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(JWT_PARAMETER, JWT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(view().name(UPLIFT_LOGIN_VIEW));

        verify(spiService).uplift(USER_EMAIL, USER_PASSWORD, JWT, REDIRECT_URI, CLIENT_ID, STATE, CUSTOM_SCOPE);
    }

    /**
     * @verifies return user uplift page if the user is authorized
     * @see AppController#upliftRegisterView(String, String, String, RegisterUserRequest, Map)
     */
    @Test
    public void upliftRegisterView_shouldReturnUserUpliftPageIfTheUserIsAuthorized() throws Exception {

        given(spiService.getDetails(JWT)).willReturn(Optional.of(anAuthorizedUser()));

        mockMvc.perform(get(UPLIFT_REGISTER_ENDPOINT)
            .param(JWT_PARAMETER, JWT)
            .param(CLIENT_ID_PARAMETER, "abc")
            .param(REDIRECT_URI, "http://localhost"))
            .andExpect(status().isOk())
            .andExpect(view().name(UPLIFT_REGISTER_VIEW));

        verify(spiService).getDetails(JWT);
    }

    /**
     * @verifies return error page if the user is not authorized
     * @see AppController#upliftRegisterView(String, String, String, RegisterUserRequest, Map)
     */
    @Test
    public void upliftRegisterView_shouldReturnErrorPageIfTheUserIsNotAuthorized() throws Exception {

        given(spiService.getDetails(JWT)).willReturn(Optional.empty());

        mockMvc.perform(get(UPLIFT_REGISTER_ENDPOINT)
            .param(JWT_PARAMETER, JWT)
            .param(CLIENT_ID_PARAMETER, "abc")
            .param(REDIRECT_URI, "http://localhost"))
            .andExpect(status().isOk())
            .andExpect(view().name(ERROR_VIEW_NAME));

        verify(spiService).getDetails(JWT);
    }

    /**
     * @verifies return user registration page if the user is authorized
     * @see AppController#upliftLoginView(String, String, String, Map)
     */
    @Test
    public void upliftLoginView_shouldReturnUserRegistrationPageIfTheUserIsAuthorized() throws Exception {

        given(spiService.getDetails(JWT)).willReturn(Optional.of(anAuthorizedUser()));

        mockMvc.perform(get(UPLIFT_LOGIN_ENDPOINT)
            .param(JWT_PARAMETER, JWT)
            .param(CLIENT_ID_PARAMETER, "abc")
            .param(REDIRECT_URI, "http://localhost"))
            .andExpect(status().isOk())
            .andExpect(view().name(UPLIFT_LOGIN_VIEW));

        verify(spiService).getDetails(JWT);
    }

    /**
     * @verifies return error page if the user is not authorized
     * @see AppController#upliftLoginView(String, String, String, Map)
     */
    @Test
    public void upliftLoginView_shouldReturnErrorPageIfTheUserIsNotAuthorized() throws Exception {

        given(spiService.getDetails(JWT)).willReturn(Optional.empty());

        mockMvc.perform(get(UPLIFT_LOGIN_ENDPOINT)
            .param(JWT_PARAMETER, JWT)
            .param(CLIENT_ID_PARAMETER, "abc")
            .param(REDIRECT_URI, "http://localhost"))
            .andExpect(status().isOk())
            .andExpect(view().name(NOT_FOUND_VIEW));

        verify(spiService).getDetails(JWT);
    }

    /**
     * @verifies redirect to reset password page if token is valid
     * @see AppController#passwordReset(String, String, Model)
     */
    @Test
    public void passwordReset_shouldRedirectToResetPasswordPageIfTokenIsValid() throws Exception {

        given(spiService.validateResetPasswordToken(RESET_PASSWORD_TOKEN, RESET_PASSWORD_CODE)).willReturn(ResponseEntity.ok("{irrelevant}"));

        mockMvc.perform(post(PASSWORD_RESET_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(view().name(RESETPASSWORD_VIEW_NAME));

        verify(spiService).validateResetPasswordToken(RESET_PASSWORD_TOKEN, RESET_PASSWORD_CODE);
    }

    /**
     * @verifies redirect to token expired page if token is invalid
     * @see AppController#passwordReset(String, String, Model)
     */
    @Test
    public void passwordReset_shouldRedirectToTokenExpiredPageIfTokenIsInvalid() throws Exception {

        given(spiService.validateResetPasswordToken(RESET_PASSWORD_TOKEN, RESET_PASSWORD_CODE)).willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        mockMvc.perform(post(PASSWORD_RESET_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(view().name(EXPIRED_PASSWORD_RESET_TOKEN_VIEW_NAME));

        verify(spiService).validateResetPasswordToken(RESET_PASSWORD_TOKEN, RESET_PASSWORD_CODE);
    }

    /**
     * @verifies redirect to token expired page if token is expired
     * @see AppController#passwordReset(String, String, Model)
     */
    @Test
    public void passwordReset_shouldRedirectToTokenExpiredPageIfTokenIsExpired() throws Exception {
        byte[] body = new ObjectMapper().writeValueAsBytes(new ForgotPasswordDetails().redirectUri("1234"));
        given(spiService.validateResetPasswordToken(RESET_PASSWORD_TOKEN, RESET_PASSWORD_CODE))
            .willThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, body, StandardCharsets.UTF_8));

        mockMvc.perform(post(PASSWORD_RESET_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(view().name(EXPIRED_PASSWORD_RESET_TOKEN_VIEW_NAME))
            .andExpect(model().attribute("forgotPasswordLink",
                "/reset/forgotpassword?redirectUri=1234&clientId=&state=&scope="));

        verify(spiService).validateResetPasswordToken(RESET_PASSWORD_TOKEN, RESET_PASSWORD_CODE);
    }

    /**
     * @verifies put in model redirect uri if service returns http 200 and redirect uri is present in response then return reset password success view
     * @see AppController#resetPassword(String, String, String, String, String, Map)
     */
    @Test
    public void resetPassword_shouldPutInModelRedirectUriIfServiceReturnsHttp200AndRedirectUriIsPresentInResponseThenReturnResetPasswordSuccessView() throws Exception {
        given(validationService.validatePassword(eq(PASSWORD_ONE), eq(PASSWORD_TWO), any(Map.class))).willReturn(true);
        given(spiService.resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE))).willReturn(ResponseEntity.ok(RESET_PASSWORD_RESPONSE));

        mockMvc.perform(post(DO_RESET_PASSWORD_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(PASSWORD_ONE, PASSWORD_ONE)
            .param(PASSWORD_TWO, PASSWORD_TWO)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(model().attribute(REDIRECTURI, REDIRECTURI))
            .andExpect(view().name(RESET_PASSWORD_SUCCESS_VIEW));

        verify(spiService).resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE));
    }

    /**
     * @verifies not put redirect uri in model if service returns http 200 and redirect uri is not present in response then return reset password success view
     * @see AppController#resetPassword(String, String, String, String, String, Map)
     */
    @Test
    public void resetPassword_shouldNotPutRedirectUriInModeIfServiceReturnsHttp200AndRedirectUriIsNotPresentInResponseThenReturnResetPasswordSuccessView() throws Exception {
        given(validationService.validatePassword(eq(PASSWORD_ONE), eq(PASSWORD_TWO), any(Map.class))).willReturn(true);
        given(spiService.resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE))).willReturn(ResponseEntity.ok("{}"));

        mockMvc.perform(post(DO_RESET_PASSWORD_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(PASSWORD_ONE, PASSWORD_ONE)
            .param(PASSWORD_TWO, PASSWORD_TWO)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(model().attributeDoesNotExist(REDIRECTURI))
            .andExpect(view().name(RESET_PASSWORD_SUCCESS_VIEW));

        verify(spiService).resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE));
    }

    /**
     * @verifies put in model the correct error code if HttpClientErrorException with http 412 is thrown by service then return reset password view.
     * @see AppController#resetPassword(String, String, String, String, String, Map)
     */
    @Test
    public void resetPassword_shouldPutInModelTheCorrectErrorCodeIfHttpClientErrorExceptionWithHttp412IsThrownByServiceThenReturnResetPasswordView() throws Exception {
        given(validationService.validatePassword(eq(PASSWORD_ONE), eq(PASSWORD_TWO), any(Map.class))).willReturn(true);
        given(spiService.resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE))).willThrow(new HttpClientErrorException(HttpStatus.PRECONDITION_FAILED));


        mockMvc.perform(post(DO_RESET_PASSWORD_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(PASSWORD_ONE, PASSWORD_ONE)
            .param(PASSWORD_TWO, PASSWORD_TWO)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, ERROR_CAPITAL))
            .andExpect(model().attribute(ERROR_MESSAGE, ERROR_INVALID_PASSWORD))
            .andExpect(model().attribute(ERROR_LABEL_ONE, ERROR_INVALID_PASSWORD))
            .andExpect(model().attribute(ERROR_LABEL_TWO, BLANK))
            .andExpect(view().name(RESETPASSWORD_VIEW_NAME));

        verify(spiService).resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE));
    }

    /**
     * @verifies put in model the correct error code if HttpClientErrorException with http 400 is thrown by service and password is blacklisted then return reset password view.
     * @see AppController#resetPassword(String, String, String, String, String, Map)
     */
    @Test
    public void resetPassword_shouldPutInModelTheCorrectErrorCodeIfHttpClientErrorExceptionWithHttp400IsThrownByServiceAndPasswordIsBlacklistedThenReturnResetPasswordView() throws Exception {
        given(validationService.validatePassword(eq(PASSWORD_ONE), eq(PASSWORD_TWO), any(Map.class))).willReturn(true);
        given(spiService.resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE))).willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.name(), PASSWORD_BLACKLISTED_RESPONSE.getBytes(), null));
        given(validationService.isErrorInResponse(eq(PASSWORD_BLACKLISTED_RESPONSE), eq(ErrorResponse.CodeEnum.PASSWORD_BLACKLISTED))).willReturn(true);


        mockMvc.perform(post(DO_RESET_PASSWORD_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(PASSWORD_ONE, PASSWORD_ONE)
            .param(PASSWORD_TWO, PASSWORD_TWO)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, ERROR_CAPITAL))
            .andExpect(model().attribute(ERROR_MESSAGE, ERROR_BLACKLISTED_PASSWORD))
            .andExpect(model().attribute(ERROR_LABEL_ONE, ERROR_BLACKLISTED_PASSWORD))
            .andExpect(model().attribute(ERROR_LABEL_TWO, ERROR_ENTER_PASSWORD))
            .andExpect(view().name(RESETPASSWORD_VIEW_NAME));

        verify(spiService).resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE));
    }

    /**
     * @verifies put in model the correct error code if HttpClientErrorException with http 400 is thrown by service and password contains then return reset password view.
     * @see AppController#resetPassword(String, String, String, String, String, Map)
     */
    @Test
    public void resetPassword_shouldPutInModelTheCorrectErrorCodeIfHttpClientErrorExceptionWithHttp400IsThrownByServiceAndPasswordContainsThenReturnResetPasswordView() throws Exception {
        given(validationService.validatePassword(eq(PASSWORD_ONE), eq(PASSWORD_TWO), any(Map.class)))
            .willReturn(true);
        given(spiService.resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE)))
            .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.name(), PASSWORD_CONTAINS_PERSONAL_INFO_RESPONSE.getBytes(), null));
        given(validationService.isErrorInResponse(eq(PASSWORD_CONTAINS_PERSONAL_INFO_RESPONSE), eq(ErrorResponse.CodeEnum.PASSWORD_CONTAINS_PERSONAL_INFO)))
            .willReturn(true);


        mockMvc.perform(post(DO_RESET_PASSWORD_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(PASSWORD_ONE, PASSWORD_ONE)
            .param(PASSWORD_TWO, PASSWORD_TWO)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, ERROR_CAPITAL))
            .andExpect(model().attribute(ERROR_MESSAGE, ERROR_CONTAINS_PERSONAL_INFO_PASSWORD))
            .andExpect(model().attribute(ERROR_LABEL_ONE, ERROR_CONTAINS_PERSONAL_INFO_PASSWORD))
            .andExpect(model().attribute(ERROR_LABEL_TWO, ERROR_ENTER_PASSWORD))
            .andExpect(view().name(RESETPASSWORD_VIEW_NAME));

        verify(spiService).resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE));
    }

    /**
     * @verifies put in model the correct error code if HttpClientErrorException with http 400 is thrown by service and password is previously used then return reset password view.
     * @see AppController#resetPassword(String, String, String, String, String, Map)
     */
    @Test
    public void resetPassword_shouldPutInModelTheCorrectErrorCodeIfHttpClientErrorExceptionWithHttp400IsThrownByServiceAndPasswordIsPreviouslyUsedThenReturnResetPasswordView() throws Exception {
        given(validationService.validatePassword(eq(PASSWORD_ONE), eq(PASSWORD_TWO), any(Map.class))).willReturn(true);
        given(spiService.resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE))).willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.name(), ERR_LOCKED_FAILED_RESPONSE.getBytes(), null));
        given(validationService.isErrorInResponse(eq(ERR_LOCKED_FAILED_RESPONSE), eq(ErrorResponse.CodeEnum.ACCOUNT_LOCKED))).willReturn(true);


        mockMvc.perform(post(DO_RESET_PASSWORD_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(PASSWORD_ONE, PASSWORD_ONE)
            .param(PASSWORD_TWO, PASSWORD_TWO)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, ERROR_CAPITAL))
            .andExpect(model().attribute(ERROR_MESSAGE, ERROR_PREVIOUSLY_USED_PASSWORD))
            .andExpect(model().attribute(ERROR_LABEL_ONE, ERROR_PASSWORD_DETAILS))
            .andExpect(model().attribute(ERROR_LABEL_TWO, ERROR_ENTER_PASSWORD))
            .andExpect(view().name(RESETPASSWORD_VIEW_NAME));

        verify(spiService).resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE));
    }

    /**
     * @verifies redirect to expired token if HttpClientErrorException with http 404 is thrown by service.
     * @see AppController#resetPassword(String, String, String, String, String, Map)
     */
    @Test
    public void resetPassword_shouldRedirectToExpiredTokenIfHttpClientErrorExceptionWithHttp404IsThrownByService() throws Exception {
        given(validationService.validatePassword(eq(PASSWORD_ONE), eq(PASSWORD_TWO), any(Map.class))).willReturn(true);
        given(spiService.resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE))).willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));


        mockMvc.perform(post(DO_RESET_PASSWORD_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(PASSWORD_ONE, PASSWORD_ONE)
            .param(PASSWORD_TWO, PASSWORD_TWO)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(EXPIREDTOKEN_REDIRECTED_VIEW_NAME));

        verify(spiService).resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE));
    }

    /**
     * @verifies return reset password view if request validation fails.
     * @see AppController#resetPassword(String, String, String, String, String, Map)
     */
    @Test
    public void resetPassword_shouldReturnResetPasswordViewIfRequestValidationFails() throws Exception {
        given(validationService.validatePassword(eq(PASSWORD_ONE), eq(PASSWORD_TWO), any(Map.class))).willReturn(false);

        mockMvc.perform(post(DO_RESET_PASSWORD_ENDPOINT).with(csrf())
            .param(ACTION_PARAMETER, UNUSED)
            .param(PASSWORD_ONE, PASSWORD_ONE)
            .param(PASSWORD_TWO, PASSWORD_TWO)
            .param(TOKEN_PARAMETER, RESET_PASSWORD_TOKEN)
            .param(CODE_PARAMETER, RESET_PASSWORD_CODE))
            .andExpect(status().isOk())
            .andExpect(view().name(RESETPASSWORD_VIEW_NAME));

        verify(spiService, never()).resetPassword(eq(PASSWORD_ONE), eq(RESET_PASSWORD_TOKEN), eq(RESET_PASSWORD_CODE));
    }


    /**
     * @verifies call forget password with the right parameters
     * @see AppController#forgotPassword(uk.gov.hmcts.reform.idam.web.model.ForgotPasswordRequest, org.springframework.validation.BindingResult, Map)
     */
    @Test
    public void forgotPassword_shouldCallForgetPasswordWithTheRightParameters() throws Exception {
        mockMvc.perform(post(FORGOT_PASSWORD_WEB_ENDPOINT).with(csrf())
            .param(USER_EMAIL_PARAMETER, USER_EMAIL)
            .param(REDIRECTURI, REDIRECT_URI)
            .param(CLIENTID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk());

        verify(spiService).forgetPassword(USER_EMAIL, REDIRECT_URI, CLIENT_ID);
        verify(spiService).getServiceByClientId(eq(CLIENT_ID));
    }

    /**
     * @verifies not call forget password if there are validation errors
     * @see AppController#forgotPassword(uk.gov.hmcts.reform.idam.web.model.ForgotPasswordRequest, org.springframework.validation.BindingResult, Map)
     */
    @Test
    public void forgotPassword_shouldNotCallForgetPasswordIfThereAreValidationErrors()
        throws Exception {
        mockMvc.perform(post(FORGOT_PASSWORD_WEB_ENDPOINT).with(csrf())
            .param(USER_EMAIL_PARAMETER, USER_EMAIL_INVALID))
            .andExpect(status().isOk())
            .andExpect(view().name(FORGOT_PASSWORD_VIEW));

        verifyZeroInteractions(spiService);
    }

    /**
     * @verifies return forgot password success view when there are no errors
     * @see AppController#forgotPassword(uk.gov.hmcts.reform.idam.web.model.ForgotPasswordRequest, org.springframework.validation.BindingResult, Map)
     */
    @Test
    public void forgotPassword_shouldReturnForgotPasswordSuccessViewWhenThereAreNoErrors()
        throws Exception {
        Service service = new Service();
        service.selfRegistrationAllowed(true);

        given(spiService.getServiceByClientId(CLIENT_ID)).willReturn(Optional.of(service));

        mockMvc.perform(post(FORGOT_PASSWORD_WEB_ENDPOINT).with(csrf())
            .param(USER_EMAIL_PARAMETER, USER_EMAIL)
            .param(REDIRECTURI, REDIRECT_URI)
            .param(CLIENTID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(view().name(FORGOT_PASSWORD_SUCCESS_VIEW))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(model().attribute(CLIENTID_PARAMETER, CLIENT_ID))
            .andExpect(model().attribute(SELF_REGISTRATION_ENABLED, true));
    }

    /**
     * @verifies return forgot password success view when there are no errors and service does not have self registration enabled
     * @see AppController#forgotPassword(uk.gov.hmcts.reform.idam.web.model.ForgotPasswordRequest, org.springframework.validation.BindingResult, Map)
     */
    @Test
    public void forgotPassword_shouldReturnForgotPasswordSuccessViewWhenThereAreNoErrorsAndServiceDoesNotHaveSelfRegistrationEnabled() throws Exception {
        mockMvc.perform(post(FORGOT_PASSWORD_WEB_ENDPOINT).with(csrf())
            .param(USER_EMAIL_PARAMETER, USER_EMAIL)
            .param(REDIRECTURI, REDIRECT_URI))
            .andExpect(status().isOk())
            .andExpect(view().name(FORGOT_PASSWORD_SUCCESS_VIEW))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(model().attribute(SELF_REGISTRATION_ENABLED, false));
    }

    /**
     * @verifies return forgot password view with correct model data when there are validation errors
     * @see AppController#forgotPassword(uk.gov.hmcts.reform.idam.web.model.ForgotPasswordRequest, org.springframework.validation.BindingResult, Map)
     */
    @Test
    public void forgotPassword_shouldReturnForgotPasswordViewWithCorrectModelDataWhenThereAreValidationErrors()
        throws Exception {
        BindingResult bindingResultForInvalidEmail = (BindingResult) mockMvc.perform(post(FORGOT_PASSWORD_WEB_ENDPOINT).with(csrf())
            .param(USER_EMAIL_PARAMETER, USER_EMAIL_INVALID))
            .andExpect(status().isOk())
            .andExpect(view().name(FORGOT_PASSWORD_VIEW))
            .andExpect(model().attribute(USER_EMAIL_PARAMETER, USER_EMAIL_INVALID))
            .andReturn()
            .getModelAndView()
            .getModelMap()
            .get("org.springframework.validation.BindingResult." + FORGOT_PASSWORD_COMMAND_NAME);

        List<String> errorCodesForInvalidEmail = bindingResultForInvalidEmail.getFieldErrors(USER_EMAIL_PARAMETER).stream()
            .flatMap(fieldError -> Arrays.stream(fieldError.getCodes()))
            .collect(Collectors.toList());

        assertThat(errorCodesForInvalidEmail, hasItem("Email.forgotPasswordCommand.email"));

        BindingResult bindingResultForBlankEmail = (BindingResult) mockMvc.perform(post(FORGOT_PASSWORD_WEB_ENDPOINT).with(csrf())
            .param(USER_EMAIL_PARAMETER, MISSING))
            .andExpect(status().isOk())
            .andExpect(view().name(FORGOT_PASSWORD_VIEW))
            .andReturn()
            .getModelAndView()
            .getModelMap()
            .get("org.springframework.validation.BindingResult." + FORGOT_PASSWORD_COMMAND_NAME);

        List<String> errorCodesForBlankEmail = bindingResultForBlankEmail.getFieldErrors(USER_EMAIL_PARAMETER).stream()
            .flatMap(fieldError -> Arrays.stream(fieldError.getCodes()))
            .collect(Collectors.toList());

        assertThat(errorCodesForBlankEmail, hasItems("NotEmpty.forgotPasswordCommand.email", "Email.forgotPasswordCommand.email"));
    }

    /**
     * @verifies return error view when there is an unexpected error
     * @see AppController#forgotPassword(uk.gov.hmcts.reform.idam.web.model.ForgotPasswordRequest, org.springframework.validation.BindingResult, Map)
     */
    @Test
    public void forgotPassword_shouldReturnErrorViewWhenThereIsAnUnexpectedError()
        throws Exception {
        given(spiService.forgetPassword(any(), any(), any()))
            .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(post(FORGOT_PASSWORD_WEB_ENDPOINT).with(csrf())
            .param(USER_EMAIL_PARAMETER, USER_EMAIL))
            .andExpect(status().isOk())
            .andExpect(view().name(ERROR_VIEW_NAME));
    }

    /**
     * @verifies put in model correct data and return forgot password view
     * @see AppController#resetForgotPassword(uk.gov.hmcts.reform.idam.web.model.ForgotPasswordRequest)
     */
    @Test
    public void resetForgotPassword_shouldPutInModelCorrectDataAndReturnForgotPasswordView() throws Exception {
        mockMvc.perform(get(RESET_FORGOT_PASSWORD_ENDPOINT)
            .param(REDIRECTURI, REDIRECTURI)
            .param(USER_EMAIL_PARAMETER, USER_EMAIL))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(FORGOT_PASSWORD_VIEW));
    }


    /**
     * @verifies redirect to logout view
     * @see AppController#logout(Map)
     */
    @Test
    public void logout_shouldRedirectToLogouView() throws Exception {
        mockMvc.perform(get(LOGOUT_ENDPOINT))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(LOGIN_LOGOUT_VIEW));
    }

    /**
     * @verifies redirect to passwordReset view
     * @see AppController#logout(Map)
     */
    @Test
    public void getPasswordReset_shouldRedirectToPasswordResetView() throws Exception {
        given(spiService.validateResetPasswordToken(RESET_PASSWORD_TOKEN, RESET_PASSWORD_CODE)).willReturn(ResponseEntity.ok("{irrelevant}"));

        mockMvc.perform(get(PASSWORD_RESET_ENDPOINT + "?token=" + RESET_PASSWORD_TOKEN + "&code=" + RESET_PASSWORD_CODE))
            .andExpect(status().is2xxSuccessful())
            .andExpect(view().name(RESETPASSWORD_VIEW_NAME));
    }

    /**
     * @verifies put in model correct data then call authorize service and redirect using redirect url returned by service
     * @see AppController#login(AuthorizeRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void login_shouldPutInModelCorrectDataThenCallAuthorizeServiceAndRedirectUsingRedirectUrlReturnedByService() throws Exception {
        List<String> cookieList = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS))).willReturn(authResult);
        given(spiService.authorize(any(), eq(cookieList))).willReturn(REDIRECT_URI);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_URI));

        verify(spiService).authorize(any(), eq(cookieList));
    }

    /**
     * @verifies put in model correct data if username or  password are empty.
     * @see AppController#login(AuthorizeRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void login_shouldPutInModelCorrectDataIfUsernameOrPasswordAreEmpty() throws Exception {
        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .param(USERNAME_PARAMETER, BLANK)
            .param(PASSWORD_PARAMETER, BLANK)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute("isUsernameEmpty", true))
            .andExpect(model().attribute("isPasswordEmpty", true))
            .andExpect(model().attribute("hasErrors", true))
            .andExpect(view().name(LOGIN_VIEW));
    }

    /**
     * @verifies put in model the correct data and return login view if authorize service doesn't return a response url
     * @see AppController#login(AuthorizeRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void login_shouldPutInModelTheCorrectDataAndReturnLoginViewIfAuthorizeServiceDoesntReturnAResponseUrl() throws Exception {
        List<String> cookieList = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS))).willReturn(authResult);
        given(spiService.authorize(any(), eq(cookieList))).willReturn(MISSING);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(HAS_LOGIN_FAILED, true))
            .andExpect(view().name(LOGIN_VIEW));
    }

    /**
     * @verifies put in model the correct error detail in case authorize service throws a HttpClientErrorException and status code is 403 then return login view
     * @see AppController#login(AuthorizeRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void login_shouldPutInModelTheCorrectErrorDetailInCaseAuthorizeServiceThrowsAHttpClientErrorExceptionAndStatusCodeIs403ThenReturnLoginView() throws Exception {
        List<String> cookieList = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        ApiAuthResult authResultNoCookies = ApiAuthResult.builder()
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        ApiAuthResult authResultLocked = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .errorCode(ErrorResponse.CodeEnum.ACCOUNT_LOCKED)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        ApiAuthResult authResultSso = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .errorCode(ErrorResponse.CodeEnum.ACCOUNT_LINKED_TO_EXTERNAL_PROVIDER)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        ApiAuthResult authResultUnchecked = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .errorCode(ErrorResponse.CodeEnum.PASSWORD_CONTAINS_PERSONAL_INFO)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        ApiAuthResult authResultSuspended = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .errorCode(ErrorResponse.CodeEnum.ACCOUNT_SUSPENDED)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        ApiAuthResult authResultNullError = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.UNAUTHORIZED)
            .errorCode(null)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResult);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(model().attribute(HAS_LOGIN_FAILED, true))
            .andExpect(status().isOk())
            .andExpect(view().name(LOGIN_VIEW));

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResultNoCookies);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(model().attribute(HAS_LOGIN_FAILED, true))
            .andExpect(status().isOk())
            .andExpect(view().name(LOGIN_VIEW));

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResultUnchecked);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(model().attribute(HAS_LOGIN_FAILED, true))
            .andExpect(status().isOk())
            .andExpect(view().name(LOGIN_VIEW));

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResultSso);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().isOk())
            .andExpect(view().name(LOGIN_VIEW));

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResultLocked);


        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(model().attribute(IS_ACCOUNT_LOCKED, true))
            .andExpect(status().isOk())

            .andExpect(view().name(LOGIN_VIEW));

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResultSuspended);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(model().attribute(IS_ACCOUNT_SUSPENDED, true))
            .andExpect(status().isOk())

            .andExpect(view().name(LOGIN_VIEW));

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResultSuspended);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(model().attribute(IS_ACCOUNT_SUSPENDED, true))
            .andExpect(status().isOk())

            .andExpect(view().name(LOGIN_VIEW));

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResultNullError);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(model().attribute(HAS_LOGIN_FAILED, true))
            .andExpect(status().isOk())

            .andExpect(view().name(LOGIN_VIEW));
    }


    @Test
    public void login_onAuthenticateThrowsNotFoundWithStaleUserRegistrationCode_returnStaleUserResetPasswordView() throws Exception {
        List<String> cookieList = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .errorCode(ErrorResponse.CodeEnum.STALE_USER_REGISTRATION_SENT)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS))).willReturn(authResult);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/reset/inactive-user?response_type=" + URLEncoder.encode(RESPONSE_TYPE, CharEncoding.UTF_8) + "&state=" + URLEncoder.encode(STATE, CharEncoding.UTF_8) + "&client_id=" + CLIENT_ID + "&redirect_uri=" + REDIRECT_URI + "&scope=" + CUSTOM_SCOPE));
    }


    @Test
    public void login_onAuthenticateThrowsNotFoundWithStaleUserRegistrationCode_withNonStaleUserResponseCode_returnLoginView() throws Exception {
        List<String> cookieList = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS))).willReturn(authResult);
        given(spiService.authorize(any(), eq(cookieList))).willThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.name(), HAS_LOGIN_FAILED_RESPONSE.getBytes(), null));
        given(spiService.authorize(any(), eq(cookieList))).willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "{\"code\":\"someErrorCode\"}".getBytes(), null));

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().isOk())
            .andExpect(view().name(LOGIN_VIEW));
    }


    @Test
    public void login_onAuthenticateThrowsNotFoundWithStaleUserRegistrationCode_withBadResponseDate_returnLoginView() throws Exception {
        List<String> cookieList = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS))).willReturn(authResult);
        given(spiService.authorize(any(), eq(cookieList))).willThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.name(), HAS_LOGIN_FAILED_RESPONSE.getBytes(), null));
        given(spiService.authorize(any(), eq(cookieList))).willThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "BAD_DATE".getBytes(), null));

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().isOk())
            .andExpect(view().name(LOGIN_VIEW));
    }

    /**
     * @verifies put in model the correct error variable in case authorize service throws a HttpClientErrorException and status code is not 403 then return login view
     * @see AppController#login(AuthorizeRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void login_shouldPutInModelTheCorrectErrorVariableInCaseAuthorizeServiceThrowsAHttpClientErrorExceptionAndStatusCodeIsNot403ThenReturnLoginView() throws Exception {
        List<String> cookieList = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(cookieList)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.ALLOW)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS))).willReturn(authResult);
        given(spiService.authorize(any(), eq(cookieList))).willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().isOk())
            .andExpect(model().attribute(HAS_LOGIN_FAILED, true))
            .andExpect(view().name(LOGIN_VIEW));
    }


    /**
     * @verifies put in model correct error data and return loginWithPin view if pin is missing.
     * @see AppController#loginWithPin(String, String, String, String, Map)
     */
    @Test
    public void loginWithPin_shouldPutInModelCorrectErrorDataAndReturnLoginWithPinViewIfPinIsMissing() throws Exception {

        mockMvc.perform(post(LOGIN_WITH_PIN_ENDPOINT).with(csrf())
            .param(PIN_PARAMETER, "")
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(view().name(LOGIN_WITH_PIN_VIEW));
    }

    /**
     * @verifies redirect to the url returned by service
     * @see AppController#loginWithPin(String, String, String, String, Map)
     */
    @Test
    public void loginWithPin_shouldRedirectToTheUrlReturnedByService() throws Exception {

        given(spiService.loginWithPin(eq(LOGIN_PIN_CODE), eq(REDIRECT_URI), eq(STATE), eq(CLIENT_ID))).willReturn(REDIRECTURI);

        mockMvc.perform(post(LOGIN_WITH_PIN_ENDPOINT).with(csrf())
            .param(PIN_PARAMETER, LOGIN_PIN_CODE)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECTURI));
    }

    /**
     * @verifies put in model the redirectUri parameter and error data and return loginWithPin view if service throws a HttpClientErrorException or BadCredentialException.
     * @see AppController#loginWithPin(String, String, String, String, Map)
     */
    @Test
    public void loginWithPin_shouldPutInModelTheRedirectUriParameterAndErrorDataAndReturnLoginWithPinViewIfServiceThrowsAHttpClientErrorExceptionOrBadCredentialException() throws Exception {
        given(spiService.loginWithPin(eq(LOGIN_PIN_CODE), eq(REDIRECT_URI), eq(STATE), eq(CLIENT_ID))).willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        mockMvc.perform(post(LOGIN_WITH_PIN_ENDPOINT).with(csrf())
            .param(PIN_PARAMETER, LOGIN_PIN_CODE)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, VALID_SECURITY_CODE_ERROR))
            .andExpect(model().attribute(ERROR_MESSAGE, SECURITY_CODE_INCORRECT_ERROR))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(LOGIN_WITH_PIN_VIEW));

        given(spiService.loginWithPin(eq(LOGIN_PIN_CODE), eq(REDIRECT_URI), eq(STATE), eq(CLIENT_ID))).willThrow(new BadCredentialsException(BLANK));

        mockMvc.perform(post(LOGIN_WITH_PIN_ENDPOINT).with(csrf())
            .param(PIN_PARAMETER, LOGIN_PIN_CODE)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, VALID_SECURITY_CODE_ERROR))
            .andExpect(model().attribute(ERROR_MESSAGE, SECURITY_CODE_INCORRECT_ERROR))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(LOGIN_WITH_PIN_VIEW));

    }

    /**
     * @verifies put in model the correct error detail and return loginWithPin view if a generic exception occurs
     * @see AppController#loginWithPin(String, String, String, String, Map)
     */
    @Test
    public void loginWithPin_shouldPutInModelTheCorrectErrorDetailAndReturnLoginWithPinViewIfAGenericExceptionOccurs() throws Exception {
        given(spiService.loginWithPin(eq(LOGIN_PIN_CODE), eq(REDIRECT_URI), eq(STATE), eq(CLIENT_ID))).willThrow(RuntimeException.class);

        mockMvc.perform(post(LOGIN_WITH_PIN_ENDPOINT).with(csrf())
            .param(PIN_PARAMETER, LOGIN_PIN_CODE)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR, ERROR))
            .andExpect(model().attribute(ERROR_TITLE, "public.login.with.pin.there.was.error"))
            .andExpect(model().attribute(ERROR_MESSAGE, "public.login.with.pin.try.action.again"))
            .andExpect(model().attribute(REDIRECTURI, REDIRECT_URI))
            .andExpect(view().name(LOGIN_WITH_PIN_VIEW));
    }

    /**
     * @verifies return forbidden if csrf token is invalid
     * @see AppController#login(AuthorizeRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void login_shouldReturnForbiddenIfCsrfTokenIsInvalid() throws Exception {
        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf().useInvalidToken())
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(CLIENT_ID_PARAMETER, MISSING))
            .andExpect(status().isForbidden());
    }

    @Test
    public void login_shouldReturnForbiddenIfCsrfTokenIsInvalid2() throws Exception {
        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf().useInvalidToken())
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(CLIENT_ID_PARAMETER, MISSING))
            .andExpect(status().isForbidden());
    }

    /**
     * @verifies return tacticalActivateExpired
     * @see AppController#tacticalActivate()
     */
    @Test
    public void tacticalActivate_shouldReturnTacticalActivateExpired() throws Exception {
        mockMvc.perform(get(TACTICAL_ACTIVATE_ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(view().name(TACTICAL_ACTIVATE_VIEW));
    }

    /**
     * @verifies return tacticalReset
     * @see AppController#tacticalResetPwd() ()
     */
    @Test
    public void tacticalResetPwd_shouldReturnTacticalReset() throws Exception {
        mockMvc.perform(get(TACTICAL_RESET_ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(view().name(TACTICAL_RESET_PWD_VIEW));
    }

    /**
     * @verifies return view
     * @see AppController#cookiesView()
     */
    @Test
    public void cookiesView_shouldReturnView() throws Exception {
        mockMvc.perform(get("/cookies"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(COOKIES_VIEW));
    }

    /**
     * @verifies return view
     * @see AppController#privacyPolicyView()
     */
    @Test
    public void privacyPolicyView_shouldReturnView() throws Exception {
        mockMvc.perform(get("/privacy-policy"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(PRIVACY_POLICY_VIEW));
    }

    /**
     * @verifies return view
     * @see AppController#termsAndConditionsView()
     */
    @Test
    public void termsAndConditionsView_shouldReturnView() throws Exception {
        mockMvc.perform(get("/terms-and-conditions"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(TERMS_AND_CONDITIONS_VIEW));
    }

    /**
     * @verifies return view
     * @see AppController#contactUsView()
     */
    @Test
    public void contactUsView_shouldReturnView() throws Exception {
        configurationProperties.getFeatures().setExternalContactPage(false);
        mockMvc.perform(get("/contact-us"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(CONTACT_US_VIEW));
    }

    /**
     * @verifies return view
     * @see AppController#contactUsView()
     */
    @Test
    public void contactUsView_shouldReturnExternalContactPage() throws Exception {
        configurationProperties.getFeatures().setExternalContactPage(true);
        configurationProperties.setExternalContactPageUrl("path-to-gov-uk-page");
        mockMvc.perform(get("/contact-us"))
            .andDo(print())
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:path-to-gov-uk-page"));
    }

    /**
     * @verifies return an error page
     * @see AppController#authorizeError(Map)
     */
    @Test
    public void authorizeError_shouldReturnAnErrorPage() throws Exception {
        mockMvc.perform(get("/auth-error"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(view().name(ERROR_VIEW_NAME));
    }


    /**
     * @verifies put in model the correct error variable in case policy returns BLOCK
     * @see AppController#login(AuthorizeRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void login_shouldPutInModelTheCorrectErrorVariableInCasePolicyCheckReturnsBLOCK() throws Exception {
        List<String> cookieList = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(cookieList)
            .errorCode(ErrorResponse.CodeEnum.POLICIES_FAIL)
            .policiesAction(EvaluatePoliciesAction.BLOCK)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResult);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().isOk())
            .andExpect(model().attribute(MvcKeys.HAS_POLICY_CHECK_FAILED, true))
            .andExpect(view().name(LOGIN_VIEW));

        verify(spiService, never()).authorize(any(), eq(cookieList));
    }

    /**
     * @verifies submit otp authentication using authId cookie and otp code then call authorise and redirect the user
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldSubmitOtpAuthenticationUsingAuthIdCookieAndOtpCodeThenCallAuthoriseAndRedirectTheUser() throws Exception {
        given(spiService.submitOtpeAuthentication(any(), any(), any()))
            .willReturn(singletonList("Idam.Session=idamSessionCookie"));

        given(spiService.authorize(any(), any()))
            .willReturn(REDIRECT_URI);

        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "12345678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_URI));

        verify(spiService).submitOtpeAuthentication(eq("authId"),
            eq(USER_IP_ADDRESS),
            eq("12345678"));

        ArgumentCaptor<Map> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(spiService).authorize(paramsCaptor.capture(), eq(singletonList("Idam.Session=idamSessionCookie")));

        Map<String, String> actualParams = paramsCaptor.getValue();
        assertThat(actualParams, hasEntry(USERNAME_PARAMETER, USER_EMAIL));
        assertThat(actualParams, hasEntry(REDIRECT_URI, REDIRECT_URI));
        assertThat(actualParams, hasEntry(STATE_PARAMETER, STATE));
        assertThat(actualParams, hasEntry(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE));
        assertThat(actualParams, hasEntry(CLIENT_ID_PARAMETER, CLIENT_ID));
        assertThat(actualParams, hasEntry(SCOPE_PARAMETER, CUSTOM_SCOPE));
        assertThat(actualParams, hasEntry(CODE_PARAMETER, "12345678"));
    }

    /**
     * @verifies return verification view for INCORRECT_OTP 401 response
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldReturnVerificationViewForINCORRECT_OTP401Response() throws Exception {
        given(spiService.submitOtpeAuthentication(any(), any(), any()))
            .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED,
                "statusText",
                "{\"code\":\"INCORRECT_OTP\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "12345678"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(view().name(VERIFICATION_VIEW))
            .andExpect(model().attribute(MvcKeys.HAS_OTP_CHECK_FAILED, true));

        verify(spiService).submitOtpeAuthentication(eq("authId"),
            eq(USER_IP_ADDRESS),
            eq("12345678"));
    }

    /**
     * @verifies return login view for TOO_MANY_ATTEMPTS_OTP 401 response
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldReturnLoginViewForTOO_MANY_ATTEMPTS_OTP401Response() throws Exception {
        given(spiService.submitOtpeAuthentication(any(), any(), any()))
            .willReturn(singletonList("Idam.Session=idamSessionCookie"));

        given(spiService.submitOtpeAuthentication(any(), any(), any()))
            .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED,
                "statusText",
                "{\"code\":\"TOO_MANY_ATTEMPTS_OTP\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "12345678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/expiredcode*"));

        verify(spiService).submitOtpeAuthentication(eq("authId"),
            eq(USER_IP_ADDRESS),
            eq("12345678"));

        verify(spiService, never()).authorize(any(), any());
    }

    /**
     * @verifies return verification view for expired OTP session 401 response
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldReturnVerificationViewForExpiredOTPSession401Response() throws Exception {
        given(spiService.submitOtpeAuthentication(any(), any(), any()))
            .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "12345678"))
            .andExpect(view().name("redirect:/" + EXPIRED_CODE_VIEW));

        verify(spiService).submitOtpeAuthentication(eq("authId"),
            eq(USER_IP_ADDRESS),
            eq("12345678"));
    }

    /**
     * @verifies return login view for 403 response
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldReturnLoginViewFor403Response() throws Exception {
        given(spiService.submitOtpeAuthentication(any(), any(), any()))
            .willThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "12345678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/login*"));

        verify(spiService).submitOtpeAuthentication(eq("authId"),
            eq(USER_IP_ADDRESS),
            eq("12345678"));
    }

    /**
     * @verifies return login view when authorize fails
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldReturnLoginViewWhenAuthorizeFails() throws Exception {
        given(spiService.submitOtpeAuthentication(any(), any(), any()))
            .willReturn(singletonList("Idam.Session=idamSessionCookie"));

        given(spiService.authorize(any(), any()))
            .willReturn(MISSING);

        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "12345678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/login*"));

        verify(spiService).submitOtpeAuthentication(eq("authId"),
            eq(USER_IP_ADDRESS),
            eq("12345678"));

        ArgumentCaptor<Map> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(spiService).authorize(paramsCaptor.capture(), eq(singletonList("Idam.Session=idamSessionCookie")));

        Map<String, String> actualParams = paramsCaptor.getValue();
        assertThat(actualParams, hasEntry(USERNAME_PARAMETER, USER_EMAIL));
        assertThat(actualParams, hasEntry(REDIRECT_URI, REDIRECT_URI));
        assertThat(actualParams, hasEntry(STATE_PARAMETER, STATE));
        assertThat(actualParams, hasEntry(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE));
        assertThat(actualParams, hasEntry(CLIENT_ID_PARAMETER, CLIENT_ID));
        assertThat(actualParams, hasEntry(SCOPE_PARAMETER, CUSTOM_SCOPE));
        assertThat(actualParams, hasEntry(CODE_PARAMETER, "12345678"));
    }

    /**
     * @verifies return hasOtpCheckCodeFailed on redirects and reject "Verification code failed"
     * @see AppController#loginView(AuthorizeRequest, BindingResult, Model)
     */
    @Test
    public void loginView_shouldReturnHasOtpCheckCodeFailedOnRedirectsAndRejectVerificationCodeFailed() throws Exception {
        Service service = new Service();
        service.selfRegistrationAllowed(true);

        given(spiService.getServiceByClientId(CLIENT_ID)).willReturn(Optional.of(service));

        mockMvc.perform(get(LOGIN_ENDPOINT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(HAS_OTP_CHECK_FAILED, "true"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(model().attribute(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE))
            .andExpect(model().attribute(STATE_PARAMETER, STATE))
            .andExpect(model().attribute(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(model().attribute(REDIRECT_URI, REDIRECT_URI))
            .andExpect(model().hasErrors())
            .andExpect(model().attribute(HAS_OTP_CHECK_FAILED, true))
            .andExpect(view().name(LOGIN_VIEW));
    }

    /**
     * @verifies return error page view if OAuth2 details are missing
     * @see AppController#verificationView(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model)
     */
    @Test
    public void verificationView_shouldReturnErrorPageViewIfOAuth2DetailsAreMissing() throws Exception {
        mockMvc.perform(get(VERIFICATION_ENDPOINT)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(CLIENT_ID_PARAMETER, MISSING))
            .andExpect(status().isOk())
            .andExpect(model().attribute(ERROR_MSG, "error.page.access.denied"))
            .andExpect(view().name(ERROR_VIEW_NAME));
    }

    /**
     * @verifies populate authorizeCommand
     * @see AppController#verificationView(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model)
     */
    @Test
    public void verificationView_shouldPopulateAuthorizeCommand() throws Exception {
        mockMvc.perform(get(VERIFICATION_ENDPOINT)
            .param(USERNAME, USERNAME_PARAMETER)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(SELF_REGISTRATION_ENABLED, "true")
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(model().attribute(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE))
            .andExpect(model().attribute(STATE_PARAMETER, STATE))
            .andExpect(model().attribute(CLIENT_ID_PARAMETER, CLIENT_ID))
            .andExpect(model().attribute(REDIRECT_URI, REDIRECT_URI))
            .andExpect(model().attribute(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(view().name(VERIFICATION_VIEW));
    }

    /**
     * @verifies validate code field is not empty
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldValidateCodeFieldIsNotEmpty() throws Exception {
        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, ""))
            .andExpect(status().is2xxSuccessful())
            .andExpect(view().name(VERIFICATION_VIEW))
            .andExpect(model().attribute("isCodeEmpty", true));

        verify(spiService, never()).submitOtpeAuthentication(any(),
            any(),
            any());
    }

    /**
     * @verifies validate code field is digits
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldValidateCodeFieldIsDigits() throws Exception {
        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "a"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(view().name(VERIFICATION_VIEW))
            .andExpect(model().attribute("isCodePatternInvalid", true));

        verify(spiService, never()).submitOtpeAuthentication(any(),
            any(),
            any());
    }

    /**
     * @verifies validate code field is 8 digits
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldValidateCodeFieldIs8Digits() throws Exception {
        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "123"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(view().name(VERIFICATION_VIEW))
            .andExpect(model().attribute("isCodeLengthInvalid", true));

        verify(spiService, never()).submitOtpeAuthentication(any(),
            any(),
            any());
    }

    @Test
    public void resetPasswordStaleUser_shouldReturnTheCorrectView() throws Exception {
        mockMvc.perform(get(RESET_INACTIVE_USER_ENDPOINT).with(csrf())
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().is2xxSuccessful())
            .andExpect(view().name(STALE_USER_RESET_PASSWORD_VIEW));
    }

    /**
     * @verifies submit otp authentication filtering out Idam.Session cookie to avoid session bugs
     * @see AppController#verification(uk.gov.hmcts.reform.idam.web.model.VerificationRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void verification_shouldSubmitOtpAuthenticationFilteringOutIdamSessionCookieToAvoidSessionBugs() throws Exception {
        given(spiService.submitOtpeAuthentication(any(), any(), any()))
            .willReturn(singletonList("Idam.Session=idamSessionCookie"));

        given(spiService.authorize(any(), any()))
            .willReturn(REDIRECT_URI);

        mockMvc.perform(post(VERIFICATION_ENDPOINT).with(csrf())
            .cookie(new Cookie("Idam.AuthId", "authId"))
            .cookie(new Cookie("Idam.Session", "sessionId"))
            .cookie(new Cookie("Idam.Affinity", "affinityId"))
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(CODE_PARAMETER, "12345678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(REDIRECT_URI));

        verify(spiService).submitOtpeAuthentication(
            eq("authId"),
            eq(USER_IP_ADDRESS),
            eq("12345678"));
    }

    /**
     * @verifies not forward username password params on OTP
     * @see AppController#login(AuthorizeRequest, BindingResult, Model, HttpServletRequest, HttpServletResponse)
     */
    @Test
    public void login_shouldNotForwardUsernamePasswordParamsOnOTP() throws Exception {
        List<String> authCookies = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(authCookies)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.MFA_REQUIRED)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResult);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(MockMvcResultMatchers.header().string(HttpHeaders.SET_COOKIE, AUTHENTICATE_SESSION_COOKE))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/verification*"))
            .andExpect(model().attributeDoesNotExist(USERNAME))
            .andExpect(model().attributeDoesNotExist(PASSWORD))
            .andExpect(model().attributeDoesNotExist(MvcKeys.SELF_REGISTRATION_ENABLED))
            .andExpect(model().attribute(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE));

        verify(spiService, never()).authorize(any(), eq(authCookies));
    }

    @Test
    public void login_shouldSetAzureLoginEnabledWhenSSOEnabledAndSSOHintPresent() throws Exception {
        List<String> authCookies = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(authCookies)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.MFA_REQUIRED)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResult);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(AZURE_LOGIN_ENABLED, "true")
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(model().attribute(AZURE_LOGIN_ENABLED,true));
    }

    @Test
    public void login_shouldReturnErrors() throws Exception {
        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().isOk())
            .andExpect(model().attribute("isUsernameEmpty", true))
            .andExpect(model().attribute("isPasswordEmpty", true))
            .andExpect(model().attribute("hasErrors", true))
            .andExpect(view().name(LOGIN_VIEW));
    }

    @Test
    public void login_shouldNotSetAzureLoginEnabledWhenSSOEnabledAndSSOHintPresent() throws Exception {
        List<String> authCookies = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(authCookies)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.MFA_REQUIRED)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResult);

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(AZURE_LOGIN_ENABLED, "false")
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(model().attributeDoesNotExist(AZURE_LOGIN_ENABLED));
    }

    private static Answer<Void> redirectToExternalProvider() {
        return new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                HttpServletResponse response = invocation.getArgument(1);
                response.sendRedirect("mockRedirect");
                return null;
            }
        };
    }

    @Test
    public void login_shouldRedirectSSOUsersWhenSSOEnabledAndEmailMatches() throws Exception {
        List<String> authCookies = singletonList(AUTHENTICATE_SESSION_COOKE);
        ApiAuthResult authResult = ApiAuthResult.builder()
            .cookies(authCookies)
            .httpStatus(HttpStatus.OK)
            .policiesAction(EvaluatePoliciesAction.MFA_REQUIRED)
            .build();

        given(spiService.authenticate(eq(USER_EMAIL), eq(USER_PASSWORD), eq(REDIRECT_URI), eq(USER_IP_ADDRESS)))
            .willReturn(authResult);

        given(ssoService.isSSOEmail(USER_EMAIL)).willReturn(true);


        Mockito.doAnswer(redirectToExternalProvider())
            .when(ssoService).redirectToExternalProvider(any(), any(), any());

        mockMvc.perform(post(LOGIN_ENDPOINT).with(csrf())
            .header(X_FORWARDED_FOR, USER_IP_ADDRESS)
            .param(USERNAME_PARAMETER, USER_EMAIL)
            .param(PASSWORD_PARAMETER, USER_PASSWORD)
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(AZURE_LOGIN_ENABLED, "true")
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("mockRedirect"));
    }

    @Test
    public void expiredCode_shouldReturnExpiredCodePage() throws Exception {
        mockMvc.perform(get("/expiredcode")
            .param(REDIRECT_URI, REDIRECT_URI)
            .param(STATE_PARAMETER, STATE)
            .param(RESPONSE_TYPE_PARAMETER, RESPONSE_TYPE)
            .param(CLIENT_ID_PARAMETER, CLIENT_ID)
            .param(SCOPE_PARAMETER, CUSTOM_SCOPE)
            .param(NONCE_PARAMETER, NONCE_VALUE)
            .param(PROMPT_PARAMETER, PROMPT_VALUE))
            .andExpect(status().isOk())
            .andExpect(view().name(EXPIRED_CODE_VIEW));
    }
}

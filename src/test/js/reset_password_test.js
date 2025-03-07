const TestData = require('./config/test_data');
const randomData = require('./shared/random_data');

Feature('I am able to reset my password');

let randomUserFirstName;
let citizenEmail;
let otherCitizenEmail;
let plusCitizenEmail;
let apostropheCitizenEmail;
let staleUserEmail;
let userFirstNames = [];
let serviceNames = [];
let specialCharacterPassword;

const serviceName = randomData.getRandomServiceName();
const serviceClientSecret = randomData.getRandomClientSecret();
const userPassword = randomData.getRandomUserPassword();
const loginPage = `${TestData.WEB_PUBLIC_URL}/login?redirect_uri=${TestData.SERVICE_REDIRECT_URI}&client_id=${serviceName}&state=`;

BeforeSuite(async ({ I }) => {
    randomUserFirstName = randomData.getRandomUserName();
    citizenEmail = 'citizen.' + randomData.getRandomEmailAddress();
    otherCitizenEmail = 'other.' + randomData.getRandomEmailAddress();
    plusCitizenEmail = `plus.extra+${randomData.getRandomEmailAddress()}`;
    apostropheCitizenEmail = "apostrophe.o'test" + randomData.getRandomEmailAddress();
    staleUserEmail = 'stale.' + randomData.getRandomEmailAddress();
    specialCharacterPassword = 'New&&&$$$%%%<>234';

    await I.createServiceData(serviceName, serviceClientSecret);
    serviceNames.push(serviceName);

    await I.createUserWithRoles(citizenEmail, userPassword, randomUserFirstName + 'Citizen', ["citizen"]);
    userFirstNames.push(randomUserFirstName + 'Citizen');
    await I.createUserWithRoles(otherCitizenEmail, userPassword, randomUserFirstName + 'Other', ["citizen"]);
    userFirstNames.push(randomUserFirstName + 'Other');
    await I.createUserWithRoles(plusCitizenEmail, userPassword, randomUserFirstName + 'Plus', ["citizen"]);
    userFirstNames.push(randomUserFirstName + 'Plus');
    await I.createUserWithRoles(apostropheCitizenEmail, userPassword, randomUserFirstName + 'Apostrophe', ["citizen"]);
    userFirstNames.push(randomUserFirstName + 'Apostrophe');
    await I.createUserWithRoles(staleUserEmail, userPassword, randomUserFirstName + 'Stale', ["citizen"]);
    userFirstNames.push(randomUserFirstName + 'Stale');
    await I.retireStaleUser(staleUserEmail)
});

AfterSuite(async ({ I }) => {
    return await I.deleteAllTestData(randomData.TEST_BASE_PREFIX);
});

Scenario('@functional @resetpass As a citizen user I can reset my password', async ({ I }) => {
    const resetPassword = randomData.getRandomUserPassword();
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.click('Forgotten password?');
    I.waitForText('Reset your password');
    I.fillField('#email', citizenEmail);
    I.click('Submit');
    I.waitForText('Check your email');
    const resetPasswordUrl = await I.extractUrlFromNotifyEmail(citizenEmail);
    I.amOnPage(resetPasswordUrl);
    I.waitForText('Create a new password');
    I.seeTitleEquals('Reset Password - HMCTS Access');
    I.fillField('#password1', resetPassword);
    I.fillField('#password2', resetPassword);
    I.click('Continue');
    I.waitForText('Your password has been changed');
    I.see('You can now sign in with your new password.');
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.fillField('#username', citizenEmail);
    I.fillField('#password', resetPassword);
    I.interceptRequestsAfterSignin();
    I.click('Sign in');
    I.waitForText(TestData.SERVICE_REDIRECT_URI);
    I.see('code=');
    I.dontSee('error=');
    I.resetRequestInterception();
});
 //NOTE: Retrying this scenario is problematic.

Scenario('@functional @resetpasswithdiffcaseemail As a citizen user I can reset my password with diff case email address', async ({ I }) => {
    const resetPassword = randomData.getRandomUserPassword();
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.click('Forgotten password?');
    I.waitForText('Reset your password');
    I.fillField('#email', citizenEmail.toUpperCase());
    I.click('Submit');
    I.waitForText('Check your email');
    const resetPasswordUrl = await I.extractUrlFromNotifyEmail(citizenEmail);
    I.amOnPage(resetPasswordUrl);
    I.waitForText('Create a new password');
    I.seeTitleEquals('Reset Password - HMCTS Access');
    I.fillField('#password1', resetPassword);
    I.fillField('#password2', resetPassword);
    I.click('Continue');
    I.waitForText('Your password has been changed');
    I.see('You can now sign in with your new password.');
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.fillField('#username', citizenEmail);
    I.fillField('#password', resetPassword);
    I.interceptRequestsAfterSignin();
    I.click('Sign in');
    I.waitForText(TestData.SERVICE_REDIRECT_URI);
    I.see('code=');
    I.dontSee('error=');
    I.resetRequestInterception();
});

Scenario('@functional @resetpass As a citizen user with a plus email I can reset my password', async ({ I }) => {
    const resetPassword = randomData.getRandomUserPassword();
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.click('Forgotten password?');
    I.waitForText('Reset your password');
    I.fillField('#email', plusCitizenEmail);
    I.click('Submit');
    I.waitForText('Check your email');
    const resetPasswordUrl = await I.extractUrlFromNotifyEmail(plusCitizenEmail);
    I.amOnPage(resetPasswordUrl);
    I.waitForText('Create a new password');
    I.seeTitleEquals('Reset Password - HMCTS Access');
    I.fillField('#password1', resetPassword);
    I.fillField('#password2', resetPassword);
    I.click('Continue');
    I.waitForText('Your password has been changed');
    I.see('You can now sign in with your new password.');
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.fillField('#username', plusCitizenEmail);
    I.fillField('#password', resetPassword);
    I.interceptRequestsAfterSignin();
    I.click('Sign in');
    I.waitForText(TestData.SERVICE_REDIRECT_URI);
    I.see('code=');
    I.dontSee('error=');
    I.resetRequestInterception();
});

Scenario('@functional @resetpass As a citizen user with an apostrophe email I can reset my password', async ({ I }) => {
    const resetPassword = randomData.getRandomUserPassword();
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.click('Forgotten password?');
    I.waitForText('Reset your password');
    I.fillField('#email', apostropheCitizenEmail);
    I.click('Submit');
    I.waitForText('Check your email');
    const resetPasswordUrl = await I.extractUrlFromNotifyEmail(apostropheCitizenEmail);
    I.amOnPage(resetPasswordUrl);
    I.waitForText('Create a new password');
    I.seeTitleEquals('Reset Password - HMCTS Access');
    I.fillField('#password1', resetPassword);
    I.fillField('#password2', resetPassword);
    I.click('Continue');
    I.waitForText('Your password has been changed');
    I.see('You can now sign in with your new password.');
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.fillField('#username', apostropheCitizenEmail);
    I.fillField('#password', resetPassword);
    I.interceptRequestsAfterSignin();
    I.click('Sign in');
    I.waitForText(TestData.SERVICE_REDIRECT_URI);
    I.see('code=');
    I.dontSee('error=');
    I.resetRequestInterception();
});

Scenario('@functional @resetpass @passwordvalidation Validation displayed when I try to reset my password with a blacklisted/invalid password', async ({ I }) => {
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.click('Forgotten password?');
    I.waitForText('Reset your password');
    I.fillField('#email', otherCitizenEmail);
    I.click('Submit');
    I.waitForText('Check your email');
    const resetPasswordUrl = await I.extractUrlFromNotifyEmail(otherCitizenEmail);
    I.amOnPage(resetPasswordUrl);
    I.waitForText('Create a new password');
    I.seeTitleEquals('Reset Password - HMCTS Access');
    I.fillField('password1', 'Passw0rd');
    I.fillField('password2', 'Passw0rd');
    I.click('Continue');
    I.waitForText('There was a problem with the password you entered');
    I.see("Your password is too easy to guess");
    I.fillField('password1', `${randomUserFirstName}Other6mKjmC`);
    I.fillField('password2', `${randomUserFirstName}Other6mKjmC`);
    I.click('Continue');
    I.waitForText('There was a problem with the password you entered');
    I.see("Do not include your name or email in your password");
    I.fillField('password1', `${otherCitizenEmail}3ksTys`);
    I.fillField('password2', `${otherCitizenEmail}3ksTys`);
    I.click('Continue');
    I.waitForText('There was a problem with the password you entered');
    I.see("Do not include your name or email in your password");
    I.fillField('password1', 'passwordidamtest');
    I.fillField('password2', 'passwordidamtest');
    I.click('Continue');
    I.waitForText('There was a problem with the password you entered');
    I.see('Your password didn\'t have all the required characters');
    I.fillField('password1', 'Lincoln1');
    I.fillField('password2', 'Lincoln1');
    I.click('Continue');
    I.waitForText('There was a problem with the password you entered');
    I.see("Your password is too easy to guess");

}).retry(TestData.SCENARIO_RETRY_LIMIT);

Scenario('@functional @resetpass As a citizen user I can reset my password with repeated special characters', async ({ I }) => {
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.click('Forgotten password?');
    I.waitForText('Reset your password');
    I.fillField('#email', citizenEmail);
    I.click('Submit');
    I.waitForText('Check your email');
    const resetPasswordUrl = await I.extractUrlFromNotifyEmail(citizenEmail);
    I.amOnPage(resetPasswordUrl);
    I.waitForText('Create a new password');
    I.saveScreenshot('create-newpassword.png');
    I.seeVisualDiff('create-newpassword.png', {tolerance: 6, prepareBaseImage: false});
    I.seeTitleEquals('Reset Password - HMCTS Access');
    I.fillField('#password1', specialCharacterPassword);
    I.fillField('#password2', specialCharacterPassword);
    I.click('Continue');
    I.waitForText('Your password has been changed');
    I.see('You can now sign in with your new password.');
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.fillField('#username', citizenEmail);
    I.fillField('#password', specialCharacterPassword);
    I.interceptRequestsAfterSignin();
    I.click('Sign in');
    I.waitForText(TestData.SERVICE_REDIRECT_URI);
    I.see('code=');
    I.dontSee('error=');
    I.resetRequestInterception();
});


Scenario('@functional @staleuserresetpass As a stale user, I can reset my password', async ({ I }) => {
    const resetPassword = randomData.getRandomUserPassword();
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.click('Forgotten password?');
    I.waitForText('Reset your password');
    I.fillField('#email', staleUserEmail);
    I.click('Submit');
    I.waitForText('Check your email');
    const resetPasswordUrl = await I.extractUrlFromNotifyEmail(staleUserEmail);
    I.amOnPage(resetPasswordUrl);
    I.waitForText('Create a password');
    I.seeTitleEquals('User Activation - HMCTS Access');
    I.fillField('#password1', resetPassword);
    I.fillField('#password2', resetPassword);
    I.click('Continue');
    I.waitForText('Your password has been changed');
    I.see('You can now sign in with your new password.');
    I.amOnPage(loginPage);
    I.waitForText('Sign in or create an account');
    I.fillField('#username', staleUserEmail);
    I.fillField('#password', resetPassword);
    I.interceptRequestsAfterSignin();
    I.click('Sign in');
    I.waitForText(TestData.SERVICE_REDIRECT_URI);
    I.see('code=');
    I.dontSee('error=');
    I.resetRequestInterception();
});

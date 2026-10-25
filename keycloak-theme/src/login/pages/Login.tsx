import { useState } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Button } from "../components/Button";
import { PasswordField } from "../components/PasswordField";
import { TextField } from "../components/TextField";

export default function Login(props: PageProps<Extract<KcContext, { pageId: "login.ftl" }>, I18n>) {
    const { kcContext, i18n, Template, classes, doUseDefaultCss } = props;
    const { realm, url, usernameHidden, login, auth, registrationDisabled, messagesPerField, social } = kcContext;
    const { msg, msgStr } = i18n;
    const [isSubmitting, setIsSubmitting] = useState(false);
    const error = messagesPerField.existsError("username", "password")
        ? kcSanitize(messagesPerField.getFirstError("username", "password"))
        : undefined;
    const socialProviders = social?.providers ?? [];
    const findProvider = (...aliases: string[]) =>
        socialProviders.find(provider => aliases.some(alias => provider.alias.toLowerCase().includes(alias)));
    const socialButtons = [
        { key: "google", label: "Google", provider: findProvider("google"), icon: <GoogleIcon /> },
        { key: "facebook", label: "Facebook", provider: findProvider("facebook"), icon: <FacebookIcon /> },
        { key: "apple", label: "Apple", provider: findProvider("apple"), icon: <AppleIcon /> }
    ];

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            displayMessage={!error}
            headerNode={msg("financeWelcomeBack")}
            displayInfo={realm.password && realm.registrationAllowed && !registrationDisabled}
            infoNode={
                <span>
                    {msg("noAccount")} <a href={url.registrationUrl}>{msg("doRegister")}</a>
                </span>
            }
            socialProvidersNode={
                <div className="finance-social">
                    <span>{msg("identity-provider-login-label")}</span>
                    <div className="finance-social-buttons">
                        {socialButtons.map(({ key, label, provider, icon }) =>
                            provider ? (
                                <a key={key} href={provider.loginUrl} aria-label={`${msgStr("doLogIn")} ${label}`} title={label}>
                                    {icon}
                                </a>
                            ) : (
                                <button key={key} type="button" aria-label={label} title={`${label} (coming soon)`} disabled>
                                    {icon}
                                </button>
                            )
                        )}
                    </div>
                </div>
            }
        >
            {realm.password && (
                <form id="kc-form-login" action={url.loginAction} method="post" onSubmit={() => setIsSubmitting(true)}>
                    {!usernameHidden && (
                        <TextField
                            id="username"
                            name="username"
                            label={
                                !realm.loginWithEmailAllowed
                                    ? msg("username")
                                    : !realm.registrationEmailAsUsername
                                      ? msg("usernameOrEmail")
                                      : msg("email")
                            }
                            defaultValue={login.username ?? ""}
                            placeholder="Enter your username or email"
                            autoComplete="username"
                            autoFocus
                            error={error}
                            leadingIcon={<MailIcon />}
                        />
                    )}
                    <PasswordField
                        id="password"
                        name="password"
                        label={msg("password")}
                        autoComplete="current-password"
                        placeholder="Enter your password"
                        error={usernameHidden ? error : undefined}
                        showLabel={msgStr("showPassword")}
                        hideLabel={msgStr("hidePassword")}
                        leadingIcon={<LockIcon />}
                    />
                    <div className="finance-form-options">
                        {realm.rememberMe && !usernameHidden ? (
                            <label className="finance-check">
                                <input name="rememberMe" type="checkbox" defaultChecked={!!login.rememberMe} />
                                <span>{msg("rememberMe")}</span>
                            </label>
                        ) : (
                            <span />
                        )}
                        {realm.resetPasswordAllowed && <a href={url.loginResetCredentialsUrl}>{msg("doForgotPassword")}</a>}
                    </div>
                    <input type="hidden" name="credentialId" value={auth.selectedCredential} />
                    <Button id="kc-login" name="login" type="submit" disabled={isSubmitting}>
                        {isSubmitting ? msg("doSubmit") : msg("doLogIn")}
                    </Button>
                </form>
            )}
        </Template>
    );
}

function MailIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="3" y="5" width="18" height="14" rx="3" />
            <path d="m5 8 7 5 7-5" />
        </svg>
    );
}

function LockIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="4" y="10" width="16" height="11" rx="3" />
            <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
        </svg>
    );
}

function GoogleIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M21.6 12.2c0-.7-.1-1.4-.2-2H12v3.9h5.4a4.6 4.6 0 0 1-2 3v2.5h3.2c1.9-1.8 3-4.3 3-7.4Z" />
            <path fill="#34A853" d="M12 22c2.7 0 5-.9 6.6-2.4l-3.2-2.5c-.9.6-2 1-3.4 1a5.8 5.8 0 0 1-5.5-4H3.2v2.6A10 10 0 0 0 12 22Z" />
            <path fill="#FBBC05" d="M6.5 14.1a6 6 0 0 1 0-4.2V7.3H3.2a10 10 0 0 0 0 9.4l3.3-2.6Z" />
            <path fill="#EA4335" d="M12 5.9c1.5 0 2.8.5 3.8 1.5l2.9-2.8A9.7 9.7 0 0 0 12 2a10 10 0 0 0-8.8 5.3l3.3 2.6a5.8 5.8 0 0 1 5.5-4Z" />
        </svg>
    );
}

function FacebookIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" fill="#1877F2" />
            <path fill="#fff" d="M13.5 21v-8h2.7l.4-3h-3.1V8.1c0-.9.2-1.5 1.6-1.5h1.7V4a23 23 0 0 0-2.5-.1c-2.5 0-4.2 1.5-4.2 4.3V10H7.3v3h2.8v8h3.4Z" />
        </svg>
    );
}

function AppleIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path fill="currentColor" d="M17.1 12.5c0-2.4 2-3.6 2.1-3.7a4.6 4.6 0 0 0-3.6-2c-1.5-.2-3 .9-3.8.9-.8 0-2-.9-3.3-.8a4.9 4.9 0 0 0-4.1 2.5c-1.8 3-.5 7.5 1.2 10 .9 1.2 1.8 2.5 3.2 2.4 1.3 0 1.8-.8 3.4-.8 1.5 0 2 .8 3.4.8 1.4 0 2.3-1.2 3.1-2.5a11 11 0 0 0 1.4-2.9 4.4 4.4 0 0 1-3-3.9ZM14.6 5.3A4.4 4.4 0 0 0 15.7 2a4.6 4.6 0 0 0-3 1.6 4.1 4.1 0 0 0-1.1 3.1 3.8 3.8 0 0 0 3-1.4Z" />
        </svg>
    );
}

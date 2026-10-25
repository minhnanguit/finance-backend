import { useEffect } from "react";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import type { TemplateProps } from "keycloakify/login/TemplateProps";
import type { I18n } from "./i18n";
import type { KcContext } from "./KcContext";
import { Alert } from "./components/Alert";
import { Coins, DollarSign, TrendingUp, WalletCards } from "lucide-react";

export default function Template(props: TemplateProps<KcContext, I18n>) {
    const {
        displayInfo = false,
        displayMessage = true,
        displayRequiredFields = false,
        headerNode,
        socialProvidersNode,
        infoNode,
        documentTitle,
        kcContext,
        i18n,
        children
    } = props;
    const { msgStr, currentLanguage, enabledLanguages } = i18n;
    const { realm, message, isAppInitiatedAction } = kcContext;
    const isLoginPage = kcContext.pageId === "login.ftl";
    const isRegisterPage = kcContext.pageId === "register.ftl";
    const isSecondaryAuthPage = [
        "login-reset-password.ftl",
        "login-update-password.ftl",
        "login-verify-email.ftl",
        "info.ftl",
        "error.ftl"
    ].includes(kcContext.pageId);
    const isBrandedAuthPage = isLoginPage || isRegisterPage || isSecondaryAuthPage;
    const isLoginStylePage = isLoginPage || isSecondaryAuthPage;

    useEffect(() => {
        document.title = documentTitle ?? msgStr("loginTitle", realm.displayName || realm.name);
    }, [documentTitle, msgStr, realm.displayName, realm.name]);

    return (
        <main
            className={`finance-auth-shell${isLoginStylePage ? " finance-login-page" : ""}${isRegisterPage ? " finance-register-page" : ""}${isSecondaryAuthPage ? " finance-secondary-page" : ""}`}
        >
            <div className="finance-auth-decoration finance-auth-decoration--one" />
            <div className="finance-auth-decoration finance-auth-decoration--two" />
            {isBrandedAuthPage && (
                <div className="finance-login-hero" aria-hidden="true">
                    <div className="finance-login-brand">
                        <span className="finance-login-brand-mark"><WalletCards /></span>
                        <span>Finance</span>
                    </div>
                    <span className="finance-organic finance-organic--left" />
                    <span className="finance-organic finance-organic--right" />
                    <img
                        className={isRegisterPage ? "finance-register-illustration" : "finance-login-illustration"}
                        src={`${kcContext.url.resourcesPath}/dist/welcome-wallet.png`}
                        alt=""
                    />
                    <span className="finance-hero-icon finance-hero-icon--coins"><Coins /></span>
                    <span className="finance-hero-icon finance-hero-icon--growth"><TrendingUp /></span>
                </div>
            )}
            <section className="finance-auth-card" aria-labelledby="kc-page-title">
                <svg className="finance-watermark finance-watermark--wallet" viewBox="0 0 90 92" aria-hidden="true">
                    <path className="finance-wallet-card finance-wallet-card--back" d="M28 21 75 7l3 36-48 9Z" />
                    <path className="finance-wallet-card finance-wallet-card--front" d="M24 27 69 14l3 32-45 10Z" />
                    <path className="finance-wallet-body" d="m17 31 61 17-8 38-58-16c-5-1-8-6-7-11l5-22c1-5 4-7 7-6Z" />
                    <path className="finance-wallet-seam" d="m18 36 53 15-6 29-50-14c-3-1-5-4-4-7l4-19c0-2 1-3 3-4Z" />
                </svg>
                <div className="finance-card-topbar">
                    <a className="finance-brand" href={kcContext.url.loginUrl} aria-label="Finance">
                        <span className="finance-mini-logo" aria-hidden="true">
                            <WalletCards />
                        </span>
                        <span>Finance</span>
                    </a>
                    {enabledLanguages.length > 1 && (
                        <div className="finance-languages" aria-label={msgStr("languages")}>
                            {enabledLanguages.map(language => (
                                <a
                                    key={language.languageTag}
                                    href={language.href}
                                    lang={language.languageTag}
                                    aria-current={language.languageTag === currentLanguage.languageTag ? "page" : undefined}
                                >
                                    {language.languageTag.toUpperCase()}
                                </a>
                            ))}
                        </div>
                    )}
                </div>
                <div className="finance-hero-mark" aria-hidden="true">
                    <DollarSign />
                </div>
                <header className="finance-auth-header">
                    <h1 id="kc-page-title">{headerNode}</h1>
                    {(isLoginPage || isRegisterPage) && (
                        <p>{msgStr(isLoginPage ? "financeLoginSubtitle" : "financeRegisterSubtitle")}</p>
                    )}
                    {displayRequiredFields && <span className="finance-required-note">{msgStr("financeRequiredFields")}</span>}
                </header>
                {displayMessage && message && (message.type !== "warning" || !isAppInitiatedAction) && (
                    <Alert type={message.type}>
                        <span dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }} />
                    </Alert>
                )}
                <div className="finance-auth-content">{children}</div>
                {socialProvidersNode}
                {isRegisterPage && (
                    <>
                        <div className="finance-register-social">
                            <span>{msgStr("financeSocialSignUp")}</span>
                            <div className="finance-register-social-buttons">
                                <button type="button" aria-label="Google" title={`Google (${msgStr("financeComingSoon")})`} disabled>
                                    <RegisterGoogleIcon />
                                </button>
                                <button type="button" aria-label="Facebook" title={`Facebook (${msgStr("financeComingSoon")})`} disabled>
                                    <RegisterFacebookIcon />
                                </button>
                                <button type="button" aria-label="Apple" title={`Apple (${msgStr("financeComingSoon")})`} disabled>
                                    <RegisterAppleIcon />
                                </button>
                            </div>
                        </div>
                        <div className="finance-register-login-link">
                            <span>{msgStr("financeAlreadyRegistered")}</span>
                            <a href={kcContext.url.loginUrl}>{msgStr("doLogIn")}</a>
                        </div>
                    </>
                )}
                {displayInfo && <div className="finance-auth-footer">{infoNode}</div>}
            </section>
            <p className="finance-security-note">{msgStr("financeSecurityNote")}</p>
        </main>
    );
}

function RegisterGoogleIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path fill="#4285F4" d="M21.6 12.2c0-.7-.1-1.4-.2-2H12v3.9h5.4a4.6 4.6 0 0 1-2 3v2.5h3.2c1.9-1.8 3-4.3 3-7.4Z" />
            <path fill="#34A853" d="M12 22c2.7 0 5-.9 6.6-2.4l-3.2-2.5c-.9.6-2 1-3.4 1a5.8 5.8 0 0 1-5.5-4H3.2v2.6A10 10 0 0 0 12 22Z" />
            <path fill="#FBBC05" d="M6.5 14.1a6 6 0 0 1 0-4.2V7.3H3.2a10 10 0 0 0 0 9.4l3.3-2.6Z" />
            <path fill="#EA4335" d="M12 5.9c1.5 0 2.8.5 3.8 1.5l2.9-2.8A9.7 9.7 0 0 0 12 2a10 10 0 0 0-8.8 5.3l3.3 2.6a5.8 5.8 0 0 1 5.5-4Z" />
        </svg>
    );
}

function RegisterFacebookIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" fill="#1877F2" />
            <path fill="#fff" d="M13.5 21v-8h2.7l.4-3h-3.1V8.1c0-.9.2-1.5 1.6-1.5h1.7V4a23 23 0 0 0-2.5-.1c-2.5 0-4.2 1.5-4.2 4.3V10H7.3v3h2.8v8h3.4Z" />
        </svg>
    );
}

function RegisterAppleIcon() {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true">
            <path fill="currentColor" d="M17.1 12.5c0-2.4 2-3.6 2.1-3.7a4.6 4.6 0 0 0-3.6-2c-1.5-.2-3 .9-3.8.9-.8 0-2-.9-3.3-.8a4.9 4.9 0 0 0-4.1 2.5c-1.8 3-.5 7.5 1.2 10 .9 1.2 1.8 2.5 3.2 2.4 1.3 0 1.8-.8 3.4-.8 1.5 0 2 .8 3.4.8 1.4 0 2.3-1.2 3.1-2.5a11 11 0 0 0 1.4-2.9 4.4 4.4 0 0 1-3-3.9ZM14.6 5.3A4.4 4.4 0 0 0 15.7 2a4.6 4.6 0 0 0-3 1.6 4.1 4.1 0 0 0-1.1 3.1 3.8 3.8 0 0 0 3-1.4Z" />
        </svg>
    );
}

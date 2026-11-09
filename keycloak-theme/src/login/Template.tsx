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

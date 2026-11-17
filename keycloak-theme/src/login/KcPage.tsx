import { Suspense, lazy } from "react";
import type { ClassKey } from "keycloakify/login";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "./Template";
import Login from "./pages/Login";
import Register from "./pages/Register";
import LoginResetPassword from "./pages/LoginResetPassword";
import LoginUpdatePassword from "./pages/LoginUpdatePassword";
import LoginVerifyEmail from "./pages/LoginVerifyEmail";
import Info from "./pages/Info";
import ErrorPage from "./pages/Error";
import "./main.css";
import "./login-blue.css";
const UserProfileFormFields = lazy(
    () => import("keycloakify/login/UserProfileFormFields")
);

const doMakeUserConfirmPassword = true;

export default function KcPage(props: { kcContext: KcContext }) {
    const { kcContext } = props;

    const { i18n } = useI18n({ kcContext });

    return (
        <Suspense>
            {(() => {
                switch (kcContext.pageId) {
                    case "login.ftl":
                        return (
                            <Login
                                kcContext={kcContext}
                                i18n={i18n}
                                classes={classes}
                                Template={Template}
                                doUseDefaultCss={false}
                            />
                        );
                    case "register.ftl":
                        return (
                            <Register
                                kcContext={kcContext}
                                i18n={i18n}
                                classes={classes}
                                Template={Template}
                                doUseDefaultCss={false}
                                UserProfileFormFields={UserProfileFormFields}
                                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
                            />
                        );
                    case "login-reset-password.ftl":
                        return <LoginResetPassword kcContext={kcContext} i18n={i18n} classes={classes} Template={Template} doUseDefaultCss={false} />;
                    case "login-update-password.ftl":
                        return <LoginUpdatePassword kcContext={kcContext} i18n={i18n} classes={classes} Template={Template} doUseDefaultCss={false} />;
                    case "login-verify-email.ftl":
                        return <LoginVerifyEmail kcContext={kcContext} i18n={i18n} classes={classes} Template={Template} doUseDefaultCss={false} />;
                    case "info.ftl":
                        return <Info kcContext={kcContext} i18n={i18n} classes={classes} Template={Template} doUseDefaultCss={false} />;
                    case "error.ftl":
                        return <ErrorPage kcContext={kcContext} i18n={i18n} classes={classes} Template={Template} doUseDefaultCss={false} />;
                    default:
                        return (
                            <DefaultPage
                                kcContext={kcContext}
                                i18n={i18n}
                                classes={classes}
                                Template={Template}
                                doUseDefaultCss={false}
                                UserProfileFormFields={UserProfileFormFields}
                                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
                            />
                        );
                }
            })()}
        </Suspense>
    );

}

const classes = {
    kcFormClass: "finance-form",
    kcFormGroupClass: "finance-field",
    kcLabelClass: "finance-label",
    kcInputClass: "finance-input",
    kcInputWrapperClass: "finance-input-wrapper",
    kcInputErrorMessageClass: "finance-field-error",
    kcInputGroup: "finance-password",
    kcFormPasswordVisibilityButtonClass: "finance-password-toggle",
    kcButtonClass: "finance-button",
    kcButtonPrimaryClass: "finance-button--primary",
    kcButtonBlockClass: "finance-button--full",
    kcFormOptionsClass: "finance-form-options",
    kcFormOptionsWrapperClass: "finance-form-options",
    kcAlertClass: "finance-alert",
    kcAlertTitleClass: "finance-alert-title",
    kcSignUpClass: "finance-auth-footer"
} satisfies { [key in ClassKey]?: string };

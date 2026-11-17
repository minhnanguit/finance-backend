/* eslint-disable @typescript-eslint/no-unused-vars */
import { i18nBuilder } from "keycloakify/login";
import type { ThemeName } from "../kc.gen";

const { useI18n, ofTypeI18n } = i18nBuilder
    .withThemeName<ThemeName>()
    .withExtraLanguages({
        vi: {
            label: "Tiếng Việt",
            getMessages: () => import("./i18n.vi")
        }
    })
    .withCustomTranslations({
        en: {
            loginAccountTitle: "Log in",
            financeWelcomeBack: "Sign in to your account",
            financeWelcome: "Sign in to manage your money",
            financeLoginSubtitle: "Sign in to manage your money",
            financeRegisterSubtitle: "Create an account to manage your money",
            financeRequiredFields: "Fields marked * are required",
            financeSecurityNote: "Protected by Finance secure sign-in",
            financeSocialSignUp: "Or sign up with",
            financeAlreadyRegistered: "Already have an account?",
            financeComingSoon: "coming soon"
        },
        vi: {
            loginAccountTitle: "Đăng nhập",
            financeWelcomeBack: "Đăng nhập tài khoản",
            financeWelcome: "Đăng nhập để quản lý tài chính của bạn",
            financeLoginSubtitle: "Đăng nhập để quản lý tài chính của bạn",
            financeRegisterSubtitle: "Tạo tài khoản để quản lý tài chính của bạn",
            financeRequiredFields: "Các trường có dấu * là bắt buộc",
            financeSecurityNote: "Được bảo vệ bởi hệ thống đăng nhập an toàn của Finance",
            financeSocialSignUp: "Hoặc đăng ký bằng",
            financeAlreadyRegistered: "Bạn đã có tài khoản?",
            financeComingSoon: "sắp có"
        }
    })
    .build();

type I18n = typeof ofTypeI18n;

export { useI18n, type I18n };

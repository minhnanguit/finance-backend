import DefaultPage from "keycloakify/login/pages/Error";
import type { ComponentProps } from "react";

export default function ErrorPage(props: ComponentProps<typeof DefaultPage>) {
    return <DefaultPage {...props} />;
}

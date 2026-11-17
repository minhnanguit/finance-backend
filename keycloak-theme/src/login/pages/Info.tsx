import DefaultPage from "keycloakify/login/pages/Info";
import type { ComponentProps } from "react";

export default function Info(props: ComponentProps<typeof DefaultPage>) {
    return <DefaultPage {...props} />;
}

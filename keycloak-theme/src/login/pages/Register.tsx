import DefaultRegister from "keycloakify/login/pages/Register";
import type { ComponentProps } from "react";

/**
 * Keycloakify's register page intentionally renders the realm User Profile.
 * Styling is supplied by the shared Finance classes, so new realm attributes
 * appear automatically without changing this component.
 */
export default function Register(props: ComponentProps<typeof DefaultRegister>) {
    return <DefaultRegister {...props} />;
}

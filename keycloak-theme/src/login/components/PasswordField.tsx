import type { InputHTMLAttributes, ReactNode } from "react";
import { useIsPasswordRevealed } from "keycloakify/tools/useIsPasswordRevealed";
import { Eye, EyeOff } from "lucide-react";

type Props = InputHTMLAttributes<HTMLInputElement> & {
    label: ReactNode;
    error?: string;
    showLabel: string;
    hideLabel: string;
    leadingIcon?: ReactNode;
};

export function PasswordField({
    label,
    error,
    showLabel,
    hideLabel,
    leadingIcon,
    id = "password",
    className = "",
    ...props
}: Props) {
    const passwordInputId = id;
    const { isPasswordRevealed, toggleIsPasswordRevealed } = useIsPasswordRevealed({
        passwordInputId
    });

    return (
        <div className="finance-field">
            <label htmlFor={passwordInputId}>{label}</label>
            <div className="finance-password">
                {leadingIcon && <span className="finance-input-icon">{leadingIcon}</span>}
                <input
                    id={passwordInputId}
                    className={`finance-input${leadingIcon ? " finance-input--with-icon" : ""} ${className}`.trim()}
                    type="password"
                    aria-invalid={!!error}
                    {...props}
                />
                <button
                    type="button"
                    className="finance-password-toggle"
                    onClick={toggleIsPasswordRevealed}
                    aria-label={isPasswordRevealed ? hideLabel : showLabel}
                    aria-controls={passwordInputId}
                >
                    {isPasswordRevealed ? (
                        <EyeOff aria-hidden="true" />
                    ) : (
                        <Eye aria-hidden="true" />
                    )}
                </button>
            </div>
            {error && (
                <span
                    className="finance-field-error"
                    role="alert"
                    dangerouslySetInnerHTML={{ __html: error }}
                />
            )}
        </div>
    );
}

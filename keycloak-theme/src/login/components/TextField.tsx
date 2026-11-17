import type { InputHTMLAttributes, ReactNode } from "react";

type Props = InputHTMLAttributes<HTMLInputElement> & {
    label: ReactNode;
    error?: string;
    leadingIcon?: ReactNode;
};

export function TextField({
    label,
    error,
    leadingIcon,
    className = "",
    id,
    ...props
}: Props) {
    return (
        <div className="finance-field">
            <label htmlFor={id}>{label}</label>
            <div className="finance-input-shell">
                {leadingIcon && <span className="finance-input-icon">{leadingIcon}</span>}
                <input
                    id={id}
                    className={`finance-input${leadingIcon ? " finance-input--with-icon" : ""} ${className}`.trim()}
                    aria-invalid={!!error}
                    {...props}
                />
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

import type { ButtonHTMLAttributes, ReactNode } from "react";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
    children: ReactNode;
    fullWidth?: boolean;
};

export function Button({ children, className = "", fullWidth = true, ...props }: Props) {
    return (
        <button
            className={`finance-button${fullWidth ? " finance-button--full" : ""} ${className}`.trim()}
            {...props}
        >
            {children}
        </button>
    );
}

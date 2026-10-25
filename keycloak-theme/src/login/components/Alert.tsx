import type { ReactNode } from "react";

export function Alert({
    type,
    children
}: {
    type: "success" | "warning" | "error" | "info";
    children: ReactNode;
}) {
    return (
        <div
            className={`finance-alert finance-alert--${type}`}
            role={type === "error" ? "alert" : "status"}
        >
            <span aria-hidden="true">
                {type === "error" ? "!" : type === "success" ? "✓" : "i"}
            </span>
            <div>{children}</div>
        </div>
    );
}

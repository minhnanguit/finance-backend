import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const stories = [
    ["login", "finance-login--default"],
    ["register", "finance-register--default"],
    ["reset-password", "finance-recovery-reset-password--default"],
    ["update-password", "finance-recovery-update-password--default"],
    ["verify-email", "finance-recovery-verify-email--default"],
    ["info", "finance-status-info--default"],
    ["error", "finance-status-error--default"]
] as const;

for (const [name, storyId] of stories) {
    test(`${name} has no serious accessibility violations`, async ({ page }) => {
        await page.goto(`/iframe.html?id=${storyId}&viewMode=story`);
        await expect(page.locator("main.finance-auth-shell")).toBeVisible();
        const results = await new AxeBuilder({ page }).analyze();
        expect(results.violations.filter(({ impact }) => impact === "critical" || impact === "serious")).toEqual([]);
    });

    test(`${name} visual regression`, async ({ page }) => {
        await page.goto(`/iframe.html?id=${storyId}&viewMode=story`);
        await expect(page.locator("main.finance-auth-shell")).toBeVisible();
        await expect(page).toHaveScreenshot(`${name}.png`, { fullPage: true, animations: "disabled" });
    });
}

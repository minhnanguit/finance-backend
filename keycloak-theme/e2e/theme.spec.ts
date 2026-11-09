import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const stories = [
    ["login", "finance-login--default"],
    ["login-vi", "finance-login--vietnamese"],
    ["login-error", "finance-login--invalid-credentials"],
    ["register", "finance-register--default"],
    ["register-vi", "finance-register--vietnamese"],
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

for (const [name, storyId] of [
    ["login-small", "finance-login--vietnamese"],
    ["register-small", "finance-register--vietnamese"]
] as const) {
    test(`${name} keeps the primary action reachable`, async ({ page }) => {
        await page.setViewportSize({ width: 360, height: 640 });
        await page.goto(`/iframe.html?id=${storyId}&viewMode=story`);
        const submit = page.locator('button[type="submit"], input[type="submit"]').first();
        await submit.scrollIntoViewIfNeeded();
        await expect(submit).toBeVisible();
        await expect(page).toHaveScreenshot(`${name}.png`, { fullPage: true, animations: "disabled" });
    });
}

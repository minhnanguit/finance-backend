import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
    testDir: "./e2e",
    snapshotPathTemplate: "{testDir}/{testFilePath}-snapshots/{arg}-{projectName}.png",
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    reporter: "html",
    use: {
        baseURL: "http://127.0.0.1:6006",
        trace: "on-first-retry"
    },
    webServer: {
        command: "npm run build-storybook && vite preview --outDir storybook-static --port 6006 --host 127.0.0.1",
        url: "http://127.0.0.1:6006",
        reuseExistingServer: !process.env.CI,
        timeout: 120_000
    },
    projects: [
        { name: "mobile-light", use: { ...devices["Pixel 7"], colorScheme: "light" } },
        { name: "mobile-dark", use: { ...devices["Pixel 7"], colorScheme: "dark" } }
    ]
});

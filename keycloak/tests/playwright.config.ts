import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: ".",
  testMatch: "login-theme.spec.ts",
  workers: 1,
  retries: 0,
  timeout: 30_000,
  reporter: "list",
  use: {
    baseURL: process.env.KEYCLOAK_TEST_BASE_URL,
    headless: true,
    trace: "retain-on-failure",
  },
});

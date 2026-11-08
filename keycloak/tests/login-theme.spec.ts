import { test, expect, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";
import { mkdir } from "node:fs/promises";
const callback = process.env.KEYCLOAK_TEST_CALLBACK_BASE!;
function auth(
  base: string,
  {
    locale = "en",
    realm = "grounds-login-test",
    client = "theme-smoke",
    extra = {},
  }: {
    locale?: string;
    realm?: string;
    client?: string;
    extra?: Record<string, string>;
  } = {},
) {
  return (
    base +
    `/realms/${realm}/protocol/openid-connect/auth?` +
    new URLSearchParams({
      client_id: client,
      redirect_uri: callback + "/callback",
      response_type: "code",
      scope: "openid",
      ui_locales: locale,
      ...extra,
    })
  );
}
async function credentials(
  page: Page,
  user = "otp-user",
  password = "theme-password",
) {
  await page.locator("#username").fill(user);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
}
function otp() {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 30_000)));
  const hash = createHmac("sha1", "theme-smoke-otp-secret")
    .update(counter)
    .digest();
  return String(
    (hash.readUInt32BE(hash[19] & 15) & 0x7fffffff) % 1_000_000,
  ).padStart(6, "0");
}
async function completeOtp(page: Page) {
  await page.locator("#otp").fill(otp());
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(
    new RegExp(
      callback.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "/callback\\?",
    ),
  );
  expect(new URL(page.url()).searchParams.get("code")).toBeTruthy();
}
test.beforeEach(async ({ page, baseURL }) => {
  expect(new URL(baseURL!).hostname).toMatch(/^(127\.0\.0\.1|localhost)$/);
  page.on("pageerror", (error) => {
    throw error;
  });
});
test("Grounds login shares controls and places language and restart by task hierarchy", async ({
  page,
  baseURL,
}) => {
  await page.goto(auth(baseURL!, { locale: "de" }));
  await expect(page.locator(".grounds-page")).toBeVisible();
  await expect(page.locator("#grounds-restart-login")).toHaveCount(0);
  await expect(page.locator("#username")).toHaveClass(/field/);
  await credentials(page);
  await expect(page.locator("#otp")).toHaveAttribute(
    "autocomplete",
    "one-time-code",
  );
  const restart = page.locator("#grounds-restart-login");
  await expect(restart).toHaveText("Von vorn beginnen");
  await page.locator("#kc-login").focus();
  await page.keyboard.press("Tab");
  await expect(restart).toBeFocused();
  const submit = await page.locator("#kc-login").boundingBox(),
    secondary = await restart.boundingBox();
  expect(secondary!.y).toBeGreaterThanOrEqual(submit!.y + submit!.height);
  await restart.click();
  await expect(page.locator("#username")).toBeVisible();
  await expect(restart).toHaveCount(0);
});
test("invalid password and OTP keep accessible errors; valid OTP completes the code flow", async ({
  page,
  baseURL,
}) => {
  await page.goto(auth(baseURL!));
  await credentials(page, "otp-user", "wrong");
  await expect(page.locator("#input-error")).toBeVisible();
  await expect(page.locator("#password")).toHaveAttribute(
    "aria-describedby",
    "input-error",
  );
  await page.locator("#password").fill("theme-password");
  await page.locator("#kc-login").click();
  await page.locator("#otp").fill("invalid");
  await page.locator("#kc-login").click();
  await expect(page.locator("#input-error-otp-code")).toBeVisible();
  await expect(page.locator("#otp")).toHaveAttribute(
    "aria-describedby",
    "input-error-otp-code",
  );
  await completeOtp(page);
});
for (const [first, second] of [
  ["hub-test", "portal-test"],
  ["portal-test", "hub-test"],
])
  test(`SSO reuses ${first} session in ${second} and requires fresh OTP for LoA 2`, async ({
    page,
    baseURL,
  }) => {
    await page.goto(auth(baseURL!, { client: first }));
    await credentials(
      page,
      first === "hub-test" ? "sso-hub-user" : "sso-portal-user",
    );
    await expect.poll(() => new URL(page.url()).pathname).toBe("/callback");
    expect(new URL(page.url()).searchParams.get("code")).toBeTruthy();
    await page.goto(
      auth(baseURL!, { client: second, extra: { prompt: "none" } }),
    );
    await expect.poll(() => new URL(page.url()).pathname).toBe("/callback");
    expect(new URL(page.url()).searchParams.get("error")).toBeNull();
    expect(new URL(page.url()).searchParams.get("code")).toBeTruthy();
    await page.goto(
      auth(baseURL!, {
        client: "portal-test",
        extra: { acr_values: "control-center-mfa" },
      }),
    );
    await expect(page.locator("#otp")).toBeVisible();
    await expect(page.locator("#username")).toHaveCount(0);
    await completeOtp(page);
  });
test("logout confirmation uses fallback shell and ends the SSO session", async ({
  page,
  baseURL,
}) => {
  await page.goto(auth(baseURL!, { client: "hub-test" }));
  await credentials(page);
  await expect.poll(() => new URL(page.url()).pathname).toBe("/callback");
  await page.goto(
    baseURL +
      "/realms/grounds-login-test/protocol/openid-connect/logout?" +
      new URLSearchParams({
        client_id: "hub-test",
        post_logout_redirect_uri: callback + "/logout-complete",
      }),
  );
  await expect(page.locator("#kc-logout")).toBeVisible();
  await expect(page.locator("#grounds-restart-login")).toHaveCount(0);
  await page.locator("#kc-logout").click();
  await expect
    .poll(() => new URL(page.url()).pathname)
    .toBe("/logout-complete");
  await page.goto(
    auth(baseURL!, { client: "portal-test", extra: { prompt: "none" } }),
  );
  await expect.poll(() => new URL(page.url()).pathname).toBe("/callback");
  expect(new URL(page.url()).searchParams.get("error")).toBe("login_required");
});
test("a real expired authentication session offers recovery into a fresh login", async ({
  page,
  baseURL,
}) => {
  await page.goto(auth(baseURL!, { realm: "grounds-expiry" }));
  await credentials(page);
  await expect(page.locator("#otp")).toBeVisible();
  const oldAction = await page.locator("form").getAttribute("action");
  await new Promise((resolve) => setTimeout(resolve, 4_500));
  await page.locator("#otp").fill("000000");
  await page.locator("#kc-login").click();
  await expect(page.locator(".grounds-page")).toBeVisible();
  await expect(page.locator("body")).toContainText(
    /expired|timed out|restart/i,
  );
  const link = page.locator("#loginRestartLink");
  if (await link.count()) await link.click();
  await expect(page.locator("#username")).toBeVisible();
  expect(await page.locator("#kc-form-login").getAttribute("action")).not.toBe(
    oldAction,
  );
});
test("detached errors keep the shell and omit restart", async ({
  page,
  baseURL,
}) => {
  await page.goto(
    baseURL +
      "/realms/grounds-login-test/protocol/openid-connect/auth?client_id=missing-client",
  );
  await expect(page.locator(".grounds-card")).toBeVisible();
  await expect(page.locator("#grounds-restart-login")).toHaveCount(0);
  await expect(page.locator(".grounds-footer")).toBeVisible();
});
test("disabled JavaScript and a failed module provide readable recovery", async ({
  browser,
  baseURL,
}) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  const page = await context.newPage();
  await page.goto(auth(baseURL!));
  expect(await page.locator("noscript").textContent()).toContain("JavaScript");
  await expect(page.locator("noscript p")).toBeVisible();
  await expect(page.locator("form")).toHaveCount(0);
  await context.close();
  const failed = await browser.newContext();
  const p = await failed.newPage();
  await p.route("**/*.js", (route) => route.abort());
  await p.goto(auth(baseURL!));
  await expect(p.locator("#root")).toContainText("Reload page");
  await expect(p.locator("form")).toHaveCount(0);
  await failed.close();
});
const labels = {
  de: "Von vorn beginnen",
  en: "Start over",
  es: "Empezar de nuevo",
  fr: "Recommencer",
  tr: "Baştan başla",
};
for (const locale of Object.keys(labels) as (keyof typeof labels)[])
  test(`responsive ${locale} layout, language switch and local resources`, async ({
    browser,
    baseURL,
  }) => {
    for (const width of [1440, 360, 320]) {
      const context = await browser.newContext({
        viewport: { width, height: 900 },
        colorScheme: width === 360 ? "light" : "dark",
      });
      const page = await context.newPage();
      const remote: string[] = [];
      const errors: string[] = [];
      page.on("request", (request) => {
        if (new URL(request.url()).origin !== new URL(baseURL!).origin)
          remote.push(request.url());
      });
      page.on("pageerror", (e) => errors.push(e.message));
      const response = await page.goto(auth(baseURL!, { locale }));
      expect(response!.headers()["content-security-policy"]).toBeTruthy();
      await credentials(page);
      await expect(page.locator("#grounds-restart-login")).toHaveText(
        labels[locale],
      );
      await page.evaluate(() => document.fonts.ready);
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true);
      expect(await page.locator("html").getAttribute("lang")).toBe(locale);
      const language = await page.locator("#grounds-language").boundingBox();
      expect(language!.y).toBeLessThanOrEqual(24);
      expect(language!.x + language!.width).toBeGreaterThan(width - 40);
      expect(language!.height).toBeGreaterThanOrEqual(44);
      expect(
        await page
          .locator("body")
          .evaluate((e) => getComputedStyle(e).backgroundColor),
      ).toBe("rgb(24, 29, 32)");
      if (process.env.KEYCLOAK_SCREENSHOT_DIR) {
        await mkdir(process.env.KEYCLOAK_SCREENSHOT_DIR, { recursive: true });
        await page.screenshot({
          path: `${process.env.KEYCLOAK_SCREENSHOT_DIR}/${locale}-${width}-mfa.png`,
          fullPage: true,
        });
      }
      const next = locale === "en" ? "de" : "en";
      await page.locator("#grounds-language").selectOption(next);
      await expect(page.locator("#otp")).toBeVisible();
      await expect(page.locator("#kc-attempted-username")).toHaveValue(
        "otp-user",
      );
      await expect(page.locator("html")).toHaveAttribute("lang", next);
      expect(remote).toEqual([]);
      expect(errors).toEqual([]);
      await context.close();
    }
  });

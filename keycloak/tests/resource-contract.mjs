import assert from "node:assert/strict";
const resources = new Set(["image", "font", "script", "stylesheet"]);
export function verifyThemeResources(records, roots) {
  assert.ok(Array.isArray(roots) && roots.length >= 1);
  const targets = records.filter((r) => resources.has(r.type));
  for (const request of targets) {
    assert.ok(
      roots.some((root) => request.url.startsWith(root + "/")),
      `Resource outside Keycloak prefix: ${request.url}`,
    );
    assert.ok(
      !request.failure,
      `Resource failed: ${request.url} ${request.failure}`,
    );
    if (request.status !== undefined)
      assert.ok(
        request.status >= 200 && request.status < 400,
        `Resource HTTP ${request.status}: ${request.url}`,
      );
  }
  for (const [kind, pattern] of [
    ["image", /grounds-logo[^/]*\.webp/],
    ["image", /hero-clockwork-(?:1920|960)[^/]*\.avif/],
    ["font", /bricolage[^/]*\.woff2/],
  ])
    assert.ok(
      targets.some(
        (r) =>
          r.type === kind &&
          pattern.test(r.url) &&
          r.status >= 200 &&
          r.status < 400,
      ),
      `Expected ${pattern} was not delivered successfully`,
    );
}

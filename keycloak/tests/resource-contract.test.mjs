import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import test from "node:test";
const target = new URL("./resource-contract.mjs", import.meta.url);
test("candidate resources must load successfully beneath the server-provided theme roots", async () => {
  assert.ok(
    existsSync(target),
    "Candidate needs an actual resource policy, not only an origin check",
  );
  const { verifyThemeResources } = await import(target.href);
  const root = "http://127.0.0.1:1234/resources/cache/login/grounds-login";
  const good = [
    {
      url: root + "/dist/assets/grounds-logo.webp",
      type: "image",
      status: 200,
    },
    {
      url: root + "/dist/assets/hero-clockwork-960.avif",
      type: "image",
      status: 200,
    },
    {
      url: root + "/dist/assets/bricolage-latin.woff2",
      type: "font",
      status: 200,
    },
  ];
  assert.doesNotThrow(() => verifyThemeResources(good, [root]));
  for (const bad of [
    [
      ...good,
      {
        url: "http://127.0.0.1:1234/brand/logo.png",
        type: "image",
        status: 200,
      },
    ],
    good.map((r, i) => (i === 0 ? { ...r, status: 404 } : r)),
    good.map((r, i) => (i === 2 ? { ...r, failure: "net::ERR_FAILED" } : r)),
    good.filter((r) => r.type !== "font"),
  ])
    assert.throws(() => verifyThemeResources(bad, [root]));
});

import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { startFixtureServer } from "./oidc-fixture-server.mjs";
const image = process.argv[2];
if (!image) throw Error("Usage: node keycloak/tests/run.mjs <candidate-image>");
const fixture = await startFixtureServer();
let child;
const stop = () => child?.kill("SIGTERM");
process.once("SIGINT", stop);
process.once("SIGTERM", stop);
try {
  child = spawn(
    "bash",
    [
      fileURLToPath(new URL("../scripts/smoke-test.sh", import.meta.url)),
      image,
    ],
    {
      stdio: "inherit",
      env: {
        ...process.env,
        GROUNDS_KEYCLOAK_BROWSER: "1",
        KEYCLOAK_TEST_CALLBACK_BASE: fixture.url,
      },
    },
  );
  process.exitCode = await new Promise((resolve, reject) => {
    child.once("error", reject);
    child.once("exit", (code) => resolve(code ?? 1));
  });
} finally {
  await fixture.close();
}

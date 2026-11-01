# Bedrock sidebar prototype

Stage-only PoC before implementing the same animated NPC models for Bedrock.
The image inherits the exact existing Geyser runtime and adds one independent
resource pack. NetherNet, Floodgate, mandatory nameplates and Java clients keep
using their existing runtime and data paths. No NPC assets are included.

Version 0.1.3 styles Bedrock's existing scoreboard directly. It keeps the native
`scoreboard_sidebar`, its player factory, its collection-length binding and its
per-item text binding. The paired score factory stays registered but invisible.
There is no second sidebar, copied fallback, slash-path override or selection by
objective-title type or row count. Live labels, rank, Coins, friends and optional
match IDs continue to come from the server, including locale changes and updates
from any proxy. All Stage Bedrock scoreboards use the native branded frame.

The header uses the unchanged first-party Grounds logo. The original numeric
sorting column and empty objective title are hidden inline. Rows display their
live text with room for an icon; translated rank, Coins and friends labels get a
first-party icon. A fixed domain footer avoids repeating the server's domain
row. Rank uses a crown in this PoC; a personal head and Java-style badge are not
implemented. Long lines remain bounded by the native label's width.

The UI contract was compared with the exact installed Bedrock 1.26.60 Windows
client. Its original sidebar definition matches Mojang's public
`resource_pack/ui/scoreboards.json` in `Mojang/bedrock-samples`. Earlier variants
that tried routing to copied templates did not pass in-game acceptance; native
collection bindings are now preserved in their original instances.

The logo, separators and icons are unchanged first-party PNGs from
`groundsgg/resourcepacks` platform pack `8b88835212ae`. Geyser's integrated pack,
nameplate atlas, HUD/cooldown controls and pause screen are not replaced.

Regenerate, test and build:

```sh
python3 geyser/sidebar-poc/layout.py
python3 -m unittest discover -s geyser/sidebar-poc -v
python3 geyser/sidebar-poc/build.py
```

Use `geyser/sidebar-poc` as the Docker context. The branch-only workflow builds a
pinned Stage image; the normal Geyser Dockerfile does not include the PoC.
JSON and ZIP checks validate packaging and native binding contracts. They do not
prove rendering; visual acceptance in Bedrock remains required. The Windows UI
automation bridge is unavailable for this Linux workspace.

Rollback: restore the Stage Geyser image to
`sha-c8b6f55@sha256:1f7370fb6409756449596c0917b32005dfe99903a9507ed6353d241b45281f70`.

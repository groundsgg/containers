# Bedrock sidebar prototype

Stage-only PoC before implementing the same animated NPC models for Bedrock.
The image inherits the exact existing Geyser runtime and adds one independent
resource pack. NetherNet, Floodgate, mandatory nameplates and Java clients keep
using their existing runtime and data paths. No NPC assets are included.

Version 0.1.5 styles Bedrock's existing scoreboard directly. It keeps the native
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
row. Known ranks use the shared mandatory nameplate pack's existing badge atlas;
unknown ranks keep their live text. Rank uses a crown in this PoC; a personal
head is not implemented. Long lines remain bounded by the native label's width.

The UI contract was compared with the exact installed Bedrock 1.26.60 Windows
client. Its original sidebar definition matches Mojang's public
`resource_pack/ui/scoreboards.json` in `Mojang/bedrock-samples`. Earlier variants
that tried routing to copied templates did not pass in-game acceptance; native
collection bindings are now preserved in their original instances.

Version 0.1.3 incorrectly assumed inline children in a replaced `controls` array
would inherit vanilla definitions by matching their names. This removed types
from six child controls and removed the score factory. Version 0.1.4 supplies
complete child definitions, including both factories. A regression check now
validates replacement children and the paired native collection contracts.

Version 0.1.5 aligns the visual layout with the measured Java sidebar: 9px text
rows, 27px section pitch, a 26px text inset and icons centered between the label
and value. Visible child sizing restores the section gaps Geyser omits, while
the duplicated domain row contributes zero height. A nine-sliced black mask
rounds only the left corners by 4px and keeps the native opacity binding. Known
labels and gold values use the Java palette. The existing logo and icon rasters
are unchanged. This is layout/pack validation; in-game acceptance is required.

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

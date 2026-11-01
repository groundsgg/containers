# Bedrock sidebar prototype

Stage-only PoC before implementing the same animated NPC models for Bedrock.
The image inherits the exact existing Geyser runtime and adds one independent
resource pack. NetherNet, Floodgate, mandatory nameplates and Java clients keep
using their existing runtime and data paths. No NPC assets are included.

Version 0.1.10 styles Bedrock's existing scoreboard directly. It keeps the native
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
unknown ranks keep their live text. The rank icon is the connecting player's
personal 8x8 face including its hat layer. Long lines remain bounded by the
native label's width.

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
labels use the Java palette. The existing logo and icon rasters
are unchanged. This is layout/pack validation; in-game acceptance is required.

Version 0.1.6 adds the small `GroundsSidebarHead` Geyser extension. During
`SessionLoadResourcePacksEvent` it reads that connection's Bedrock login skin,
crops the face and alpha-composites its hat, and registers a highest-priority
pack containing only `textures/grounds/sidebar/player_head.png` and a manifest.
No external skin service, Java-player lookup or proxy-local identity is used.
Pack UUIDs derive from the face pixels so a changed face invalidates the client
cache. Each connection owns a separate temporary pack file until disconnect;
shutdown and failed registration also clean up the files. Unsupported, empty or
malformed skin data keeps a crown fallback and does not block login. The skin
dimensions are bounded before image allocation. Skin/identity data is not logged.

The adapter accesses `GeyserSession.getClientData()` from the exact pinned
Geyser 2.12.1-b1280 runtime; this internal call must be reviewed before a runtime
upgrade. The Docker build compiles against that runtime and runs the head crop,
hat/hires, malformed-input, deterministic-cache and session-isolation checks.
The startup smoke test also verifies the extension initialization when run with
`GROUNDS_SMOKE_EXPECT_EXTENSION='Personal sidebar portraits ready.'`.

Version 0.1.7 corrects two failed assumptions from the first visual test of
0.1.6. Row labels no longer infer their height from visible child controls;
instead, `use_anchored_offset` and the native `#size_binding_y` property set
explicit 9px/18px row heights (zero for the duplicated footer). Numeric values
keep their original collection text and formatting, without a custom gold-text
conversion. The portrait now resolves the selected Bedrock geometry's UV canvas
and head/hat front UVs, including box and per-face mappings. Image resolution
alone is not a sufficient UV scale. Metadata logging includes dimensions and
face coordinates only, so the next client join can verify the selected layout.

Version 0.1.7 failed visual acceptance: the size binding compressed the native
rows, and the client's Persona face was not in the default 256x256 atlas. A
read-only snapshot of that connection showed an empty default head bone and a
separate 32x64 `animated_face` geometry with normalized front-face meshes.

Version 0.1.8 returns to fixed 9px native rows and places labels, values and icons
at fixed drawing offsets. This avoids both child-dependent and bound row sizes.
The frame adds 9px for the two section gaps, accounting for the hidden original
domain row. The recorded fallback translates to nine distinct Bedrock entries;
matching labels and native value formatting keep fields live without copying
the data into a new controller. Other scoreboard shapes remain PoC limitations.

Persona portraits now read the face animation from Geyser's already-verified
client-data JWT during the resource-pack event, before Java login clears that
field. Only the first face frame is retained. Its `animated_face` mesh selects
the head and hat UV rectangles; the ordinary body atlas is not used for Persona
faces. JWT input size, JSON depth, animation dimensions and frame count are
bounded. Unrelated claims are skipped, and the JWT is neither persisted nor
logged. The resource pack still contains only the 8x8 face PNG and its manifest.
The Persona regression fixture keeps only anonymized geometry, with synthetic
face pixels and synthetic JWT data. Classic skin handling remains available.

Version 0.1.8 rendered the head, badges and all values, but also drew a second
friends value in the generic text row. Its last exclusion was beyond character
1024 in a 1078-character condition, consistent with native expression truncation.
Version 0.1.9 replaces that compound guard with an equivalent compact string
comparison, guarded below 512 characters. It also moves the domain up 2 GUI
pixels so the glyphs remain inside the rounded panel. Native row sizes, section
positions and the personal-head extension are unchanged.

Version 0.1.10 hides the live text of the server's 26-space width-anchor row.
That row is wider than the 82px text child and otherwise paints an ellipsis at
the right edge. Its fixed 9px native height remains, preserving the layout.

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

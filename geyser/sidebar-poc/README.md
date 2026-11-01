# Bedrock lobby sidebar prototype

Throwaway Stage PoC, requested before implementing the same animated NPC models
for Bedrock. This adds a native JSON UI lobby frame to the exact existing Geyser
image. It does not enable Java fonts for Bedrock or replace the mandatory
nameplate pack. Only Stage's image pin is intended to use this prototype.

The logo, separators and three icons are unchanged first-party PNGs from
`groundsgg/resourcepacks`' platform pack `8b88835212ae`. There are no NPC assets.
Rank uses a crown in this PoC; a per-player head and Java-style badge are not yet
implemented. Labels, rank, Coins, friends and optional match ID remain live
server scoreboard text, including server-selected translations.

The lobby fallback has an empty objective title and 14 rows, or 15 with a match.
Bedrock can drop six empty rows, leaving 8 or 9. Therefore each displayed value
is selected by its stable score, never by its position in the compacted list:
rank 14/13, Coins 10/9, friends 6/5, match 2, domain 2 (or 1 with a match).
All connections read their own native scoreboard collections; no player UUID,
proxy-local lookup or alternative data cache is introduced.

The in-game 0.1.1 probe exposed two native JSON UI details: inherited controls
are merged, and standalone labels with a collection index do not acquire the
scoreboard's factory context. Version 0.1.2 explicitly hides the inherited
`main` and objective-background instances and instantiates live text using
Bedrock's `player_list_factory`. Its row panels share an overlay area and select
their visible label by the original score, rather than stacking compacted rows.

The original sidebar remains available for objectives outside that signature.
Its definition follows Mojang's `resource_pack/ui/scoreboards.json` from
`Mojang/bedrock-samples` (retrieved 2026-11-01), including the original player
and score factories. The PoC overrides only the `scoreboard` namespace, leaving
Geyser's HUD/cooldown controls and pause-screen scoreboard definitions alone.

Run `python3 -m unittest discover -s geyser/sidebar-poc -v`, then
`python3 geyser/sidebar-poc/build.py`. Build using `geyser/sidebar-poc` as the
Docker context. The branch-only workflow publishes an immutable image for a
Stage pin; the normal Geyser Dockerfile does not include this PoC.

Validation covers JSON/ZIP integrity, unchanged image base and badge pack,
native text bindings and full/compacted lobby rows. Geyser startup can validate
pack loading but does not render JSON UI. Final rendering, both UI profiles,
GUI scales, language changes and reconnect/server transfer require Bedrock
client acceptance. The Windows automation bridge was unavailable in the WSL
workspace during this PoC.

Rollback: restore the Stage image pin to
`sha-c8b6f55@sha256:1f7370fb6409756449596c0917b32005dfe99903a9507ed6353d241b45281f70`.

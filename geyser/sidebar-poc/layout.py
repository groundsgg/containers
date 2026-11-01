"""Style the existing Bedrock scoreboard, preserving its one native row factory."""

import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parent
WIDTH = 110


def player_binding():
    return {"binding_name": "#player_name_sidebar", "binding_type": "collection", "binding_collection_name": "scoreboard_players"}


def image(texture, size, offset=(0, 0), **extra):
    return {"type": "image", "texture": "textures/grounds/sidebar/" + texture,
            "size": list(size), "offset": list(offset), "anchor_from": "top_left",
            "anchor_to": "top_left", "layer": 2, **extra}


def contains_label(labels):
    return "(" + " or ".join(f"(not ((#player_name_sidebar - '{label}') = #player_name_sidebar))" for label in labels) + ")"


def visible_when(condition):
    return [player_binding(), {"binding_type": "view", "source_property_name": condition, "target_property_name": "#visible"}]


def rank_badges():
    source = ROOT.parent / "pack-src"
    def properties(name):
        return dict(line.split("=", 1) for line in (source / name).read_text().splitlines() if line and not line.startswith("#"))
    letters = properties("rank-letters.properties")
    for key, code in sorted(properties("rank-badges.properties").items()):
        label = key.rsplit(".", 1)[0]
        width = max(sum(len(letters[c].split(",")[0]) + 1 for c in label) + 4, 7)
        point = int(code, 16)
        yield label, width, [(point & 15) * 64, ((point & 255) // 16) * 64]


def panel_png():
    """Deterministic 4px left corners; the right edge stays flush and square."""
    width, height, samples = 5, 9, 8
    pixels = bytearray()
    for y in range(height):
        pixels.append(0)  # PNG filter: none
        for x in range(width):
            coverage = 0
            for dy in range(samples):
                for dx in range(samples):
                    px, py = x + (dx + .5) / samples, y + (dy + .5) / samples
                    cy = 4 if py < 4 else 5 if py > 5 else py
                    coverage += px >= 4 or (px - 4) ** 2 + (py - cy) ** 2 <= 16
            pixels.extend((0, 0, 0, round(255 * coverage / samples ** 2)))
    def chunk(name, data):
        return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels, 9)) + chunk(b"IEND", b""))


def document():
    # The factory, collection and length binding are the installed 26.60
    # client's own scoreboard contract. No second sidebar instance is created.
    players = {
        "type": "stack_panel", "size": [WIDTH, "100%c"], "orientation": "vertical",
        "anchor_from": "top_left", "anchor_to": "top_left",
        "collection_name": "scoreboard_players",
        "factory": {"name": "player_list_factory", "control_name": "scoreboard.scoreboard_sidebar_player"},
        "bindings": [{"binding_name": "#scoreboard_sidebar_size", "binding_type": "global", "binding_name_override": "#collection_length"}],
    }
    labels = ("RANG", "RANK", "COINS", "FREUNDE", "FRIENDS")
    badge_variants = list(rank_badges())
    badge_condition = contains_label(tuple(s for name, _, _ in badge_variants for s in (name, name.title())))
    label_condition = contains_label(labels)
    domain_condition = contains_label(("grounds.gg",))
    gold_condition = contains_label(("§e", "§6"))
    regular = f"(not ({label_condition} or {badge_condition} or {domain_condition} or {gold_condition}))"
    row_controls = [{"live_text": {
        "type": "label", "size": [82, 9], "max_size": [82, 9], "offset": [26, 0],
        "anchor_from": "top_left", "anchor_to": "top_left", "layer": 3,
        "text": "#player_name_sidebar", "localize": False, "shadow": False,
        "locked_alpha": 1.0, "color": "$player_name_color", "bindings": visible_when(regular),
    }}]
    # Geyser drops the empty rows between Java's sections. Restore their 9px
    # spacing in the same native item context, without a second factory.
    row_controls.append({"section_spacing": {"type": "panel", "size": [WIDTH, 18],
        "bindings": visible_when(contains_label(("COINS", "FREUNDE", "FRIENDS")))}})
    row_controls.append({"row_spacing": {"type": "panel", "size": [WIDTH, 9],
        "bindings": visible_when(f"(not {domain_condition})")}})
    for label in labels:
        row_controls.append({"label_" + label.lower(): {
            "type": "label", "text": label, "size": [82, 9], "offset": [26, 0 if label in ("RANG", "RANK") else 9],
            "anchor_from": "top_left", "anchor_to": "top_left", "layer": 3, "localize": False,
            "shadow": False, "locked_alpha": 1.0, "color": [193 / 255, 185 / 255, 170 / 255],
            "bindings": visible_when(contains_label((label,))),
        }})
    # Use the exact shared badge atlas already supplied by the mandatory pack.
    for name, width, uv in badge_variants:
        row_controls.append({"badge_" + name.lower(): {
            "type": "image", "texture": "font/glyph_E6", "uv": uv, "uv_size": [width, 8],
            "size": [width, 8], "offset": [26, 0], "anchor_from": "top_left", "anchor_to": "top_left",
            "layer": 3, "bindings": visible_when(contains_label((name, name.title()))),
        }})
    plain_gold = "(#player_name_sidebar" + "".join(" - '§" + code + "'" for code in "0123456789abcdefklmnor") + ")"
    row_controls.append({"gold_value": {
        "type": "label", "size": [82, 9], "max_size": [82, 9], "offset": [26, 0],
        "anchor_from": "top_left", "anchor_to": "top_left", "layer": 3, "text": "#plain_gold",
        "localize": False, "shadow": False, "locked_alpha": 1.0, "color": [231 / 255, 184 / 255, 75 / 255],
        "bindings": visible_when(f"({gold_condition} and not ({label_condition} or {badge_condition} or {domain_condition}))") + [{
            "binding_type": "view", "source_property_name": plain_gold, "target_property_name": "#plain_gold",
        }],
    }})
    icons = []
    for name, texture, labels, color in (
        ("rank_icon", "crown", ("RANG", "RANK"), [0.906, 0.722, 0.294]),
        ("coins_icon", "coin", ("COINS",), [0.906, 0.722, 0.294]),
        ("friends_icon", "friends", ("FREUNDE", "FRIENDS"), [0.475, 0.788, 0.745]),
    ):
        icons.append({name: image(texture, (10, 10), (8, 4 if name == "rank_icon" else 13),
            color=color, bindings=visible_when(contains_label(labels)))})
    # Icons extend across both text rows; their 10px size must not change the
    # 9px native line pitch. The zero-height panel only supplies their anchor.
    row_controls.append({"icons": {"type": "panel", "size": [WIDTH, 0],
        "anchor_from": "top_left", "anchor_to": "top_left", "controls": icons}})
    return {
        "namespace": "scoreboard",
        "scoreboard_sidebar_score": {"visible": False},
        "scoreboard_sidebar_player": {
            # Keep the original label control/factory registration. Its children
            # share the same live item context; the label itself paints no text.
            "type": "label", "text": "", "size": [WIDTH, "100%cm"], "max_size": [WIDTH, "100%cm"],
            "localize": False, "bindings": [player_binding(), {
                "binding_type": "view",
                "source_property_name": "((#player_name_sidebar - 'grounds.gg') = #player_name_sidebar)",
                "target_property_name": "#visible",
            }], "controls": row_controls,
        },
        "scoreboard_sidebar": {
            "type": "panel", "size": [WIDTH, "100%c"],
            "anchor_from": "right_middle", "anchor_to": "right_middle",
            "controls": [
                {"main": {
                    # `controls` replaces an array, so these children need
                    # complete definitions rather than partial named patches.
                    "type": "image", "texture": "textures/grounds/sidebar/panel", "nineslice_size": [4, 4, 0, 4], "alpha": "#alpha",
                    "anchor_from": "top_middle", "anchor_to": "top_middle",
                    "bindings": [{"binding_name": "#objective_background_opacity", "binding_name_override": "#alpha"}],
                    "size": [WIDTH, "100%c"],
                    "controls": [
                        {"displayed_objective": {
                            "type": "label", "text": "#objective_sidebar_name", "visible": False,
                            "size": [0, 0], "anchor_from": "top_middle", "anchor_to": "top_middle",
                            "localize": False, "bindings": [{"binding_name": "#objective_sidebar_name", "binding_type": "global"}],
                        }},
                        {"header": {"type": "panel", "size": [WIDTH, 25],
                            "anchor_from": "top_middle", "anchor_to": "top_middle",
                            "controls": [
                                {"grounds_logo": image("logo", (72, 14), (19, 5), uv=[0, 0], uv_size=[216, 42])},
                                {"top_separator": image("separator", (90, 9), (10, 22), uv=[0, 0], uv_size=[180, 18], color=[193 / 255, 185 / 255, 170 / 255])},
                            ]}},
                        {"lists": {
                            "type": "panel", "anchor_from": "top_middle", "anchor_to": "top_middle",
                            "size": [WIDTH, "100%cm"], "offset": [0, 25], "inherit_max_sibling_width": False,
                            "controls": [{"players": players}, {"horizontal_padding": {"type": "panel", "ignored": True}},
                                {"scores": {
                                    "type": "stack_panel", "visible": False, "size": [0, "100%c"],
                                    "anchor_from": "top_right", "anchor_to": "top_right",
                                    "orientation": "vertical", "use_child_anchors": True,
                                    "collection_name": "scoreboard_scores",
                                    "factory": {"name": "player_score_factory", "control_name": "scoreboard.scoreboard_sidebar_score"},
                                    "bindings": [{"binding_name": "#scoreboard_sidebar_size", "binding_type": "global", "binding_name_override": "#collection_length"}],
                                }}],
                        }},
                        {"footer": {"type": "panel", "size": [WIDTH, 13],
                            "anchor_from": "bottom_middle", "anchor_to": "bottom_middle", "controls": [
                                {"footer_separator": image("separator", (90, 9), (10, -8),
                                    uv=[0, 0], uv_size=[180, 18], color=[193 / 255, 185 / 255, 170 / 255])},
                                {"grounds_domain": {"type": "label", "size": [WIDTH, 9], "offset": [0, 6],
                                    "anchor_from": "top_left", "anchor_to": "top_left",
                                    "text": "grounds.gg", "text_alignment": "center", "localize": False,
                                    "locked_alpha": 1.0, "layer": 3, "color": [193 / 255, 185 / 255, 170 / 255]}},
                            ]}},
                    ],
                }},
                {"displayed_objective_background": {
                    "type": "image", "texture": "textures/ui/Black", "alpha": "#alpha",
                    "visible": False, "size": [0, 0],
                    "anchor_from": "top_middle", "anchor_to": "top_middle", "layer": 1,
                    "bindings": [{"binding_name": "#scoreboard_objective_background_opacity", "binding_name_override": "#alpha"}],
                }},
            ],
        },
    }


if __name__ == "__main__":
    target = ROOT / "pack/ui/scoreboards.json"
    target.write_text(json.dumps(document(), indent=2) + "\n")
    (ROOT / "pack/textures/grounds/sidebar/panel.png").write_bytes(panel_png())
    manifest = ROOT / "pack/manifest.json"
    data = json.loads(manifest.read_text())
    data["header"]["version"] = [0, 1, 5]
    data["modules"][0]["version"] = [0, 1, 5]
    manifest.write_text(json.dumps(data, separators=(",", ":")))

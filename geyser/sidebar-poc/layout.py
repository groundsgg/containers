"""Style the existing Bedrock scoreboard, preserving its one native row factory."""

import json
from pathlib import Path

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
    row_controls = [{"live_text": {
        "type": "label", "size": [82, 10], "max_size": [82, 10], "offset": [24, 0],
        "anchor_from": "top_left", "anchor_to": "top_left", "layer": 3,
        "text": "#player_name_sidebar", "localize": False, "shadow": False,
        "locked_alpha": 1.0, "color": "$player_name_color", "bindings": [player_binding()],
    }}]
    for name, texture, labels, color in (
        ("rank_icon", "crown", ("RANG", "RANK"), [0.906, 0.722, 0.294]),
        ("coins_icon", "coin", ("COINS",), [0.906, 0.722, 0.294]),
        ("friends_icon", "friends", ("FREUNDE", "FRIENDS"), [0.475, 0.788, 0.745]),
    ):
        row_controls.append({name: image(texture, (10, 10), (7, 0), color=color, bindings=[
            player_binding(), {"binding_type": "view", "source_property_name": contains_label(labels), "target_property_name": "#visible"},
        ])})
    return {
        "namespace": "scoreboard",
        "scoreboard_sidebar_score": {"visible": False},
        "scoreboard_sidebar_player": {
            # Keep the original label control/factory registration. Its children
            # share the same live item context; the label itself paints no text.
            "type": "label", "text": "", "size": [WIDTH, 10], "max_size": [WIDTH, 10],
            "localize": False, "bindings": [player_binding(), {
                "binding_type": "view",
                "source_property_name": "((#player_name_sidebar - 'grounds.gg') = #player_name_sidebar)",
                "target_property_name": "#visible",
            }], "controls": row_controls,
        },
        "scoreboard_sidebar": {
            "size": [WIDTH, "100%c"],
            "controls": [
                {"main": {
                    "size": [WIDTH, "100%c"],
                    "controls": [
                        {"displayed_objective": {"visible": False, "size": [0, 0]}},
                        {"header": {"type": "panel", "size": [WIDTH, 27],
                            "anchor_from": "top_middle", "anchor_to": "top_middle",
                            "controls": [
                                {"grounds_logo": image("logo", (72, 14), (19, 5), uv=[0, 0], uv_size=[216, 42])},
                                {"top_separator": image("separator", (90, 9), (10, 18), uv=[0, 0], uv_size=[180, 18], color=[0.757, 0.725, 0.667])},
                            ]}},
                        {"lists": {
                            "size": [WIDTH, "100%cm"], "offset": [0, 27], "inherit_max_sibling_width": False,
                            "controls": [{"players": players}, {"horizontal_padding": {"ignored": True}},
                                {"scores": {"visible": False, "size": [0, "100%c"]}}],
                        }},
                        {"footer_separator": image("separator", (90, 9), (0, -10),
                            anchor_from="bottom_middle", anchor_to="bottom_middle",
                            uv=[0, 0], uv_size=[180, 18], color=[0.757, 0.725, 0.667])},
                        {"grounds_domain": {"type": "label", "size": [WIDTH, 10],
                            "anchor_from": "bottom_middle", "anchor_to": "bottom_middle",
                            "text": "grounds.gg", "text_alignment": "center", "localize": False,
                            "locked_alpha": 1.0, "layer": 3, "color": [0.757, 0.725, 0.667]}},
                    ],
                }},
                {"displayed_objective_background": {"visible": False, "size": [0, 0]}},
            ],
        },
    }


if __name__ == "__main__":
    target = ROOT / "pack/ui/scoreboards.json"
    target.write_text(json.dumps(document(), indent=2) + "\n")
    manifest = ROOT / "pack/manifest.json"
    data = json.loads(manifest.read_text())
    data["header"]["version"] = [0, 1, 3]
    data["modules"][0]["version"] = [0, 1, 3]
    manifest.write_text(json.dumps(data, separators=(",", ":")))

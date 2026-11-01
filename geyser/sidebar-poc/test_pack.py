import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
import zlib

from build import ROOT, build


def controls(node):
    return {name: child for entry in node.get("controls", []) for name, child in entry.items()}


class NativeSidebarTests(unittest.TestCase):
    def setUp(self):
        self.ui = json.loads((ROOT / "pack/ui/scoreboards.json").read_text())

    def test_existing_native_renderer_is_the_only_entry_point(self):
        # Resolve existing names without copied scoreboards, slash paths or title heuristics.
        self.assertEqual(set(self.ui), {"namespace", "scoreboard_sidebar", "scoreboard_sidebar_player", "scoreboard_sidebar_score"})
        self.assertFalse(any(
            "#objective_sidebar_name" in binding.get("source_property_name", "")
            for binding in self.ui["scoreboard_sidebar"].get("bindings", [])
        ))

    def test_live_text_keeps_the_original_native_factory_and_collection(self):
        self.assertIn("main", controls(self.ui["scoreboard_sidebar"]))
        main = controls(self.ui["scoreboard_sidebar"])["main"]
        players = controls(controls(main)["lists"])["players"]
        self.assertEqual(players["collection_name"], "scoreboard_players")
        self.assertEqual(players["factory"], {"name": "player_list_factory", "control_name": "scoreboard.scoreboard_sidebar_player"})
        self.assertEqual(players["bindings"], [{"binding_name": "#scoreboard_sidebar_size", "binding_type": "global", "binding_name_override": "#collection_length"}])
        live = controls(self.ui["scoreboard_sidebar_player"])["live_text"]
        self.assertEqual(live["text"], "#player_name_sidebar")
        self.assertEqual(live["bindings"][0], {"binding_name": "#player_name_sidebar", "binding_type": "collection", "binding_collection_name": "scoreboard_players"})
        self.assertFalse(live["localize"])
        self.assertNotIn("collection_index", json.dumps(self.ui))
        self.assertNotIn("#player_score_sidebar", json.dumps(self.ui))

    def test_generic_text_visibility_filter_stays_compact(self):
        # In 0.1.8 the only duplicated value was guarded by the final clause
        # beyond character 1024. Keep this native expression well below that.
        live = controls(self.ui["scoreboard_sidebar_player"])["live_text"]
        expression = live["bindings"][-1]["source_property_name"]
        self.assertLessEqual(len(expression), 512)

    def test_replaced_controls_arrays_are_self_contained(self):
        # Arrays replace the original list: named children do not inherit the
        # original child's type or descendants by matching its name.
        def check(node, path):
            for name, child in controls(node).items():
                with self.subTest(control=path + "/" + name):
                    self.assertTrue("type" in child or "@" in name,
                                    "replacement child has no type or template")
                check(child, path + "/" + name)

        check(self.ui["scoreboard_sidebar"], "scoreboard_sidebar")
        main = controls(self.ui["scoreboard_sidebar"])["main"]
        scores = controls(controls(main)["lists"])["scores"]
        self.assertEqual(scores.get("factory"), {
            "name": "player_score_factory",
            "control_name": "scoreboard.scoreboard_sidebar_score",
        })
        self.assertEqual(scores.get("collection_name"), "scoreboard_scores")
        self.assertEqual(scores.get("bindings"), [{
            "binding_name": "#scoreboard_sidebar_size", "binding_type": "global",
            "binding_name_override": "#collection_length",
        }])
        self.assertEqual(main.get("type"), "image")
        self.assertEqual(main.get("texture"), "textures/grounds/sidebar/panel")

    def test_title_and_sorting_columns_are_removed_inline(self):
        root = controls(self.ui["scoreboard_sidebar"])
        self.assertIn("main", root)
        main = controls(root["main"])
        columns = controls(main["lists"])
        self.assertFalse(main["displayed_objective"].get("visible", True))
        self.assertFalse(root["displayed_objective_background"].get("visible", True))
        self.assertFalse(columns["scores"].get("visible", True))
        self.assertFalse(columns["scores"].get("ignored", False))
        self.assertTrue(columns["horizontal_padding"]["ignored"])
        self.assertFalse(self.ui["scoreboard_sidebar_score"]["visible"])
        self.assertFalse(self.ui["scoreboard_sidebar_score"].get("ignored", False))

    def test_body_is_independent_of_title_type_and_row_count(self):
        self.assertIn("scoreboard_sidebar_player", self.ui)
        row = self.ui["scoreboard_sidebar_player"]
        self.assertEqual(row["size"], [110, 9])
        self.assertEqual(controls(row)["live_text"]["offset"], [26, 0])
        self.assertNotIn("#objective_sidebar_name", json.dumps(row))
        self.assertNotIn("#scoreboard_sidebar_size", json.dumps(row))
        # Native text includes match IDs and translations; only the repeated domain is hidden.
        self.assertEqual(row["bindings"], [{"binding_name": "#player_name_sidebar", "binding_type": "collection", "binding_collection_name": "scoreboard_players"}])

    def test_java_spacing_and_icon_alignment_are_preserved(self):
        self.assertEqual(controls(self.ui["scoreboard_sidebar"])["main"]["size"], [110, "100%c + 9px"])
        main = controls(controls(self.ui["scoreboard_sidebar"])["main"])
        self.assertEqual(main["header"]["size"], [110, 25])
        self.assertEqual(controls(main["header"])["top_separator"]["offset"], [10, 22])
        self.assertEqual(main["lists"]["offset"], [0, 25])
        row = controls(self.ui["scoreboard_sidebar_player"])
        self.assertEqual(row["live_text"]["size"], [82, 9])
        native = self.ui["scoreboard_sidebar_player"]
        self.assertNotIn("#size_binding_y", json.dumps(native))
        self.assertNotIn("section_spacing", row)
        self.assertNotIn("gold_value", row)
        self.assertEqual(row["label_friends"]["offset"], [26, 18])
        self.assertEqual(row["coins_value"]["offset"], [26, 9])
        self.assertEqual(row["friends_value"]["offset"], [26, 18])
        icons = controls(row["icons"])
        self.assertEqual(icons["coins_icon"]["offset"], [8, 13])
        self.assertEqual(icons["friends_icon"]["offset"], [8, 22])
        self.assertEqual(icons["rank_icon"]["offset"], [9, 4])
        self.assertEqual(icons["rank_icon"]["size"], [8, 8])
        self.assertEqual(icons["rank_icon"]["color"], [1, 1, 1])
        self.assertEqual(icons["rank_icon"]["texture"], "textures/grounds/sidebar/player_head")
        self.assertEqual(main["footer"]["size"], [110, 13])
        self.assertEqual(controls(main["footer"])["footer_separator"]["offset"], [10, -8])
        self.assertEqual(controls(main["footer"])["grounds_domain"]["offset"], [0, 4])

    def test_badges_reuse_the_existing_mandatory_rank_atlas(self):
        row = controls(self.ui["scoreboard_sidebar_player"])
        for name, x, width in (("admin", 0, 32), ("dev", 64, 22), ("user", 128, 28), ("mod", 192, 22)):
            badge = row["badge_" + name]
            self.assertEqual(badge["texture"], "font/glyph_E6")
            self.assertEqual(badge["uv"], [x, 0])
            self.assertEqual(badge["uv_size"], [width, 8])
            self.assertEqual(badge["size"], [width, 8])
            self.assertEqual(badge["offset"], [26, 0])

    def test_background_rounds_only_the_left_edge_and_keeps_native_opacity(self):
        main = controls(self.ui["scoreboard_sidebar"])["main"]
        self.assertEqual(main["nineslice_size"], [4, 4, 0, 4])
        self.assertEqual(main["bindings"], [{"binding_name": "#objective_background_opacity", "binding_name_override": "#alpha"}])
        data = (ROOT / "pack/textures/grounds/sidebar/panel.png").read_bytes()
        self.assertEqual(struct.unpack_from(">II", data, 16), (5, 9))
        offset, compressed = 8, b""
        while offset < len(data):
            size = struct.unpack_from(">I", data, offset)[0]
            if data[offset + 4:offset + 8] == b"IDAT":
                compressed += data[offset + 8:offset + 8 + size]
            offset += size + 12
        pixels = zlib.decompress(compressed)
        self.assertEqual(pixels[4], 0)  # upper-left corner is transparent
        self.assertEqual(pixels[8 * 21 + 4], 0)  # lower-left corner
        self.assertEqual(pixels[4 * 21 + 4], 255)  # middle of left edge
        self.assertTrue(all(pixels[y * 21 + 20] == 255 for y in range(9)))

    def test_fixed_rows_keep_the_live_values_at_the_reference_positions(self):
        class Text(str):
            def __sub__(self, other):
                return Text(self.replace(other, ""))

        def visible(node, text):
            for binding in node.get("bindings", []):
                if binding.get("target_property_name") == "#visible":
                    expr = binding["source_property_name"].replace("#player_name_sidebar", "name").replace(" = ", " == ")
                    return bool(eval(expr, {"__builtins__": {}}, {"name": Text(text)}))
            return node.get("visible", True)

        # Replay the actual nine distinct translated names received by Bedrock.
        # Native rows stay fixed; only their drawing positions add section gaps.
        native = self.ui["scoreboard_sidebar_player"]
        row = controls(native)
        for rank, friends in (("RANG", "FREUNDE"), ("RANK", "FRIENDS")):
            texts = [" " * 26, "§h" + rank, "§hUser", "§hCOINS", "§p0", "§h" + friends, "§b0§r§h / 0", "§r", "§hgrounds.gg"]
            y, tops, values = 25, {}, []
            for text in texts:
                active = {k: v for k, v in row.items() if visible(v, text)}
                for field in ("badge_user", "coins_value", "friends_value"):
                    if field in active: values.append(y + active[field]["offset"][1])
                if text in ("§p0", "§b0§r§h / 0"):
                    self.assertNotIn("live_text", active)
                for name in ("label_" + rank.lower(), "label_coins", "label_" + friends.lower()):
                    if name in active:
                        tops[name] = y + active[name]["offset"][1]
                y += native["size"][1]
            self.assertEqual(list(tops.values()), [34, 61, 88])
            self.assertEqual(values, [43, 70, 97])
            self.assertEqual(y + 13 + 9, 128)

    def test_archive_is_reproducible_and_keeps_badges_and_npcs_separate(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / "a.mcpack", Path(directory) / "b.mcpack"
            build(a)
            build(b)
            self.assertEqual(a.read_bytes(), b.read_bytes())
            with zipfile.ZipFile(a) as pack:
                self.assertIsNone(pack.testzip())
                self.assertEqual(len(pack.namelist()), 9)
                manifest = json.loads(pack.read("manifest.json"))
                self.assertEqual(manifest["header"]["version"], [0, 1, 9])
                self.assertNotEqual(manifest["header"]["uuid"], "32f559d7-e76a-470e-ac36-8b458b99c84c")
                self.assertFalse(any(path.startswith(("font/", "entity/", "models/")) for path in pack.namelist()))
                for name in ("logo", "separator", "crown", "coin", "friends"):
                    self.assertIn(f"textures/grounds/sidebar/{name}.png", pack.namelist())


if __name__ == "__main__":
    unittest.main()

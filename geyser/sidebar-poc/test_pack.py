import json
from pathlib import Path
import tempfile
import unittest
import zipfile

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
        self.assertEqual(live["bindings"], [{"binding_name": "#player_name_sidebar", "binding_type": "collection", "binding_collection_name": "scoreboard_players"}])
        self.assertFalse(live["localize"])
        self.assertNotIn("collection_index", json.dumps(self.ui))
        self.assertNotIn("#player_score_sidebar", json.dumps(self.ui))

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
        self.assertEqual(main.get("texture"), "textures/ui/Black")

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
        self.assertEqual(row["size"], [110, 10])
        self.assertEqual(controls(row)["live_text"]["offset"], [24, 0])
        self.assertNotIn("#objective_sidebar_name", json.dumps(row))
        self.assertNotIn("#scoreboard_sidebar_size", json.dumps(row))
        # Native text includes match IDs and translations; only the repeated domain is hidden.
        self.assertIn("#player_name_sidebar", row["bindings"][-1]["source_property_name"])
        self.assertIn("grounds.gg", row["bindings"][-1]["source_property_name"])

    def test_archive_is_reproducible_and_keeps_badges_and_npcs_separate(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / "a.mcpack", Path(directory) / "b.mcpack"
            build(a)
            build(b)
            self.assertEqual(a.read_bytes(), b.read_bytes())
            with zipfile.ZipFile(a) as pack:
                self.assertIsNone(pack.testzip())
                self.assertEqual(len(pack.namelist()), 7)
                manifest = json.loads(pack.read("manifest.json"))
                self.assertEqual(manifest["header"]["version"], [0, 1, 4])
                self.assertNotEqual(manifest["header"]["uuid"], "32f559d7-e76a-470e-ac36-8b458b99c84c")
                self.assertFalse(any(path.startswith(("font/", "entity/", "models/")) for path in pack.namelist()))
                for name in ("logo", "separator", "crown", "coin", "friends"):
                    self.assertIn(f"textures/grounds/sidebar/{name}.png", pack.namelist())


if __name__ == "__main__":
    unittest.main()

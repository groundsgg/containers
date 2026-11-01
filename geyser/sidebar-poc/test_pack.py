import json
from pathlib import Path
import re
import tempfile
import unittest
import zipfile

from build import ROOT, build


def text(component):
    if isinstance(component, str):
        return component
    return component.get("text", "") + "".join(map(text, component.get("extra", [])))


def fields(panel):
    return {name: control for item in panel["controls"] for name, control in item.items()}


def visible_values(panel, prefix, collection):
    result = []
    for name, control in fields(panel).items():
        if not name.startswith(prefix + "_") or "collection_index" not in control:
            continue
        condition = control["bindings"][-1]["source_property_name"]
        wanted = int(re.search(r"= (\d+)", condition).group(1))
        index = control["collection_index"]
        if index < len(collection) and collection[index][0] == wanted:
            result.append(collection[index][1])
    return result


class SidebarPrototypeTests(unittest.TestCase):
    def setUp(self):
        self.ui = json.loads((ROOT / "pack/ui/scoreboards.json").read_text())
        self.rows = json.loads((ROOT / "fallback-fixture.json").read_text())["rows"]

    def test_real_server_values_survive_empty_row_compaction(self):
        complete = [(15 - index, text(component)) for index, component in enumerate(self.rows)]
        compact = [(score, value) for score, value in complete if value]
        self.assertEqual((len(complete), len(compact)), (14, 8))
        for collection in (complete, compact):
            for prefix, expected in (
                ("rank_label", "RANG"), ("coins_label", "COINS"),
                ("coins_value", "1.240"), ("friends_label", "FREUNDE"),
                ("friends_value", "3 / 12"), ("domain", "grounds.gg"),
            ):
                self.assertEqual(visible_values(self.ui["grounds_lobby_sidebar"], prefix, collection), [expected])
        # A fresh server update changes the live label, not pack-generated text.
        changed = [(score, "9 / 23" if score == 5 else value) for score, value in compact]
        self.assertEqual(visible_values(self.ui["grounds_lobby_sidebar"], "friends_value", changed), ["9 / 23"])

    def test_match_and_domain_survive_compaction_and_do_not_overlap(self):
        rows = [(15 - index, text(component)) for index, component in enumerate(self.rows[:-1])]
        rows.extend(((2, "#a1b2c3d4"), (1, "grounds.gg")))
        for collection in (rows, [(score, value) for score, value in rows if value]):
            panel = self.ui["grounds_lobby_match_sidebar"]
            self.assertEqual(visible_values(panel, "match_id", collection), ["#a1b2c3d4"])
            self.assertEqual(visible_values(panel, "domain", collection), ["grounds.gg"])
        controls = fields(panel)
        self.assertLess(controls["match_id_0"]["offset"][1], controls["domain_0"]["offset"][1])

    def test_only_the_lobby_uses_the_branded_layout(self):
        fallback = json.dumps(self.ui["grounds_vanilla_sidebar"])
        self.assertIn("scoreboard.scoreboard_sidebar_score", fallback)
        self.assertIn("not", fallback)
        for key in ("grounds_lobby_sidebar", "grounds_lobby_match_sidebar"):
            for control in fields(self.ui[key]).values():
                self.assertNotEqual(control.get("text"), "#player_score_sidebar")
                if "collection_index" in control:
                    self.assertEqual(control["text"], "#player_name_sidebar")
                    self.assertEqual({binding.get("binding_collection_name") for binding in control["bindings"] if "binding_collection_name" in binding}, {"scoreboard_players", "scoreboard_scores"})

    def test_archive_is_reproducible_and_cannot_replace_the_badge_pack(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / "a.mcpack", Path(directory) / "b.mcpack"
            build(a)
            build(b)
            self.assertEqual(a.read_bytes(), b.read_bytes())
            with zipfile.ZipFile(a) as pack:
                self.assertIsNone(pack.testzip())
                self.assertEqual(len(pack.namelist()), 7)
                manifest = json.loads(pack.read("manifest.json"))
                self.assertNotEqual(manifest["header"]["uuid"], manifest["modules"][0]["uuid"])
                self.assertNotEqual(manifest["header"]["uuid"], "32f559d7-e76a-470e-ac36-8b458b99c84c")
                self.assertFalse(any(path.startswith(("font/", "entity/", "models/")) for path in pack.namelist()))
                for control in fields(self.ui["grounds_lobby_sidebar"]).values():
                    texture = control.get("texture", "")
                    if texture.startswith("textures/grounds/"):
                        self.assertIn(texture + ".png", pack.namelist())


if __name__ == "__main__":
    unittest.main()

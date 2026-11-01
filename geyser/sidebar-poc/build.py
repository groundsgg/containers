"""Package the throwaway Bedrock HUD without rebuilding Geyser or its badge pack."""

from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent


def build(target=ROOT / "grounds-sidebar-poc.mcpack"):
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for source in sorted((ROOT / "pack").rglob("*")):
            if source.is_file():
                entry = zipfile.ZipInfo(source.relative_to(ROOT / "pack").as_posix(), (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, source.read_bytes())
    return target


if __name__ == "__main__":
    print(build())

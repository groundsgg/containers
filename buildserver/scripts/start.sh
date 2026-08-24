#!/bin/sh
set -eu

# Prefer the Paper start script baked beside this wrapper in the image.
PAPER_START=/opt/grounds/paper-start.sh
DATA_ROOT=${BUILDSERVER_DATA_ROOT:-/data}

# Idempotently move a durable path onto the PVC and replace it with a symlink
# from /app so Paper keeps writing under its usual layout while data survives
# pod replacements. Plugin JARs stay on the image under /app/plugins/*.jar.
link_durable() {
  src="$1"
  dest="$2"
  mkdir -p "$(dirname "$dest")"
  if [ -L "$src" ]; then
    return 0
  fi
  if [ -e "$src" ] && [ ! -e "$dest" ]; then
    mv "$src" "$dest"
  else
    mkdir -p "$dest"
    if [ -e "$src" ] && [ ! -L "$src" ]; then
      rm -rf "$src"
    fi
  fi
  ln -sfn "$dest" "$src"
}

if [ -d "$DATA_ROOT" ]; then
  link_durable /app/world "$DATA_ROOT/world"
  link_durable /app/plugins/BuildSystem "$DATA_ROOT/plugins/BuildSystem"
  link_durable /app/plugins/GroundsMaps "$DATA_ROOT/plugins/GroundsMaps"
  # Tool plugin data dirs appear after first boot; link any that exist.
  for plugin_dir in \
    FastAsyncWorldEdit \
    FastAsyncVoxelSniper \
    AxiomPaper \
    Axiom \
    goPaintAdvanced \
    CreativeUtilities \
    EasyArmorStands \
    HeadDatabase
  do
    if [ -d "/app/plugins/$plugin_dir" ] || [ -L "/app/plugins/$plugin_dir" ]; then
      link_durable "/app/plugins/$plugin_dir" "$DATA_ROOT/plugins/$plugin_dir"
    fi
  done
fi

if [ ! -x "$PAPER_START" ]; then
  echo "buildserver: missing $PAPER_START" >&2
  exit 1
fi

exec sh "$PAPER_START"

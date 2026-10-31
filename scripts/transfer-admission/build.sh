#!/bin/sh
set -eu
[ "$#" -eq 3 ] || { echo 'Usage: build.sh input.jar output.jar expected-sha256' >&2; exit 2; }
source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT HUP INT TERM
javac -cp "$1" -d "$classes" "$source_dir/TransferAdmission.java" "$source_dir/PatchRuntime.java"
java -cp "$classes:$1" gg.grounds.build.transfer.PatchRuntime "$1" "$2" "$3" "$classes/gg/grounds/runtime/transfer"

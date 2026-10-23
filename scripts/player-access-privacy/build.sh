#!/bin/sh
set -eu

if [ "$#" -ne 4 ]; then
    echo "Usage: build.sh velocity|geyser input.jar output.jar expected-sha256" >&2
    exit 2
fi
runtime=$1
input=$2
output=$3
checksum=$4
source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT HUP INT TERM

# JDK 25's supported Class-File API; no instrumentation agent or ASM dependency.
javac -cp "$input" -d "$classes" \
    "$source_dir/PlayerAccessLogPrivacy.java" "$source_dir/PatchRuntime.java"
java -cp "$classes:$input" gg.grounds.build.privacy.PatchRuntime \
    "$runtime" "$input" "$output" \
    "$classes/gg/grounds/runtime/logging/PlayerAccessLogPrivacy.class" "$checksum"

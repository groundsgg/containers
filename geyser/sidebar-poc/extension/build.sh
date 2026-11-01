#!/bin/sh
set -eu
extension_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
extension_geyser_jar=$1
extension_output=$2
mkdir -p "$extension_output/classes" "$extension_output/tests"
javac --release 21 -cp "$extension_geyser_jar" -d "$extension_output/classes" "$extension_root"/src/*.java
javac --release 21 -cp "$extension_output/classes" -d "$extension_output/tests" "$extension_root"/test/*.java
java -Djava.awt.headless=true -cp "$extension_output/classes:$extension_output/tests" gg.grounds.sidebar.HeadPackTest
jar --create --file "$extension_output/grounds-sidebar-head.jar" -C "$extension_output/classes" . -C "$extension_root" extension.yml

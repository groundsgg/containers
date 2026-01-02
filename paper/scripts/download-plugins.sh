#!/bin/sh
set -eu

if [ -z "$PLUGINS" ]; then
  exit 0
fi

mkdir -p /app/plugins

IFS=','
for plugin in $PLUGINS; do
  IFS=':'
  set -- $plugin
  repository="$1"
  artifact_id="$2"
  version="$3"
  IFS=','
  
  if [ -z "$repository" ] || [ -z "$artifact_id" ] || [ -z "$version" ]; then
    echo "Invalid plugin format: $plugin (expected: repository:artifactId:version)" >&2
    exit 1
  fi
  
  url="https://maven.pkg.github.com/groundsgg/${repository}/gg/grounds/${artifact_id}/${version}/${artifact_id}-${version}.jar"
  output_file="/app/plugins/${artifact_id}.jar"
  
  echo "Downloading plugin: ${artifact_id} (${version}) from ${repository}..."
  
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    curl -fsSL -H "Authorization: Bearer $GITHUB_TOKEN" -o "$output_file" "$url"
  else
    curl -fsSL -o "$output_file" "$url"
  fi
  
  echo "Downloaded: ${output_file}"
done

#!/bin/sh
set -eu

if [ -z "$PLUGINS" ]; then
  exit 0
fi

mkdir -p /app/plugins

old_ifs="$IFS"
IFS=','
for plugin in $PLUGINS; do
  IFS=':'
  set -- $plugin
  repository="$1"
  artifact_id="$2"
  version="$3"
  IFS=','
  
  if [ -z "$repository" ] || [ -z "$artifact_id" ] || [ -z "$version" ]; then
    IFS="$old_ifs"
    echo "Invalid plugin format: $plugin (expected: repository:artifactId:version)" >&2
    exit 1
  fi
  
  url="https://maven.pkg.github.com/groundsgg/${repository}/gg/grounds/${artifact_id}/${version}/${artifact_id}-${version}.jar"
  output_file="/app/plugins/${artifact_id}.jar"
  
  echo "Downloading plugin: ${artifact_id} (${version}) from ${repository}..."
  
  tmp_file="$(mktemp)"
  http_code="000"
  
  http_code=$(curl -sSL -w "%{http_code}" -H "Authorization: Bearer ${GITHUB_TOKEN:-}" -o "$tmp_file" "$url" || echo "000")
  
  if [ "$http_code" != "200" ]; then
    IFS="$old_ifs"
    echo "Error: Failed to download plugin '${artifact_id}' (version: ${version}) from repository '${repository}'." >&2
    echo "       HTTP Status: $http_code" >&2
    echo "       URL: ${url}" >&2
    rm -f "$tmp_file"
    exit 1
  fi
  
  if [ ! -s "$tmp_file" ]; then
    IFS="$old_ifs"
    echo "Error: Downloaded file for plugin '${artifact_id}' (version: ${version}) is empty." >&2
    echo "       URL: ${url}" >&2
    rm -f "$tmp_file"
    exit 1
  fi
  
  if ! head -c 4 "$tmp_file" | grep -q '^PK'; then
    IFS="$old_ifs"
    echo "Error: Downloaded file for plugin '${artifact_id}' (version: ${version}) does not appear to be a valid JAR file (missing ZIP header)." >&2
    echo "       URL: ${url}" >&2
    rm -f "$tmp_file"
    exit 1
  fi
  
  mv "$tmp_file" "$output_file"
  echo "Downloaded: ${output_file}"
done
IFS="$old_ifs"

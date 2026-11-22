#!/usr/bin/env bash
set -euo pipefail

output=${1:?installation directory is required}
version=2.14.2
asset="git-cliff-$version-x86_64-unknown-linux-gnu.tar.gz"
sha256=24f397c733add5390fdceee3a2088588ab0d5f944ce00d34cb7029b888cf2db4

temporary=$(mktemp -d)
trap 'rm -rf -- "$temporary"' EXIT
curl --fail --silent --show-error --location --retry 3 --max-time 120 \
    "https://github.com/orhun/git-cliff/releases/download/v$version/$asset" -o "$temporary/$asset"
printf '%s  %s\n' "$sha256" "$temporary/$asset" | sha256sum --check --status
mkdir -p "$output"
tar -xzf "$temporary/$asset" --directory "$output" --strip-components=1 "git-cliff-$version/git-cliff"
"$output/git-cliff" --version

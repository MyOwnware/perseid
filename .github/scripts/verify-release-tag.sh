#!/usr/bin/env bash
set -euo pipefail

tag=${1:?release tag is required}
expected=${2:?event commit is required}
fingerprint=DE8CEF57AE9EB80689C45B0C82B7C1FCC73F30EF
ref="refs/tags/$tag"

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

[[ $tag =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$ ]] ||
    fail 'Expected a stable vX.Y.Z tag with minor and patch versions below 100.'
[[ $(git cat-file -t "$ref" 2>/dev/null) == tag ]] ||
    fail 'Release requires an annotated tag.'
commit=$(git rev-parse --verify "$ref^{commit}")
[[ $commit == "$(git rev-parse --verify --end-of-options "$expected^{commit}")" ]] ||
    fail 'The release tag does not point at the event commit.'
git merge-base --is-ancestor "$commit" refs/remotes/origin/main ||
    fail 'The release tag must point to a commit on main.'

export GNUPGHOME
GNUPGHOME=$(mktemp -d)
trap 'rm -rf -- "$GNUPGHOME"' EXIT
gpg --batch --quiet --import .github/keys/release-signing.asc
signature=$(git -c gpg.format=openpgp -c gpg.program=gpg verify-tag --raw "$ref" 2>&1) ||
    fail 'The tag signature could not be verified.'
awk -v fingerprint="$fingerprint" '
    $1 == "[GNUPG:]" && $2 == "VALIDSIG" && $NF == fingerprint { trusted = 1 }
    END { exit !trusted }
' <<<"$signature" || fail 'The tag signer is not the pinned release owner.'
printf 'Verified %s on main.\n' "$tag"

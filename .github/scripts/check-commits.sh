#!/usr/bin/env bash
set -euo pipefail

range="$1..$2"
pattern='^(feat|fix|refactor|test|docs|ci|build|chore|perf)(\([a-z0-9]+([/-][a-z0-9]+)*\))?!?: [a-z0-9][^[:cntrl:]]*$'
failed=0

subjects=$(git log --format='%s' "$range")
while IFS= read -r subject; do
    if [[ ! $subject =~ $pattern || ${#subject} -gt 72 || $subject == *. ]]; then
        printf 'Invalid commit subject: %s\n' "$subject" >&2
        failed=1
    fi
done <<< "$subjects"

exit "$failed"

#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

assert_ignored() {
    if ! git check-ignore --quiet --no-index -- "$1"; then
        printf 'Expected Git to ignore: %s\n' "$1" >&2
        exit 1
    fi
}

assert_trackable() {
    if git check-ignore --quiet --no-index -- "$1"; then
        printf 'Expected Git to allow: %s\n' "$1" >&2
        exit 1
    fi
}

for path in \
    data/closed-traverse.mv.db \
    data/closed-traverse.trace.db \
    data/archive/measurements.json \
    target/classes/App.class \
    .env \
    .env.local \
    logs/application.log \
    .idea/workspace.xml \
    project.iml \
    AGENTS.md \
    docs/guide.markdown \
    docs/notes.mdown \
    src/.DS_Store; do
    assert_ignored "$path"
done

for path in \
    data/.gitkeep \
    .env.example \
    README.md \
    src/main/resources/application.properties; do
    assert_trackable "$path"
done

if ! git ls-files --error-unmatch -- README.md >/dev/null 2>&1; then
    printf 'README.md must be tracked.\n' >&2
    exit 1
fi

tracked_markdown=$(git ls-files -- '*.md' '*.markdown' '*.mdown' | sed '/^README\.md$/d')
if [[ -n "$tracked_markdown" ]]; then
    printf 'Only README.md may be tracked as Markdown:\n%s\n' "$tracked_markdown" >&2
    exit 1
fi

printf 'Git ignore rules passed.\n'

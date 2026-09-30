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
    README.md \
    docs/guide.markdown \
    docs/notes.mdown \
    src/.DS_Store; do
    assert_ignored "$path"
done

for path in \
    data/.gitkeep \
    .env.example \
    src/main/resources/application.properties; do
    assert_trackable "$path"
done

tracked_markdown=$(git ls-files -- '*.md' '*.markdown' '*.mdown')
if [[ -n "$tracked_markdown" ]]; then
    printf 'Markdown files must not be tracked:\n%s\n' "$tracked_markdown" >&2
    exit 1
fi

printf 'Git ignore rules passed.\n'

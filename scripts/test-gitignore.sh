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

printf 'Git ignore rules passed.\n'

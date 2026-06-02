#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: ./release.sh <version> [commit message]"
  echo "Example: ./release.sh 1.3.0"
  echo "         ./release.sh 1.3.0 \"release 1.3.0\""
  exit 1
fi

VERSION="$1"
MESSAGE="${2:-release $VERSION}"
TAG="v$VERSION"

if git tag -l "$TAG" | grep -q .; then
  echo "Error: tag $TAG already exists"
  exit 1
fi

if ! git diff --quiet HEAD 2>/dev/null; then
  echo "Error: working tree has uncommitted changes, please commit or stash first"
  exit 1
fi

echo "==> Updating version to $VERSION in build.gradle.kts"
sed -i '' "s/^version = \".*\"/version = \"$VERSION\"/" build.gradle.kts

echo "==> Committing"
git add build.gradle.kts
git commit -m "$MESSAGE"

echo "==> Tagging $TAG"
git tag "$TAG"

echo "==> Pushing commit and tag"
git push origin HEAD "$TAG"

echo "==> Done! GitHub Actions will build and create the Release."

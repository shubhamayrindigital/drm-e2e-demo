#!/bin/bash
set -euo pipefail

# Download Big Buck Bunny (1080p, H.264, AAC) from Blender Foundation
# CC-BY 3.0 license

OUTPUT="bbb_1080p.mp4"

if [ -f "$OUTPUT" ]; then
  echo "✓ $OUTPUT already exists, skipping download"
  exit 0
fi

echo "Downloading Big Buck Bunny (1080p, ~350MB)..."
echo "Source: https://www.blender.org/about/projects/open-movies/"

# Blender's CDN mirror
URL="https://download.blender.org/demo/movies/BBB/bbb_sunflower_1080p_h264.mov"

curl -L -o "$OUTPUT" "$URL"

echo "✓ Downloaded to $OUTPUT"
echo ""
echo "Next: run ./package.sh $OUTPUT"

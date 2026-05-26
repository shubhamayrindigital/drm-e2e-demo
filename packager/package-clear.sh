#!/bin/bash
set -euo pipefail

# Non-DRM packaging: DASH + HLS (clear)
# Input: input-video.mp4
# Output: out/clear/ with DASH manifest + HLS master + CMAF fragments

INPUT_VIDEO="${1:?Usage: package-clear.sh <input.mp4>}"
OUTDIR="./out/clear"

if [ ! -f "$INPUT_VIDEO" ]; then
  echo "Error: $INPUT_VIDEO not found"
  exit 1
fi

mkdir -p "$OUTDIR"

echo "Packaging clear (non-DRM) stream..."
echo "Input: $INPUT_VIDEO"
echo "Output: $OUTDIR"

shaka-packager \
  "in=${INPUT_VIDEO},stream=video,output=${OUTDIR}/video.mp4" \
  "in=${INPUT_VIDEO},stream=audio,output=${OUTDIR}/audio.mp4" \
  --mpd_output "${OUTDIR}/manifest.mpd" \
  --hls_master_playlist_output "${OUTDIR}/master.m3u8" \
  --segment_duration 4

echo ""
echo "✓ Clear packaging complete"
echo ""
echo "Next steps:"
echo "1. Upload ${OUTDIR}/* to Cloudflare R2 under vod/clear-bbb/"
echo "2. Backend catalog.ts will reference https://r2.example.com/vod/clear-bbb/manifest.mpd"
echo ""

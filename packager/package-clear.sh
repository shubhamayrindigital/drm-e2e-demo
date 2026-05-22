#!/bin/bash
set -euo pipefail

# Non-DRM packaging: DASH + HLS (clear)
# Input: mezzanine.mp4
# Output: out/clear/ with DASH manifest + HLS master + CMAF fragments

MEZZANINE="${1:?Usage: package-clear.sh <input.mp4>}"
OUTDIR="./out/clear"

if [ ! -f "$MEZZANINE" ]; then
  echo "Error: $MEZZANINE not found"
  exit 1
fi

mkdir -p "$OUTDIR"

echo "Packaging clear (non-DRM) stream..."
echo "Input: $MEZZANINE"
echo "Output: $OUTDIR"

shaka-packager \
  "in=${MEZZANINE},stream=video,output=${OUTDIR}/video.mp4" \
  "in=${MEZZANINE},stream=audio,output=${OUTDIR}/audio.mp4" \
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

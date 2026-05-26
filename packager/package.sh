#!/bin/bash
set -euo pipefail

# DRM packaging: DASH + CENC (Widevine)
# Uses Google's published Widevine UAT test KID/CEK
# Input: input-video.mp4
# Output: out/drm/ with encrypted DASH manifest + CMAF fragments

INPUT_VIDEO="${1:?Usage: package.sh <input.mp4>}"
OUTDIR="./out/drm"

if [ ! -f "$INPUT_VIDEO" ]; then
  echo "Error: $INPUT_VIDEO not found"
  exit 1
fi

mkdir -p "$OUTDIR"

# Google Widevine UAT test KID/CEK (published, for testing only)
KID="abba271e8bcf552bbd2e86a434a9a5d9"
CEK="69eaa802a6763af979e0d6ed5e2c4ed7"
CONTENT_ID="fkj3ljaSdfalkr3j"

echo "Packaging DRM stream (Widevine CENC)..."
echo "Input: $INPUT_VIDEO"
echo "Output: $OUTDIR"
echo "KID: $KID"
echo "Content ID: $CONTENT_ID"

shaka-packager \
  "in=${INPUT_VIDEO},stream=video,output=${OUTDIR}/video.mp4" \
  "in=${INPUT_VIDEO},stream=audio,output=${OUTDIR}/audio.mp4" \
  --enable_raw_key_encryption \
  --keys "label=CENC:key_id=${KID}:key=${CEK}" \
  --protection_scheme cenc \
  --protection_systems Widevine \
  --pssh "" \
  --mpd_output "${OUTDIR}/manifest.mpd" \
  --hls_master_playlist_output "${OUTDIR}/master.m3u8" \
  --segment_duration 4

echo ""
echo "✓ DRM packaging complete"
echo ""
echo "Test credentials (already in DB seed):"
echo "  Content ID: $CONTENT_ID"
echo "  KID: $KID"
echo "  CEK: $CEK"
echo ""
echo "Next steps:"
echo "1. Upload ${OUTDIR}/* to Cloudflare R2 under vod/drm-bbb/"
echo "2. Backend will seed Content row with these KID/CEK values"
echo "3. License proxy will use CEK to issue licenses via Google UAT"
echo ""

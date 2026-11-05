#!/bin/sh
# Downloads BRouter routing data (5x5 degree tiles) into data/segments4.
# Re-run to update: only tiles that changed on the server are downloaded again.
#
# Default tiles cover Luxembourg and the Greater Region (Saarland, Lorraine,
# Rhineland-Palatinate, Wallonia). Pass other tile names to add areas, e.g.
#   ./scripts/download-segments.sh E5_N45 E10_N45
# Tile names: https://brouter.de/brouter/segments4/  (E5_N45 = 5-10°E, 45-50°N)
set -eu

cd "$(dirname "$0")/.."
TILES=${*:-"E0_N45 E0_N50 E5_N45 E5_N50"}
BASE_URL=${BROUTER_SEGMENTS_URL:-https://brouter.de/brouter/segments4}

mkdir -p data/segments4 data/customprofiles data/db

for tile in $TILES; do
  target="data/segments4/$tile.rd5"
  echo "-> $tile"
  if [ -f "$target" ]; then
    curl -fL --progress-bar -z "$target" -o "$target.part" "$BASE_URL/$tile.rd5"
  else
    curl -fL --progress-bar -o "$target.part" "$BASE_URL/$tile.rd5"
  fi
  # curl -z writes nothing when the server copy is not newer
  if [ -s "$target.part" ]; then
    mv "$target.part" "$target"
  else
    rm -f "$target.part"
    echo "   up to date"
  fi
done

echo "Done. Restart BRouter to pick up new tiles: docker compose restart brouter"

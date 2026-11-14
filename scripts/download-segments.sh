#!/bin/sh
# Downloads BRouter routing data (5x5 degree tiles) into data/segments4.
# Re-run to update: only tiles that changed on the server are downloaded again.
#
# Without arguments it downloads the tiles around TRAILPLANNER_AREA (from the environment or
# .env; "latitude,longitude[,zoom]", default Luxembourg): the tile with that point and its
# neighbours within about 100 km. Pass tile names to choose them yourself, e.g.
#   ./scripts/download-segments.sh E5_N45 E10_N45
# Tile names: https://brouter.de/brouter/segments4/  (E5_N45 = 5-10°E, 45-50°N)
set -eu

cd "$(dirname "$0")/.."

area=${TRAILPLANNER_AREA:-}
if [ -z "$area" ] && [ -f .env ]; then
  area=$(sed -n 's/^TRAILPLANNER_AREA=//p' .env | tail -n 1 | tr -d '"'"'"' \r')
fi

# The 5x5 degree tiles within 1 degree of latitude and 1.5 degrees of longitude of the area.
tiles_around() {
  echo "${1:-49.61,6.13}" | awk -F, '
    function floor5(x) { return 5 * int((x >= 0 ? x : x - 4.999999) / 5) }
    function name(lng, lat) {
      return (lng < 0 ? "W" (-lng) : "E" lng) "_" (lat < 0 ? "S" (-lat) : "N" lat)
    }
    NF < 2 || $1 + 0 != $1 || $2 + 0 != $2 {
      print "TRAILPLANNER_AREA must be latitude,longitude[,zoom], not: " $0 > "/dev/stderr"; exit 1
    }
    {
      lat = $1 + 0; lng = $2 + 0; out = ""
      for (y = floor5(lat - 1); y <= floor5(lat + 1); y += 5)
        for (x = floor5(lng - 1.5); x <= floor5(lng + 1.5); x += 5)
          out = out " " name(x, y)
      print substr(out, 2)
    }'
}

if [ $# -gt 0 ]; then
  TILES="$*"
else
  TILES=$(tiles_around "$area")
  echo "Tiles around ${area:-Luxembourg (default)}: $TILES"
fi
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

#!/bin/sh
# Copies a GPX file onto a USB-connected ELEMNT/BOLT (Linux, via gio/MTP).
# Windows: copy the GPX into "This PC > ELEMNT BOLT > ... > routes" in Explorer.
# macOS:   use an MTP app such as OpenMTP and copy into the "routes" folder.
#
# Usage: ./scripts/copy-to-bolt.sh route.gpx
# Then on the BOLT: map page -> ROUTE -> press SYNC if the route is not listed.
set -eu

[ $# -eq 1 ] || { echo "usage: $0 <file.gpx>" >&2; exit 1; }
command -v gio >/dev/null || { echo "gio not found (install gvfs / gvfs-backends)" >&2; exit 1; }

device=$(gio mount -li | sed -n 's/.*activation_root=\(mtp:\/\/[^ ]*\).*/\1/p' | head -n 1)
[ -n "$device" ] || { echo "No MTP device found. Is the BOLT switched on and connected with a data cable?" >&2; exit 1; }

gio mount "$device" 2>/dev/null || true
routes=$(gio list "$device" | while read -r storage; do
  if gio info "$device$storage/routes" >/dev/null 2>&1; then echo "$device$storage/routes"; break; fi
done)
[ -n "$routes" ] || { echo "No routes folder found on $device" >&2; exit 1; }

gio copy "$1" "$routes/"
echo "Copied $(basename "$1") to $routes"

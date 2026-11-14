#!/bin/sh
# Starts Trail Planner in Docker and waits until the planner is reachable.
#
# Usage: ./run.sh          build (if needed) and start, then print the URL
#        ./run.sh stop     stop all containers
#        ./run.sh logs     follow the logs
#        ./run.sh status   show the containers
set -eu

cd "$(dirname "$0")"

command -v docker >/dev/null || { echo "Docker is not installed." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose (v2) is not available." >&2; exit 1; }

case "${1:-start}" in
  stop) exec docker compose down ;;
  logs) exec docker compose logs -f ;;
  status) exec docker compose ps ;;
  start) ;;
  *) echo "usage: $0 [start|stop|logs|status]" >&2; exit 1 ;;
esac

# Create the data folders ourselves: if Docker creates them they belong to root and the backend
# (uid 1000) cannot write the database or the routing profiles.
mkdir -p data/segments4 data/customprofiles data/db

if ! ls data/segments4/*.rd5 >/dev/null 2>&1; then
  echo "No map data yet. Downloading the routing data for TRAILPLANNER_AREA (a few hundred MB, only needed once)…"
  ./scripts/download-segments.sh
fi

# Same port as docker-compose.yml: TRAILPLANNER_PORT from the environment or .env, default 8080.
port=${TRAILPLANNER_PORT:-}
if [ -z "$port" ] && [ -f .env ]; then
  port=$(sed -n 's/^TRAILPLANNER_PORT=//p' .env | tail -n 1 | tr -d '"'"'"' \r')
fi
port=${port:-8080}

docker compose up -d --build

printf 'Waiting for Trail Planner to start'
i=0
until curl -fsS -o /dev/null "http://localhost:$port/api/routing/preferences/defaults" 2>/dev/null; do
  i=$((i + 1))
  if [ "$i" -ge 90 ]; then
    echo
    echo "Trail Planner did not answer after 3 minutes. Check the logs: $0 logs" >&2
    exit 1
  fi
  printf '.'
  sleep 2
done
echo
echo "Trail Planner is running: http://localhost:$port   (stop with: $0 stop)"

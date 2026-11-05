# Trailplan

A self-hosted mountain-bike route planner on OpenStreetMap data, for planning rides on a Wahoo
ELEMNT BOLT.

- Click the map to set points, drag points to move them, drag the route line to add a via point.
- Routing prefers the way types you choose (singletrails, forest tracks, roads …), respects a
  maximum trail difficulty (`mtb:scale`) and can prefer riding through woods.
- Shows distance, climbing, estimated time, an elevation profile and a way-type/surface breakdown.
- Saves routes in a library (SQLite) and exports GPX for the BOLT.

```
browser ──► web (nginx + Vue/MapLibre) ──/api──► backend (Spring Boot, SQLite)
                                                    │ writes profiles  │ HTTP
                                                    ▼                  ▼
                                           data/customprofiles ◄── brouter (BRouter 1.7.10)
                                                                       ▲
                                                               data/segments4 (map tiles)
```

## Quick start (Docker)

Requirements: Docker with Compose, about 1 GB of disk for the map data.

```sh
cp .env.example .env                # optional, defaults are fine for local use
./run.sh                            # downloads map data on first run, builds, starts, waits
```

`./run.sh stop` stops it, `./run.sh logs` follows the logs, `./run.sh status` lists the containers.
Without the script: `./scripts/download-segments.sh` (BRouter data for Luxembourg and the Greater
Region), then `docker compose up -d --build`.

Open <http://localhost:8080>.

Map data is updated weekly on brouter.de. Re-run `./scripts/download-segments.sh` (it only downloads
changed tiles) and `docker compose restart brouter`.

To cover other areas, pass tile names: `./scripts/download-segments.sh E5_N45 E10_N45`. A tile covers
5×5 degrees; `E5_N45` is 5–10° east, 45–50° north.

**Riders.** On first start you add riders (just names). Each rider has their own library, trash,
imports, Wahoo connection and planner state; the chosen rider is remembered per browser.

> **No login.** Riders have no passwords and Trailplan has no authentication: everyone who can reach
> it can open every rider's library. Run it on your own machine or
> home network. If you expose it to the internet, put it behind a reverse proxy with authentication.

## Getting routes onto the BOLT

**USB (recommended for the BOLT V2).** Switch the BOLT on, connect it with a USB data cable and copy
the downloaded GPX into the device's `routes` folder:

- Windows: the BOLT appears under “This PC”.
- macOS: needs an MTP app such as OpenMTP.
- Linux: any MTP-capable file manager, or `./scripts/copy-to-bolt.sh route.gpx` (uses `gio`; best
  effort, tested only against the documented MTP layout).

On the BOLT, open the map page, choose ROUTE and press SYNC if the new route isn't listed.

**ELEMNT app.** Open the GPX on your phone (or use “Share GPX” in the planner on a phone) and choose
the ELEMNT app; it syncs the route to the BOLT.

**Wahoo Cloud API (optional, off by default).** Sends routes straight to your Wahoo account as FIT
course files. Needs an app registered at <https://developers.wahooligan.com> (with Wahoo's approval
process) and the redirect URI `<TRAILPLAN_PUBLIC_URL>/api/wahoo/callback`. Set `WAHOO_ENABLED=true`,
`WAHOO_CLIENT_ID` and `WAHOO_CLIENT_SECRET` in `.env`. Note: a Wahoo API reference states that
API-created routes reach the newer Wahoo app and head units but not the legacy ELEMNT app, which the
BOLT V2 may still use. Test with your device before relying on it.

## Development

Run BRouter in Docker and the backend and frontend locally. The backend needs Java 25 and Maven
3.9+; with [SDKMAN!](https://sdkman.io) run `sdk env install` in the project root, which installs
and activates the versions in `.sdkmanrc` (or set `sdkman_auto_env=true` to switch automatically).

```sh
./scripts/download-segments.sh
docker compose up -d brouter           # listens on 127.0.0.1:17777

cd backend
mvn spring-boot:run                    # http://localhost:8081, DB in ../data/trailplan.db

cd frontend
npm install
npm run dev                            # http://localhost:5173, proxies /api to 8081
```

The backend writes routing profiles to `../data/customprofiles`, which the BRouter container mounts.

### Checks

| Part     | Command                     | What it does                                                    |
|----------|-----------------------------|-----------------------------------------------------------------|
| Backend  | `mvn verify`                | Compiles with Error Prone, runs tests, checks formatting        |
| Backend  | `mvn spotless:apply`        | Formats Java code (google-java-format)                          |
| Frontend | `npm run typecheck`         | `vue-tsc` with strict settings (`noUncheckedIndexedAccess` etc.) |
| Frontend | `npm run lint`              | ESLint (typescript-eslint strict, type-checked) + Prettier check |
| Frontend | `npm run format`            | Formats with Prettier                                           |

## How routing works

The backend turns your preferences into a BRouter profile (`BRouterProfileGenerator`). Each way type
gets a cost factor: Favourite 1, Prefer 1.5, Neutral 2.5, Avoid 6, Never 10000 (impassable). Ways
above the chosen `mtb:scale`, against the direction of one-ways, or closed to bikes are blocked.
With “prefer woods”, ways outside forest (BRouter's `estimated_forest_class`) cost 1.5× or 2.2× more.

Profiles are written to the shared `customprofiles` directory, named by a hash of their content, and
requested as `custom_tp_<hash>`. Each leg between two points is routed and cached separately, so
moving one point recalculates only its two legs.

### Notes on BRouter

- `brouter/Dockerfile` builds from the official release zip. The Dockerfile shipped with BRouter
  1.7.9 pins a deprecated base image with a fixed 128 MB heap and one thread.
- The custom profile directory **must be passed as a relative path** (`../customprofiles`). BRouter
  resolves custom profiles against the main profile directory, so an absolute path would be looked
  up as `/profiles2/customprofiles/…` and fail.
- BRouter rejects profiles that mention tag values not in its `lookups.dat`. The generator only uses
  known values (see `MTB_SCALE_VALUES`).

## What has been tested

- Generated profiles parse with BRouter 1.7.9's own parser, and the cost factors for sample ways
  match the intent (trails preferred, blocked difficulty, private access, wrong-way one-ways, forest
  bonus).
- End to end against a real BRouter 1.7.9 server with a small synthetic map: the default profile
  takes the trail, “Never” for trails and an S0 difficulty limit switch to the road, and the relative
  custom profile path resolves. A point outside the downloaded tiles gives
  `datafile … not found`, which is shown as a clear message.
- FIT course files decode cleanly with Garmin's official FIT SDK (including CRCs).
- Frontend: type check, lint and production build pass.

- Backend: `mvn verify` (Error Prone, unit tests, a smoke test that boots the whole app on a
  temporary SQLite database, and the formatting check) passes on Java 25 with Spring Boot 4.1.

Not yet tested: the frontend in a real browser, the Docker images, the end-to-end checks above
against BRouter 1.7.10 (the version the image now uses), and the Wahoo API against a real account.

## Credits

Map data © OpenStreetMap contributors (ODbL). Routing: [BRouter](https://github.com/abrensch/brouter)
(MIT). Base map: [OpenFreeMap](https://openfreemap.org); raster maps: CyclOSM, OpenTopoMap, Esri World
Imagery; MTB overlay: Waymarked Trails. Respect each tile provider's usage policy; for heavy use,
host your own tiles.

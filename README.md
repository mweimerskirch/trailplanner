# Trail Planner

A self-hosted mountain-bike route planner on OpenStreetMap data, for planning rides on a Wahoo
ELEMNT BOLT. It also plans road-bike rides and horseback rides.

- Click the map to set points, drag points to move them, drag the route line to add a via point.
- Routing prefers the way types you choose (singletrails, forest tracks, roads …), respects a
  maximum trail difficulty (`mtb:scale`) and can prefer riding through woods.
- Activities: mountain bike, road bike (paved ways only) and horseback (horse access, no steps or
  stiles, soft ground preferred). Each has its own presets and average speed.
- "Trails" map overlay on any base map: paths and tracks from OpenStreetMap coloured by difficulty
  (mtb:scale S0 green, S1 blue, S2 red, S3+ black), unrated ones dashed brown, ways closed to bikes
  dotted grey. Trails harder than the riding style allows are faded. Pointing at a path or road
  shows its name, type, difficulty, surface and bike access.
- In English, French and German (switch in the header; the browser's language by default).
- Shows distance, climbing, estimated time, an elevation profile and a way-type/surface breakdown.
- Saves routes in a library (SQLite) and exports GPX for the BOLT.
- Imports the rides the BOLT recorded (Rides): distance, moving time, climbing, speed, heart rate
  and the track on a map; a ride can be saved to the library as a route.

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
cp .env.example .env                # then set TRAILPLANNER_AREA to where you ride
./run.sh                            # downloads map data on first run, builds, starts, waits
```

`./run.sh stop` stops it, `./run.sh logs` follows the logs, `./run.sh status` lists the containers.
Without the script: `./scripts/download-segments.sh` (BRouter data around `TRAILPLANNER_AREA`),
then `docker compose up -d --build`.

Open <http://localhost:8080>.

Map data is updated weekly on brouter.de. Re-run `./scripts/download-segments.sh` (it only downloads
changed tiles) and `docker compose restart brouter`.

**Your area.** Set `TRAILPLANNER_AREA` in `.env` to `latitude,longitude` or
`latitude,longitude,zoom`, e.g. `46.95,7.45,11` (without it, Luxembourg). New visitors' map opens
there, and `./scripts/download-segments.sh` downloads the routing data within about 100 km of it.
Routing only works where the data is downloaded. For a larger area, pass tile names:
`./scripts/download-segments.sh E5_N45 E10_N45`. A tile covers 5×5 degrees; `E5_N45` is 5–10° east,
45–50° north. Restart with `docker compose up -d` after changing `.env`.

**Riders.** On first start you add riders (just names). Each rider has their own library, trash,
imports, presets and planner state; the chosen rider is remembered per browser.

> **No login.** Riders have no passwords and Trail Planner has no authentication: everyone who can reach
> it can open every rider's library. Run it on your own machine or
> home network. If you expose it to the internet, put it behind a reverse proxy with authentication.

## Getting routes onto the BOLT

**Sync over USB (recommended).** Switch the BOLT on, connect it with a USB data cable to the computer
that runs Trail Planner, and choose **Sync to ELEMNT** in the library. The BOLT's `routes` folder then
holds exactly your active routes (not archived, not in the trash) as GPX files: new and changed
routes are copied, unchanged ones are skipped. Files in the folder that are not active routes (for
example archived routes, or files from other apps) are listed and deleted only if you confirm. The
BOLT keeps routes it has already loaded when their file is deleted, so delete those on the BOLT too.
Changed routes replace the BOLT's copy (same file name).

- The BOLT must not be open in a file manager at the same time (eject it there first): only one
  program can talk to it.
- In Docker the backend gets the USB bus (`/dev/bus/usb`, see `docker-compose.yml`) and runs as uid
  1000. Desktop Linux gives the logged-in user access to MTP devices; if your user is not uid 1000,
  or on a headless server, add a udev rule that makes the device accessible, e.g.
  `SUBSYSTEM=="usb", ATTR{idVendor}=="05c6", ATTR{idProduct}=="9039", MODE="0666"` (the BOLT V2;
  check yours with `lsusb`) in `/etc/udev/rules.d/70-elemnt.rules`.
- The USB part is a small C helper on libmtp (`backend/mtp/trailplanner-mtp.c`), built into the backend
  image. To run the backend outside Docker, build it with `libmtp-dev` installed:
  `gcc -O2 -o ~/.local/bin/trailplanner-mtp backend/mtp/trailplanner-mtp.c $(pkg-config --cflags --libs libmtp)`.

**Copy by hand.** Copy a downloaded GPX into the device's `routes` folder:

- Windows: the BOLT appears under “This PC”.
- macOS: needs an MTP app such as OpenMTP.
- Linux: any MTP-capable file manager, or `./scripts/copy-to-bolt.sh route.gpx` (uses `gio`; best
  effort, tested only against the documented MTP layout).

On the BOLT, open the map page, choose ROUTE and press SYNC if the new route isn't listed.

**ELEMNT app.** Open the GPX on your phone (or use “Share GPX” in the planner on a phone) and choose
the ELEMNT app; it syncs the route to the BOLT.

## Importing recorded rides

The BOLT keeps every recorded ride as a FIT file in its `exports` folder. On the **Rides** page,
**Import from ELEMNT** (BOLT connected over USB, as for the sync) imports the rides that are not in
Trail Planner yet. Files are recognised by name and size, so later imports only download new rides.
Recordings without a GPS track (e.g. started by accident) are skipped. **Import FIT files…** takes
FIT files from other devices or apps.

Times are shown in the local time where the ride was recorded. Deleting a ride in Trail Planner does not
touch the device, and importing from the device does not bring it back; uploading its FIT file does.

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

The activity changes the rules on top of that:

- Road bike: rough surfaces (gravel, dirt, grass …), bad smoothness, tracks without a surface or a
  solid grade, and any trail with an `mtb:scale` are blocked. Compacted and fine gravel cost 3×,
  cobbles extra. Paths with `bicycle=designated` count as cycleways. Busy roads by BRouter's traffic
  estimate cost a little more. Woods do not count.
- Horseback: `horse=` decides access instead of `bicycle=`. Footways and cycleways are used only
  where horses are allowed. Trunk roads, steps and paths from `sac_scale=demanding_mountain_hiking`
  up are blocked; stiles, kissing gates, turnstiles and cattle grids are impassable unless tagged
  for horses. Asphalt costs 1.3×, setts and cobbles 1.6×. OpenStreetMap's horse tagging is patchier
  than its bike tagging, and the map tiles have no horse access, so the trail overlay shows only
  general access in this mode.

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
  known values (see `MTB_SCALE_VALUES`). Aliases are not accepted either: `horse=permissive` is
  folded into `yes`, so the profile must say `horse=yes`.

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
against BRouter 1.7.10 (the version the image now uses).

## Credits

Map data © OpenStreetMap contributors (ODbL). Routing: [BRouter](https://github.com/abrensch/brouter)
(MIT). Base map: [OpenFreeMap](https://openfreemap.org); raster maps: CyclOSM, OpenTopoMap, Esri World
Imagery; MTB overlay: Waymarked Trails; trail overlay: drawn from the OpenFreeMap vector tiles.
Respect each tile provider's usage policy; for heavy use, host your own tiles.

## License

MIT, see [LICENSE](LICENSE). Map data and tiles have their own licences (see Credits).

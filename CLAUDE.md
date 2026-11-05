# Trailplan

Self-hosted MTB route planner: Vue/MapLibre frontend (`frontend/`), Spring Boot backend with SQLite
(`backend/`), BRouter routing server (`brouter/`). See `README.md` for architecture and setup.

## Working rules

- Commit after every change, with a descriptive message. Do not push unless asked.
- Never delete or overwrite `data/`. It holds the user's downloaded map tiles and the route database
  (`data/db/trailplan.db`), which are not in git. Test against a copy, or stop and ask first.

## Checks

- Backend: Java 25 and Maven 3.10 from `.sdkmanrc`. Run `sdk env` in the project root (not in
  `backend/`), then `mvn verify` in `backend/`. It runs Error Prone, the tests and the Spotless
  format check. Fix formatting with `mvn spotless:apply`.
- Frontend: run `npm run typecheck`, `npm run lint` and `npm run build` in `frontend/`. Format
  with `npm run format`.
- Docker: `docker compose build`. The backend and BRouter containers must both run as uid 1000
  because they share `data/customprofiles`.

## Things to know

- Spring Boot 4 uses Jackson 3 (`tools.jackson.*`). The annotations stay in
  `com.fasterxml.jackson.annotation`. Jackson 3 rejects a missing value for a primitive field, so
  optional request fields must be boxed and get their default in the record constructor (see
  `RoutingPreferences`).
- `frontend/src/types.ts` mirrors the Java records. Keep the two in sync.
- BRouter rejects profiles that use tag values missing from its `lookups.dat`. Check
  `MTB_SCALE_VALUES` in `BRouterProfileGenerator` when upgrading BRouter.

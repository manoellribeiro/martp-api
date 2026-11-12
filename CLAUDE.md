# CLAUDE.md

Project context for Claude Code. Keep this file short and current. When we make a new decision, add it under "Decisions log".

Architecture, layers, Koin, errors, outside-API services and test conventions are general and live in the **`ktor-layered-api` skill**, imported here so every session follows it:

@.claude/skills/ktor-layered-api/SKILL.md

The rest of this file only holds what is specific to Martp API. When the two disagree, this file wins.

## Project overview

Martp API is the backend of the Martp project (Android app: https://github.com/manoellribeiro/map-arts, plus a web PWA copy of it). Its goals are:

1. **Store every map art** created in the apps, with the user who created it, in a remote database.
2. **Serve the approved arts** to a digital exhibition.
3. **Reverse geocoding** for both apps, through Nominatim: the web app's stand-in for Android's `Geocoder`, and Android's fallback when its `Geocoder` isn't available.
4. **Color palettes**: the single source of the palettes used by the colorful map art style, for both apps.

Out of scope for now: user login, admin endpoints, proxying Mapbox. Don't add them unless asked.

The Android source is at `/Users/manoellribeiro/Documents/martp/map-arts`. Use it as a read-only reference (entities in `app/src/main/java/manoellribeiro/dev/martp/core/data/local/entities/`). Don't edit it, or the web project, from here.

## Stack

- **Ktor 3.6** (Netty server engine), **Kotlin 2.4**, JVM 21, Gradle Kotlin DSL with version catalogs (`ktorLibs` for Ktor, `libs` in `gradle/libs.versions.toml` for the rest).
- **PostgreSQL 17** (15+ is required for `UNIQUE NULLS NOT DISTINCT`).
- **Exposed 1.x over JDBC** + **HikariCP** + **Flyway**. The generator's R2DBC/H2 setup was replaced because Flyway and HikariCP need JDBC.
- **Koin** for dependency injection, **kotlinx.serialization** for JSON.
- **Ktor Client (CIO engine)** for outside APIs.
- **AWS SDK for Kotlin (S3)** for images: **Cloudflare R2** in production, **RustFS** in Docker Compose locally (MinIO is archived).
- **Ktor Authentication (JWT)** to check Firebase App Check tokens.
- **JTS** + **Natural Earth** 1:50m country borders (bundled) for the continent of each art.
- **Logback** for logs, behind our `ErrorLogger`.
- Tests: **JUnit 6 + MockK + kotlinx-coroutines-test**.
- **Docker**: `docker-compose.yml` runs PostgreSQL and RustFS for local development. The API's image is built by the `Dockerfile`: a JDK stage runs `buildFatJar` (`build/libs/martp-api.jar`, tests skipped: the deploy workflow runs `./gradlew build` first), then an Eclipse Temurin 21 JRE stage runs it as a non-root user, with `-XX:MaxRAMPercentage=75` and `-XX:+ExitOnOutOfMemoryError`.

Root package: `manoellribeiro.dev`.

## Project structure

```
src/main/kotlin/manoellribeiro/dev/
├── Application.kt              main + the single Ktor module (plugin order matters)
├── core/
│   ├── config/AppConfig.kt     typed application.yaml, provided by Koin
│   ├── di/Modules.kt           one Koin module per layer
│   ├── plugins/                Koin, Database, Serialization, Monitoring, Cors, StatusPages, Security, Routing
│   ├── data/
│   │   ├── database/           MartpDatabase (Hikari + Flyway + Exposed), tables/, entities/, daos/
│   │   └── repositories/       MartpRepository (the only repository)
│   ├── services/               ImageStorageService (S3/R2), GeocodingService (Nominatim), RegionService (Natural Earth)
│   ├── logging/                ErrorLogger + LogbackErrorLogger
│   ├── models/                 domain models, ErrorResponse, failures/
│   └── utils/
└── routes/
    ├── mapArts/                MapArtsRoutes.kt + MapArtsController.kt + models/ (DTOs)
    ├── geocoding/              GeocodingRoutes.kt + GeocodingController.kt + models/
    └── colorPalettes/          ColorPalettesRoutes.kt + ColorPalettesController.kt + models/
src/main/resources/
├── application.yaml            every value overridable by env var
└── db/migration/               Flyway SQL migrations (V1__..., V2__...)
```

- **One repository**: `MartpRepository` reads the DAOs, `ImageStorageService` and `GeocodingService`. Don't create one repository per theme.
- Routes are grouped by theme in `routes/<theme>/`, each with its routes (presentation) and its controller.

## Protecting the API (Firebase App Check)

The API has **no public endpoint**: only the Martp apps may call it. There's no login, so every request proves it comes from a genuine app instead:

- **Android** (distributed on Google Play): App Check with the **Play Integrity** provider.
- **Web app**: App Check with the **reCAPTCHA** provider (score-based, invisible). No badge: the web app hides it and shows "This site is protected by reCAPTCHA and the Google Privacy Policy and Terms of Service apply." only on screens that call the API.
- **Exhibition**: also needs to prove itself. How depends on what it is (not decided): a public web page → registered as its own Firebase web app with App Check + reCAPTCHA; a device or server we control → can use a secret instead.

How the API checks it (`core/plugins/Security.kt`):
- The token comes in the **`X-Firebase-AppCheck`** header. It's a JWT signed by Google, checked **locally** with Ktor's JWT authentication: RS256 signature with the keys from `https://firebaseappcheck.googleapis.com/v1/jwks` (cached up to 6 hours), issuer `https://firebaseappcheck.googleapis.com/<projectNumber>`, audience `projects/<projectNumber>`, expiry, and `sub` must be one of `FIREBASE_ALLOWED_APP_IDS`. No call to Google per request.
- Every route is registered inside `authenticate(APP_CHECK_AUTH)` in `core/plugins/Routing.kt`. A missing or invalid token answers `401` with `error = "InvalidAppCheckToken"`. There's no puzzle for the user: the apps show their normal error screen.
- The SDKs cache tokens (about an hour), so Play Integrity (10,000 a day free) and reCAPTCHA (10,000 a month free) are called about once per hour per active user.
- On top of App Check: **per-IP rate limits** (`core/plugins/RateLimiting.kt`, values in `core/utils/RateLimits.kt`), plus the moderation and the image limits below. Even a client that gets through can only fill the moderation queue.
  - Every route together: 120 requests per minute. `POST /map-arts`: 30 per hour. `GET /geocoding/reverse`: 30 per minute. Both stricter limits count on top of the global one.
  - Over a limit: `429` with `error = "TooManyRequests"` and a `Retry-After` header, logged with `logClientError`.
  - Only calls that passed App Check are counted: a rejected token answers `401` first (a local JWT check, so it costs almost nothing).
  - The client IP is the **last** `X-Forwarded-For` value (`XForwardedHeaders` with `useLastProxy()`), the one the host's proxy adds; earlier values can be faked. So exactly one proxy may sit in front of the API: `api.martp.app` stays "DNS only" in Cloudflare (no orange cloud).
  - Counters are in memory, per instance: one more reason to run a single instance.
- Images are public by URL in the R2 bucket (no signed URLs). Protecting the API stops listing arts, not opening an image link someone already has. That's fine: only approved arts are ever returned.
- Local development: the apps use App Check **debug tokens** (debug provider, registered in the Firebase console). They are real signed tokens, so the API checks them the same way.

## Data model

Schema: `src/main/resources/db/migration/V1__create_initial_schema.sql`. Until the first deploy, change `V1` directly. After that, never edit a migration that already ran: add `V2__...`.

**Every table** has `created_at`, `updated_at`, `is_deleted` (default `false`) and `deleted_at`:
- **Soft delete only.** Rows are never removed. Deleting = `UPDATE ... SET is_deleted = true`. No `DELETE` statements in the code.
- The `set_audit_columns()` trigger sets `updated_at` on every update and `deleted_at` when `is_deleted` changes, so manual edits in the database stay consistent too. The code never sets these columns. A `CHECK` keeps `deleted_at` and `is_deleted` in sync.
- Every read filters `is_deleted = false` unless it's explicitly about deleted rows. New tables get the same four columns, the trigger and the check.

**users**: `id` (UUID, generated by the database), `email?`, `username?`.
- The apps' local `UserInfoEntity.id` is **not** used by the API. Users are identified by the pair (email, username).

**map_arts**: `id` (UUID **generated by the app**, same as `MapArtEntity.id`), `title`, `latitude`, `longitude`, `date_in_millis`, `image_key`, `sketch_art_type?`, `color_palette?`, the six `map_layer_*_color?` columns, `artist_id?` → `users.id`, `is_approved_for_the_exhibition` (default `false`).
- `sketch_art_type`: the Android `SketchArtType` name (`DEFAULT`, `POINTILLISM`, `COLORFUL_MAP`, `WATER_FLOW`, `PARTICLE_SYSTEM`; the web only creates the first three). Nullable like in the entity, for arts made before the apps recorded it.
- `color_palette`: the **name** of the palette used, copied as text (not a foreign key), so the art keeps it even if the palette is deleted later.
- `map_layer_*_color`: `MapArtEntity.colorsLayers` (`MapLayersColors`) flattened into six columns, the same way Room's `@Embedded(prefix = "map_layer_")` does: roads, building stroke, building, roads path, water, land/others. Stored and sent as `#RRGGBB` (Android converts its `Int` colors). Each one is nullable, since the sketch only sets the layers it finds in the map image.
- `created_at` is when the API received the art. `date_in_millis` is when it was created on the device.
- `MapArtEntity.imagePathLocation` is a path on the phone and never reaches the API. The API stores the image in the bucket and keeps only its **key** (`image_key`). The public URL is built when reading: `storage.publicBaseUrl + "/" + image_key`, so the domain can change without touching data.
- Coordinates are stored as `DOUBLE PRECISION` (the Android entity uses `Float`, the web sends more decimals).
- `continent` (not null) and `country_code?` (ISO 3166-1 alpha-2): worked out by the API from the coordinates when the art is saved. See "Continents".

**Deleting:**
- Deleting a **user** never deletes its arts. They are shown as anonymous (a deleted user is returned as `user: null`).
- Deleting an **art** never deletes its user, even if the user has no other art. Users without arts are kept.

**color_palettes**: `name`, `colors` (`TEXT[]`), `position`, `is_available` (default `true`), plus the audit columns. Seeded with the 15 palettes of the Android `ColorPaletteProvider`, in the same order.
- `name` is unique across **every** row, deleted ones included.
- A deferred constraint trigger refuses any commit that leaves no palette available and not deleted.

## Color palettes

The API is the **single source** of the color palettes. The Android `ColorPaletteProvider` and the web `colorPalettes.ts` will stop being the source, and the apps read `GET /color-palettes` instead.

- **Endpoint:** `GET /color-palettes`, protected by App Check like every route. It answers `200` with the palettes that are available and not deleted, ordered by `position`:
  ```json
  { "palettes": [ { "name": "Martp", "colors": ["#100C2D", "#120D31", "#4E3C41", "#896B51", "#FFC971", "#FFCE7E"] } ] }
  ```
  The **first palette is the default**, which replaces `byNameOrDefault`'s `all.first()`. The list is never empty: the database refuses to leave no palette available.
- **Colors:** exactly **6** per palette, as uppercase `#RRGGBB`. The colorful map sketch reads indexes 0 to 5, so fewer colors would crash it. The database enforces both rules with `CHECK`s. The web app uses the strings directly; Android converts them with `Color.parseColor`.
- **The name is the identifier.** The apps save the selected palette by name (DataStore / `localStorage`), so a published name is **never renamed**: renaming would silently reset everyone's choice to the default. To replace a palette, soft delete it and add a new one with a **new name**: a name is never reused, not even a deleted one.
- **Changing palettes** is done by hand in the database, like moderation: `INSERT` a new row, change `position`, set `is_available`, or set `is_deleted = true`. The checks, the unique indexes (name across every row, position among palettes that aren't deleted) and the "at least one available" trigger keep the data valid. No admin endpoint and no migration needed for later changes.
- **Campaigns** (e.g. only the Pride palette in June) use the `is_available` flag, switched by hand. Each switch is one atomic statement:
  ```sql
  -- start: only Pride is offered (Pride inserted beforehand with is_available = false)
  UPDATE color_palettes SET is_available = (name = 'Pride');
  -- end: everything except Pride is offered again
  UPDATE color_palettes SET is_available = (name <> 'Pride');
  ```
  Apps that cached the old list keep sending its palettes, so **saving an art accepts every palette that ever existed** (available or not, deleted or not). The apps need no new rule: "saved name not in the list → first palette" moves users onto the campaign palette and back.
- **Contract with the apps** (implemented in the apps, recorded here so the API stays compatible):
  - Keep the current hardcoded list as an **offline fallback**, so creating an art never depends on this call.
  - Cache the last successful response and refresh it when the app starts.
  - If the saved palette name isn't in the list anymore, use the first palette.
- Flow: `ColorPalettesRoutes` → `ColorPalettesController` → `MartpRepository.getColorPalettes()` → `ColorPalettesDao.getAvailable()`, which only returns rows where `is_available = true` and `is_deleted = false`, ordered by `position`. The upload check uses `MartpRepository.colorPaletteExists()` → `ColorPalettesDao.existsByName()`, which ignores both flags.

## User matching (when an art is saved)

A user is reused only when **both** email and username match, with `null` counting as a value:

| Sent email | Sent username | Existing user with the same pair? | Result |
|---|---|---|---|
| any | any | no | create a new user |
| any | any | yes | reuse it |
| `null` | `null` | — | **no user**: `artist_id = null`, the art is anonymous |

Example: an art with (`a@x.com`, `null`) creates user 1. A later art with (`a@x.com`, `mano`) creates user 2, even though the email is the same.

How it is enforced:
- The database has a unique index `(email, username) NULLS NOT DISTINCT WHERE NOT is_deleted` and a `CHECK` that at least one of them is present. Only users that are not deleted are matched, so a deleted user's pair creates a new user.
- Saving an art runs in **one transaction**: insert the user with `ON CONFLICT DO NOTHING` (Exposed `insertIgnore`), then select its id with `isNotDistinctFrom` on both columns and `is_deleted = false` (never `eq`, which doesn't match nulls the same way), then insert the art. Two uploads arriving at the same time can't create duplicate users.
- That transaction runs at `READ COMMITTED`: at the pool's `REPEATABLE READ`, `ON CONFLICT DO NOTHING` fails with a serialization error when a concurrent upload just created the same user.
- When reading, an art with `artist_id = null`, or whose user is deleted, returns `user: null`. The exhibition shows it as anonymous.

## Validation

The API is public, so it checks every rule and answers `400` (`ValidationFailure`) when one fails. A rule that only bothers the user without protecting anything is not added: an art should almost never be rejected.

The only input the API fixes is outer whitespace: **every text field** (`id`, `title`, `sketchArtType`, `colorPalette`, the `colorsLayers` colors, `email`, `username`) is trimmed first, and the inner spaces are kept (`"  Manoel Ribeiro "` → `"Manoel Ribeiro"`). An optional field that is blank after trimming is saved as `null`, the same as not sent. Nothing else is ever fixed (no lowercasing, no color conversion): every rule below is checked on the trimmed value.

- `email` and `username` are optional. **No email format check**: any text is accepted.
- Art `id`: a valid UUID. An id that already exists → `409 Conflict`.
- `title`: required, but may be empty (the title comes from the address, and some coordinates don't have one). Trimmed, so a title of only spaces is saved as `""`. The exhibition returns an empty title as `"Unknown Location"`; the database keeps it empty.
- `latitude` between -90 and 90, `longitude` between -180 and 180. `dateInMillis` > 0.
- `sketchArtType`, when present: one of the five `SketchArtType` names (exact case).
- `colorPalette`, when present: the exact name (case included) of any palette that ever existed, even one that isn't available or is deleted. The art was made with a real palette, maybe offline from a cached list, so it's never rejected for that.
- `colorsLayers` colors, when present: uppercase `#RRGGBB`.
- Image: **PNG only**, checked by its signature, not by the file name or content type. **At most 1400 × 1400 px**, read from the PNG header (no full decoding). Where the 1400 comes from: Mapbox Static Images allow at most 1280 px per side, and both apps add a 30 px frame + 30 px padding on each side (`MAX_CANVAS_SIZE` in the web app, `MartpSketch.frameThickness`/`framePadding` on Android).
- Image file size: at most **8 MB**. A 1400 × 1400 RGBA image is 7.84 MB uncompressed, so no real art reaches it. It only blocks abuse.
- Maximum text lengths: `email` 254, `username` 100, `title` 300.
- Keep every limit as a named constant in one place.

## Moderation

- New arts are saved with `is_approved_for_the_exhibition = false`.
- Approval is done **by hand in the database** for now (no admin endpoint):
  ```sql
  UPDATE map_arts SET is_approved_for_the_exhibition = true WHERE id = '<art id>';
  ```
- Endpoints that feed the exhibition must only return arts that are approved and not deleted. Usernames are only ever public through approved arts.
- The exhibition receives only the artist's `username`, never the email. The email is internal: it's for thank-you messages and news about the project. The query for the exhibition doesn't even select it.
- Removing an art from the exhibition: set `is_approved_for_the_exhibition = false`, or `is_deleted = true` to delete it.

## Image storage

- Production: a public **Cloudflare R2** bucket (free tier: 10 GB, 1M writes and 10M reads a month, free egress). Local: **RustFS** from `docker-compose.yml`, whose `storage-init` service creates the `map-arts` bucket with public read.
- One S3 client config for both, from `storage.*` in `application.yaml`. R2: endpoint `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`, region `auto`, `forcePathStyle = false`. RustFS: `forcePathStyle = true`.
- Object key: `<artId>.png`.

## Saving an art

Endpoint: `POST /map-arts`, multipart with two parts, answering `201` with no body (the apps already have the id, and the art isn't public until approved):
- `art`: JSON with `id`, `title`, `latitude`, `longitude`, `dateInMillis`, and the optional `sketchArtType`, `colorPalette`, `colorsLayers` (`roadsColor`, `buildingStrokeColor`, `buildingColor`, `roadsPathColor`, `waterColor`, `landOthersColor`), `email`, `username`.
- `image`: the PNG bytes.

The route reads the parts in `MapArtsRoutes.kt`:
- A `Content-Length` over **8 MB + 64 KB** (`MAX_MAP_ART_UPLOAD_BYTES`) answers `400` before anything is read. Ktor's own part limit fails the call with a `500`, so it's only the backstop for a chunked body without `Content-Length`.
- `art` may come as a text field or as a file part (a `Blob` on the web). Invalid JSON, a missing field or a wrong type answers `400 BadRequest`. A missing `art` or `image` part answers `400 ValidationFailure`.
- A body that isn't multipart answers `415` with `error = "UnsupportedMediaType"`.

`MapArtsController` runs every validation before touching the repository. The `colorPalette` check comes last because it's the only one that reads the database. The name must match exactly (case included), and it may be a palette that isn't available or is deleted.

1. If the id already exists (even on a deleted art), answer `409`. This comes first because uploading would overwrite that art's image.
2. Upload the image.
3. Insert the user and the art in one transaction.
4. If the insert fails, delete the uploaded image. If it fails because another request just saved the same id, keep the image: it belongs to that art.

## Exhibition

`GET /map-arts/exhibition` answers `200` with every approved art that isn't deleted, newest first (`date_in_millis`):
```json
{ "mapArts": [ { "id": "...", "title": "Unknown Location", "latitude": 48.8584, "longitude": 2.2945, "dateInMillis": 1790000000000,
  "imageUrl": "https://.../<id>.png", "sketchArtType": "COLORFUL_MAP", "colorPalette": "Martp",
  "colorsLayers": { "roadsColor": "#AABBCC", "buildingStrokeColor": null, "...": "..." }, "user": { "username": "mano" } } ] }
```
- An empty title is returned as `"Unknown Location"`.
- `user` is `null` when the art is anonymous, or its user is deleted or has no username. Never the email.
- Missing optional fields are sent as `null`, not left out.
- Each art also has `continent` (e.g. `"EUROPE"`) and `countryCode` (e.g. `"FR"`, `null` in the ocean).
- `?continent=EUROPE` returns only that continent's arts (trimmed, exact name, case included). Missing or blank = every continent. An unknown value answers `400 ValidationFailure`.

`GET /map-arts/exhibition/continents` answers `200` with the continents that have arts in the exhibition, and how many each has:
```json
{ "continents": [ { "continent": "SOUTH_AMERICA", "total": 32 }, { "continent": "EUROPE", "total": 10 } ] }
```
- Counts only arts that are approved and not deleted (the same ones `/exhibition` returns). A continent without arts is left out; no arts at all = `[]`.
- `OCEAN` and `UNKNOWN` are returned like any other continent; the exhibition decides whether to show them.
- Biggest total first, ties by name.

`GET /map-arts/exhibition/globe` answers `200` with a random selection of approved arts for the globe, in the same format as `/exhibition` (`{ "mapArts": [...] }`). The card's location line comes from the title ("Street, City, Country").
- At most this many per continent (`GlobeLimits.MAX_ARTS_PER_CONTINENT`): Asia 8, Africa 7, North America 6, South America 5, Europe 5, Oceania 4, Central America 2, Antarctica 2, Ocean 2, Unknown 0 (left off until fixed by hand). At most 41 pins.
- No two pins closer than **300 km** (`GlobeLimits.MIN_DISTANCE_KM`), across continents too. Still to be tested on the real globe.
- A new random set on every request: the arts are shuffled, then each one is kept if its continent isn't full and it isn't too close to one already kept. It doesn't search for the biggest possible set, so a continent whose arts are all in one city may get fewer pins than its limit.
- Done in memory by `GlobeArtsPicker` over the same query as `/exhibition`. Move it to SQL if approved arts reach the tens of thousands.

## Continents

Each art gets a continent so the exhibition can group and filter by it.

- Values: `AFRICA`, `ANTARCTICA`, `ASIA`, `CENTRAL_AMERICA`, `EUROPE`, `NORTH_AMERICA`, `OCEANIA`, `SOUTH_AMERICA`, `OCEAN`, `UNKNOWN`.
- Worked out **once, when the art is saved**, by `RegionService` from the coordinates, in memory (no outside call): the country whose borders contain the point; if none, the nearest country within **50 km** (simplified coasts); if none, `OCEAN`.
- Borders: **Natural Earth 1:50m admin 0 countries**, public domain, v5.1.2, in `src/main/resources/naturalEarth/countries.geojson`, trimmed to `CONTINENT`, `SUBREGION`, `REGION_UN`, `ISO_A2_EH`. Loaded once at startup (Koin `createdAtStart`). To update it, download `geojson/ne_50m_admin_0_countries.geojson` from a release tag of `nvkelso/natural-earth-vector` and run `jq -c '{type, features: [.features[] | {type, properties: (.properties | {CONTINENT, SUBREGION, REGION_UN, ISO_A2_EH}), geometry}]}'` on it.
- Rules on top of Natural Earth's `CONTINENT`:
  - `CENTRAL_AMERICA` = the Caribbean + the UN's Central America **without Mexico** (Mexico is `NORTH_AMERICA`).
  - Remote islands Natural Earth calls "Seven seas (open ocean)" use their UN region (Mauritius → `AFRICA`, Maldives → `ASIA`, South Georgia → `SOUTH_AMERICA`).
  - The **whole country decides**: Russia is `EUROPE`, Turkey is `ASIA`, and so on. Not 100% accurate on purpose; wrong ones are fixed by hand.
- **Never blocks saving an art.** Any failure (borders that can't be read at startup, a broken country, a lookup error) is logged and gives `UNKNOWN`; the API still starts and the art is saved.
- Fixing by hand: `SELECT id, latitude, longitude FROM map_arts WHERE continent = 'UNKNOWN';` then `UPDATE map_arts SET continent = 'EUROPE', country_code = 'FR' WHERE id = '<art id>';`. Approved ones also show with `?continent=UNKNOWN`.

## Geocoding (Nominatim)

- Reverse geocoding only, for both apps (Android uses it when its `Geocoder` isn't available), through `GeocodingService` → `MartpRepository` → geocoding route.
- The result has three fields, `street`, `city` and `country`, the same ones Android uses to build the default title. `city` falls back to Nominatim's `town`, then `village`.
- Endpoint: `GET /geocoding/reverse?latitude=..&longitude=..&language=pt-BR`, answering `200` with `{ "street": ..., "city": ..., "country": ... }`. A missing, non-numeric or out-of-range coordinate answers `400`.
- The apps send their language and the API passes it to Nominatim as `accept-language`. Without it, Nominatim uses each place's local names. An invalid language (not just letters, digits and hyphens, or over 35 characters) never blocks the user: the lookup uses English and the value goes to `logClientError`.
- One or two fields missing: `200` with those fields `null`. All three missing: `404` with `error = "AddressNotFoundFailure"`, which the apps detect by that name (an unknown path also answers 404).
- Nominatim down or timing out: `502` with `NominatimApiErrorFailure`, logged.
- Nominatim usage policy: **at most 1 request per second** (enforced inside `GeocodingService` with a `Mutex`, for the whole API), a **User-Agent that identifies the app** (`geocoding.userAgent`), and both apps must show the attribution "© OpenStreetMap contributors" wherever they use a result from this endpoint. Cache repeated lookups if traffic grows.
- A point without an address answers `200` with `{"error":"Unable to geocode"}` on Nominatim's side, so a successful status doesn't mean there's an address.
- Map Nominatim's response to our own model in the service; never return Nominatim's JSON as is.

## Configuration and secrets

- Everything is in `src/main/resources/application.yaml` as `"$ENV_VAR:default"`. Defaults point to the Docker Compose services, so local development needs no variables.
- `FIREBASE_PROJECT_NUMBER` and `FIREBASE_ALLOWED_APP_IDS` default to the Martp Firebase project (`814915753343`) and the app IDs of the Android app and the web app (PWA). They aren't secrets: both are public in the apps (`google-services.json` on Android, the Firebase config in the web app's `.env`). When a new app (e.g. the exhibition) gets App Check, add its app ID to the default list.
- Production variables are listed in `.env.example`. Never commit real values (`.env` is git-ignored).
- New config: add it to `application.yaml`, `AppConfig.kt` and `.env.example`, then provide it through Koin.

## Errors and logging

- Error body for every error: `{ "error": "<FailureClassName>", "message": "<safe message>" }` (`ErrorResponse`), produced only by `core/plugins/StatusPages.kt`.
- Every 5xx is sent to `ErrorLogger.logNonFatal` exactly once: where the exception is converted into a `Failure` (repository or service), or in `StatusPages` for unexpected exceptions.
- Every 4xx is sent to `ErrorLogger.logClientError`, without a stack trace, to observe expected errors: `Failure`s, malformed requests, unsupported content types and unknown paths in `StatusPages`, rejected App Check tokens in `Security.kt`.
- Logs are for solving production problems, so they may carry user data: coordinates, query strings, and the value of any field that failed validation (`ValidationFailure.rejectedValue`, cut to 300 characters). Emails and usernames that pass validation are not logged. Images are never logged.
- `messageToClient` never contains what the user sent.
- `CallLogging` logs status, method, path (no query string) and the client IP the rate limits use, never bodies.
- Monitoring is **Google Cloud Error Reporting**, fed by the logs: `LogbackErrorLogger` stays the `ErrorLogger` in production. (Firebase Crashlytics doesn't support the web or the JVM.) Switching tools = a new `ErrorLogger` implementation + one line in `core/di/Modules.kt`.
- Log formats: `logback.xml` (plain text) locally; `logback-cloud.xml` in production, selected with `JAVA_TOOL_OPTIONS=-Dlogback.configurationFile=logback-cloud.xml`. It writes one JSON object per line (`logstash-logback-encoder` 8.x, the last line on Jackson 2), with `severity`, `message` and `stack_trace`, so a stack trace stays one entry and Error Reporting groups errors. A crash during startup is caught in `main()` (`Application.kt`, which is why `mainClass` is `ApplicationKt`, not `EngineMain`) and logged through SLF4J as one entry, then the process exits with code 1.

## Tests

- Unit tests for **controllers** (mocked `MartpRepository`) and **`MartpRepository`** (mocked DAOs, services and `ErrorLogger`). Conventions in the `ktor-layered-api` skill.
- **No DAO tests and no database in tests** (no Testcontainers, no H2).
- `RegionServiceImpl` and the Natural Earth loader are also tested (fake shapes + known cities on the bundled file): it's our own logic with no outside system.

## Local development

```bash
docker compose up -d     # PostgreSQL on :5432, RustFS S3 on :9000 (console :9001)
./gradlew run            # API on :8080, runs Flyway migrations on startup
./gradlew test
```

To check the production image locally (the API in the container reaches Compose through `host.docker.internal`):
```bash
docker build -t martp-api:local .
docker run --rm --memory=512m -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/martp \
  -e STORAGE_ENDPOINT=http://host.docker.internal:9000 martp-api:local
```
Built on an Apple Silicon Mac the image is `arm64`; Cloud Run needs `linux/amd64` (build there with Cloud Build, or `--platform linux/amd64`).

## Production

Everything is on free tiers. Expected cost: $0 a month, plus the yearly `martp.app` domain on Cloudflare.

- **API: Google Cloud Run**, service `martp-api`, `europe-west1`, in the Firebase project's Google Cloud project (`martp-2e001`, number `814915753343`). It runs as the project's default service account (`814915753343-compute@developer.gserviceaccount.com`), which can read the three secrets. Settings: max **1** instance (rate limit counters, the Nominatim limit and the globe are per instance), min **0** (scales to zero; cold starts accepted), 512 MiB, 1 vCPU, request-based billing, startup CPU boost, request timeout 300 s.
- **Deploy: GitHub Actions**, the same flow as the Android app. A pull request to `main` runs `./gradlew build` (`.github/workflows/pull_request.yml`). **Pushing a tag** (`git tag 1.0.0 && git push origin 1.0.0`) runs the tests, then builds the image on the runner (`linux/amd64`), pushes it to **Artifact Registry** (`europe-west1-docker.pkg.dev/martp-2e001/martp-api/martp-api:<tag>`) and deploys it (`.github/workflows/release.yml`). All Cloud Run settings are in that workflow.
  - GitHub logs in with **Workload Identity Federation** (pool `github`, provider `martp-api`): no key is stored. Google only accepts the repo `manoellribeiro/martp-api` (checked by its numeric id) running on a tag, as the `github-deployer` service account, which may only push to the `martp-api` image repository and manage Cloud Run (`run.admin`: `--allow-unauthenticated` needs it).
  - `DATABASE_URL`, `DATABASE_USER` and `STORAGE_ENDPOINT` are GitHub **repository variables** (Settings → Secrets and variables → Actions), not in the code: the repo is public. The other plain variables are in the workflow.
  - The image repository keeps the **2 newest** versions (one to roll back to), so it stays in the 0.5 GB free storage. The image is built with `--provenance=false`: Docker's default provenance record adds two extra versions per push (an index and the record), and the rule would count them and could delete the image a tag points to.
- **Database: Neon** free plan, Frankfurt (`aws-eu-central-1`), PostgreSQL 17. Use the **direct** endpoint (not `-pooler`) with `?sslmode=require`. Neon sleeps after 5 minutes without queries and the free plan has 100 compute hours a month, so the pool keeps no idle connection (`minimumIdle = 0`, `idleTimeout` 60 s in `MartpDatabase`). The first query after a pause wakes it (a few hundred ms).
- **Cold start, measured** (first deploy): after 18 minutes idle, the first call took **8.3 s** (new instance + JVM + Neon waking + Flyway check); the next one 0.1 s. Startup inside the app: about 6 s.
- **Images: Cloudflare R2**, bucket `map-arts`, location hint Western Europe, served at **`images.martp.app`** (R2 custom domain; `STORAGE_PUBLIC_BASE_URL`). The API's token has Object Read & Write on that bucket only. CORS rule: `GET` from `https://martp.app`, so the exhibition can draw images on a canvas/WebGL.
- **Domain: `martp.app`** on Cloudflare. `martp.app` = the PWA, `api.martp.app` = Cloud Run domain mapping (CNAME to `ghs.googlehosted.com`, **DNS only**, never proxied: see the rate limits), `images.martp.app` = R2.
- **Variables**: listed in `.env.example`. Secrets (`DATABASE_PASSWORD`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`) are in **Secret Manager**, mounted as env vars; everything else is a plain Cloud Run env var. `CORS_ALLOWED_ORIGINS` = `https://martp.app,https://manoellribeiro.github.io` (remove GitHub once the PWA has moved).
- **Budget**: "Firebase Project martp-2e001", **R$1** a month (the billing account is in BRL), emails at 50%, 90% and 100%. Created by the Firebase upgrade to Blaze. It only warns, it never stops anything.
- **Backups**: Neon's free plan can only restore the last **6 hours**. Run a dump weekly by hand, and always before a manual bulk change (moderation, palettes). Keep the files outside the repo:
  ```bash
  # NEON_URL = postgresql://<user>:<password>@<endpoint>.eu-central-1.aws.neon.tech/<database>?sslmode=require
  docker run --rm postgres:17 pg_dump "$NEON_URL" -Fc > martp-$(date +%F).dump
  # restore into an empty database: pg_restore --no-owner -d "<target url>" martp-<date>.dump
  ```
  Images in R2 aren't in the dump; they're never deleted by the API.
- **Uploads from the apps** (contract, implemented in the apps): the first request after a pause hits a cold start (API + Neon), so the apps keep the art locally until `POST /map-arts` answers `201`, retry on failure or timeout with a long timeout, and treat `409` (id already exists) as already saved. That way an art is never lost.

## Open decisions

- What the exhibition is (public web page or a device/server we control), and so how it proves itself.

## Decisions log

- Exposed over **JDBC** (not the generator's R2DBC), so HikariCP and Flyway can be used. H2 removed.
- **RustFS** replaces MinIO for local S3 (MinIO was archived in 2026).
- Every table has `created_at`, `updated_at`, `is_deleted`, `deleted_at`, kept by a trigger. Soft delete only.
- Deleting a user makes its arts anonymous. Deleting an art never deletes its user.
- Image limits: PNG, at most 1400 × 1400 px and 8 MB.
- Color palettes come from the API (`color_palettes` table, `GET /color-palettes`). Names are never renamed; the first palette is the default; the apps keep their current list as an offline fallback.
- No public endpoint: Firebase App Check on every route (Play Integrity on Android, reCAPTCHA on the web, with the text line instead of the badge). No signed image URLs.
- Reverse geocoding serves both apps. No address at all is a specific failure (`AddressNotFoundFailure`, 404), not an empty `200`.
- A 5xx `Failure` is logged where it's created, and every 4xx at warn level in `StatusPages`/`Security.kt`, so each error is logged once and 5xx errors keep their original stack trace.
- The exhibition list returns every approved art at once, newest first. Paging and filters come when we understand the exhibition better.
- An interface and its implementation never share a file (`MapArtsDao.kt` and `MapArtsDaoImpl.kt`). The rule is in the `ktor-layered-api` skill.
- No `description` in the API: it's deprecated on Android and unused. An app that still sends it is ignored.
- An empty title is accepted, and the exhibition shows it as "Unknown Location".
- Maximum text lengths: email 254, username 100, title 300.
- Logs may carry user data to solve production problems (coordinates, query strings, rejected values). Treat access to them and how long they're kept with the same care as the database.
- An invalid geocoding language falls back to English and is logged, it never fails the request.
- Saving an art is `POST /map-arts`, multipart with an `art` JSON part and an `image` PNG part (raw bytes, no Base64). It answers `201` with no body.
- The exhibition is `GET /map-arts/exhibition`. It returns coordinates: the title already shows the street, and every art is approved by hand. The artist comes as `user: { username }` or `null`.
- An upload's declared size is checked in the route (8 MB + 64 KB), because Ktor's multipart part limit answers 500. A wrong Content-Type answers 415.
- Campaign palettes use a manual `is_available` flag (no scheduled dates). `GET /color-palettes` returns only available palettes; at least one must always be available (deferred constraint trigger).
- Saving an art accepts any palette name that ever existed, available or not, deleted or not. Palette names are unique across every row and never reused.
- The App Check values (project number, allowed app IDs) have defaults in `application.yaml`: they're public in the apps, so `./gradlew run` needs no exports. No Firebase setup in the API itself: tokens are checked against Google's public keys.
- No email validation: any text is accepted, so users aren't bothered. Every text field of an art is trimmed instead of rejected for outer whitespace, and a blank optional field is saved as `null`.
- Each art has a continent, worked out from its coordinates when it's saved, with Natural Earth 1:50m borders bundled in the API (JTS, no outside call). The whole country decides; wrong ones are fixed by hand. Central America = UN Central America without Mexico + the Caribbean. Points away from any country are `OCEAN`.
- The continent never blocks saving an art: any failure gives `UNKNOWN`, logged, fixed by hand. No retries and no background job.
- The exhibition filters by continent in the API (`?continent=`), and returns `continent` and `countryCode` for each art.
- `GET /map-arts/exhibition/continents` returns `{ continents: [{ continent, total }] }` for approved arts, biggest total first, including `OCEAN` and `UNKNOWN`, without empty continents.
- The exhibition globe (`GET /map-arts/exhibition/globe`) picks a new random set on every request, with a limit per continent (roughly by size) and a 300 km minimum distance between pins. Greedy pick, not the biggest possible set: fine for a decorative globe.
- The globe's location line comes from the art's title, which already holds "Street, City, Country". The API doesn't store the city.
- Rate limits per IP: 120 requests per minute on every route, 30 uploads per hour, 30 geocoding lookups per minute. The IP is the last `X-Forwarded-For` value, so only one proxy in front of the API.
- The API's image is a two-stage `Dockerfile` (JDK build with `buildFatJar`, Temurin 21 JRE to run). Measured in a 512 MiB container: first answer 1.4 s after start, about 220 MiB used.
- Hosting: Cloud Run `europe-west1` (max 1 instance, scale to zero), Neon free plan in Frankfurt, Cloudflare R2 for images, `martp.app` on Cloudflare (`api.` DNS only, `images.` on R2). Everything on free tiers; cold starts accepted, so the apps retry uploads until `201` (`409` = already saved).
- Secrets in Secret Manager; a R$1 budget alert (the billing account is in BRL); Artifact Registry keeps the 2 newest images.
- Monitoring: Google Cloud Error Reporting over JSON logs (`logback-cloud.xml`), with `LogbackErrorLogger` as the implementation.
- Backups: a manual weekly `pg_dump` (and before any manual bulk change), since Neon's free plan only restores 6 hours.
- Deploys run on GitHub Actions when a tag is pushed (tests first), like the Android app. Workload Identity Federation instead of a JSON key. Cloud Run uses the default service account.
- The request log carries the client IP (the one the rate limits use), to check `X-Forwarded-For` and look into `429`s.
- The PWA's reCAPTCHA Enterprise key must live in the Firebase project (`martp-2e001`): a key from another Google Cloud project fails with `FAILED_PRECONDITION`.
- Startup failures are caught in `main()` and logged through Logback, so production keeps them as one entry.

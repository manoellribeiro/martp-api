# Martp API

Backend of [Martp](https://github.com/manoellribeiro/map-arts): stores the map arts created in the Android and web apps, serves the approved ones to the digital exhibition and provides reverse geocoding for the web app.

Built with Ktor, Kotlin, PostgreSQL (Exposed + Flyway), Koin and Cloudflare R2.

## Running locally

Requirements: JDK 21 and Docker.

```bash
docker compose up -d     # PostgreSQL + RustFS (local S3)
export FIREBASE_PROJECT_NUMBER=... FIREBASE_ALLOWED_APP_IDS=...
./gradlew run            # http://localhost:8080
./gradlew test
```

Production configuration is done with environment variables, listed in `.env.example`.

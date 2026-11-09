# Hisaab AI

Family expense, EMI and loan tracker for Indian households. Native Android (Kotlin + Jetpack Compose), offline-first, built for low-end phones.

Start with `CLAUDE.md`, then the docs in `DOCS/`: `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `IMPLEMENTATION_PLAN.md`, `TESTING.md`.

## Build
```bash
./gradlew assembleDebug        # debug APK
./gradlew test                 # unit tests
./gradlew ktlintCheck detekt   # lint
```
Windows: `gradlew.bat`. Put `sdk.dir`, `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `local.properties` (never committed).

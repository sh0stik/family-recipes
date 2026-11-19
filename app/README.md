# Recipe Book — Frontend

Kotlin Multiplatform (Compose Multiplatform) client for the Family Recipe Manager.
Talks to the Spring Boot GraphQL backend over Apollo.

## Platforms

- Android
- iOS
- Desktop (JVM)
- Web (JS / Wasm)

## Modules

- `shared/` — all common UI and logic (Compose UI, Apollo client, repositories, navigation).
  GraphQL operations live in `shared/src/commonMain/graphql/`.
- `androidApp/` — Android entry point.
- `webApp/` — web (JS/Wasm) entry point.
- `iosApp/` — iOS entry point (CocoaPods).

## Backend configuration

The GraphQL host and scheme are baked into a generated `data.AppConfig` at build time by the
`:shared generateAppConfig` task — nothing is hardcoded in source.

- **Debug** (default): `http` + `recipe.serverHost.debug` from `gradle.properties`
  (`localhost:8080`, the local Docker backend; use `10.0.2.2:8080` from the Android emulator).
- **Release**: `https` + a public host that must be supplied at build time. A release build with
  no host fails on purpose, so no placeholder ships.

```bash
# Debug build (uses the debug host from gradle.properties)
./gradlew :androidApp:assembleDebug

# Debug build for the Android emulator
./gradlew :androidApp:assembleDebug -Precipe.serverHost.debug=10.0.2.2:8080

# Release build (public host required)
./gradlew :androidApp:assembleRelease -Precipe.serverHost.release=<your-public-host>
# or: RECIPE_SERVER_HOST_RELEASE=<your-public-host> ./gradlew :androidApp:assembleRelease
```

## Build & run

```bash
# Web dev server
./gradlew :webApp:jsBrowserRun

# Android build
./gradlew :androidApp:assembleDebug

# Apollo codegen (runs automatically on build)
./gradlew :shared:generateApolloSources
```

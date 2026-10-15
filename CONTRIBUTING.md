# Contributing to Dynamic Island for Android

Thank you for your interest in contributing to **Dynamic Island for Android**! This is an open-source project dedicated to bringing a fluid, battery-efficient, and customizable island overlay experience to the Android ecosystem.

## Code of Conduct
Please be respectful, constructive, and collaborative in discussions, pull requests, and issue reports.

## Getting Started
1. **Fork and Clone** the repository.
2. Open the project in **Android Studio Koala / Ladybug (or newer)** with **JDK 17 or JDK 21**.
3. Let Gradle sync project dependencies.
4. Run the app on an Android device running **Android 8.0 (API 26) through Android 15 (API 35)**.

## Architecture Guidelines
- **Kotlin 2.0+**: Idiomatic Kotlin code, coroutines, and reactive Flows (`StateFlow`, `SharedFlow`).
- **Jetpack Compose**: Modern declarative UI with Compose BOM and Material 3 design tokens.
- **Service & WindowManager Integration**: The overlay runs inside `IslandOverlayService` with `TYPE_APPLICATION_OVERLAY` and an isolated `OverlayLifecycleOwner`.
- **Battery & Performance**:
  - Keep idle CPU usage at zero when no media is playing and no notification banner is active.
  - Dynamically shrink the `WindowManager.LayoutParams` bounding box when compact so that outside touches pass seamlessly to foreground applications.

## Submitting Pull Requests
1. Create a feature branch: `git checkout -b feature/my-new-feature`
2. Follow standard Android coding conventions.
3. Verify unit tests pass: `./gradlew test`
4. Submit a Pull Request with a clear description of changes, screenshots/GIFs of UI modifications, and tested device models.

# CarSystemUI

CarSystemUI is the Android Automotive OS (AAOS) implementation of System UI. It
provides the system-owned surfaces and behaviors used in a car, including system
bars, notifications, status indicators, volume controls, user switching,
keyguard, display and window management, and other automotive-specific
experiences.

This project is an AOSP platform component. It is built with
[Soong](https://source.android.com/docs/setup/build) as part of a complete
Android source tree and cannot be built as a standalone Gradle application.

## Main features

- Automotive system bars and navigation controls
- Notifications and minimized controls
- Status and privacy indicators
- Volume and HVAC integration
- User picker and user switching
- Keyguard and biometric integration
- Multi-display, display-area, and window-management support
- Runtime resource overlay (RRO) customization

## Repository structure

| Path | Contents |
| --- | --- |
| `src/` | Java and Kotlin production sources |
| `res/` | Main Android resources and overlayable configuration |
| `res-keyguard/` | Keyguard-specific resources |
| `pods/` | Feature modules composed into `CarSystemUI-core` |
| `lib/` | Shared supporting libraries |
| `tests/` | Instrumented tests and test utilities |
| `multivalentTests/` | Tests for configuration-dependent behavior |
| `samples/` | Sample RROs for system bars and other UI variants |
| `aconfig/` | Feature flag declarations |
| `daggervis/` | Dagger graph visualization support |

The principal Soong modules are:

- `CarSystemUI`: privileged system application that replaces the standard
  `SystemUI` package on automotive builds.
- `CarSystemUI-core`: application wiring and feature composition.
- `CarSystemUI-Shared`: shared implementation and resources.
- `CarSystemUITests`: on-device instrumented tests.
- `CarSystemUIRoboTests`: host-side Robolectric tests.

## Prerequisites

Before building, prepare an AOSP checkout that includes the AAOS platform
dependencies and select a compatible automotive product:

```bash
source build/envsetup.sh
lunch <automotive-product>-<release_config>-userdebug
```

The exact lunch target depends on the product and branch being developed.

## Build

From the root of the Android source tree, build the application and its
dependencies:

```bash
m CarSystemUI
```

When iterating from this directory, the equivalent directory-scoped command is:

```bash
mm
```

Build output is written below `$ANDROID_PRODUCT_OUT`.

## Test

Run the on-device test suite on a connected or virtual AAOS device:

```bash
atest CarSystemUITests
```

Run the host-side Robolectric suite:

```bash
atest CarSystemUIRoboTests
```

The `carsysui-presubmit` test mapping runs `CarSystemUITests` with the
`com.android.systemui.car.CarSystemUiTest` annotation.

## Deploy during development

`CarSystemUI` is a platform-signed, privileged application installed on the
`system_ext` partition. For normal development, build it as part of the selected
AAOS product and flash the resulting image.

On a debuggable build that supports remounting, a faster local iteration cycle
may be available:

```bash
adb root
adb remount
adb sync system_ext
adb shell pkill -TERM -f com.android.systemui
```

If the changed artifact cannot be synchronized or the service does not restart
cleanly, rebuild and flash the product image instead.

## Customize with runtime resource overlays

AAOS products should prefer resources and RROs for product-specific appearance
and configuration instead of modifying shared implementation code. Sample
overlays for system-bar position, shape, transparency, persistence, and other
variants are available in [`samples/`](samples/).

Build all sample overlays from an initialized AOSP build environment:

```bash
mmma <path-to-carsystemui>/samples
```

See [`samples/README.md`](samples/README.md) for installation and activation
examples.

## Planning and testing guides

- [`docs/PLANEJAMENTO_EPICO_JIRA.txt`](docs/PLANEJAMENTO_EPICO_JIRA.txt) contains
  the proposed epic, user stories, technical tasks, acceptance criteria, and
  delivery phases.
- [`docs/CADERNO_DE_TESTES.txt`](docs/CADERNO_DE_TESTES.txt) contains the
  learning path, manual test cases, exploratory sessions, evidence templates,
  and the initial non-functional test plan.

The guides require an educational review and explicit confirmation before any
new automated test is implemented.

## Executable learning showcase

The [`showcase/`](showcase/) directory contains a standalone Android application
for learning and visual experimentation. It can be opened directly in Android
Studio and run on a compatible Android or Android Automotive emulator.

The showcase simulates automotive states and does not replace the privileged
CarSystemUI platform application or prove its behavior.

## Development notes

- Keep automotive behavior in `com.android.systemui.car` where practical and
  reuse shared SystemUI APIs instead of duplicating them.
- Add product differences through overlayable resources or feature flags when
  the behavior is expected to vary between vehicles.
- Add or update tests with every behavior change. Use instrumented tests when
  Android framework or vehicle services are required; prefer Robolectric for
  isolated UI and logic tests.
- Changes to dependency injection should preserve the separation between
  application wiring in `CarSystemUI-core` and reusable code in
  `CarSystemUI-Shared`.

## License

CarSystemUI is part of the Android Open Source Project and is licensed under the
Apache License 2.0. Individual files contain their applicable copyright and
license notices.

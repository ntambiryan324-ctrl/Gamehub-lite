# PocketWin

PocketWin is an Android application that runs compatible Windows desktop software locally through a Wine/Proton and Box64 compatibility environment.

## Main workflow

1. Install the Android app and allow the initial system filesystem setup to finish.
2. Open **Components** and download a Wine or Proton runtime. Optional components such as Box64, DXVK and VKD3D can be installed there when needed.
3. Create a container. Each container has its own Wine prefix and virtual Windows C: drive.
4. Launch that container to open its Wine desktop in the X server display.

## Local storage

The root filesystem and container prefixes are stored under the app's private Android files directory. They use the tablet's internal storage and persist between normal app sessions. Android normally removes this private data when the app is uninstalled, so export/backup is recommended before uninstalling.

## Device profile

The app is ARM64 and retains the Mali-oriented graphics configuration from the Winlator Mali/Bionic base. It does not select Adreno-only Turnip drivers on Mali devices.

## Important compatibility notes

PocketWin is not Windows 11 and does not contain a Microsoft Windows license. It uses Wine/Proton and CPU translation to run supported Windows applications. Application, installer, browser and graphics compatibility varies; some Windows software will not work. The default Wine desktop is styled with a Windows 11-inspired blue bloom wallpaper, but it is not the genuine Windows 11 Explorer shell.

## Build

The repository's GitHub Actions workflow builds the standard ARM64 APK with Java 17, Android SDK 34, CMake 3.22.1 and NDK 29.0.14206865. Pushes to `main` trigger a build and publish the APK as an artifact after archive, package, launcher and native-library checks.

## Upstream and licensing

This project is based on Winlator Mali / Bionic and incorporates Wine/Proton, Box64, Mesa and related open-source components. Review upstream notices and included license files when redistributing. The project retains upstream native engines and runtime assets; the product UI is refocused around local Windows application containers.

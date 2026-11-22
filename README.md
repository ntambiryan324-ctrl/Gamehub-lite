# PocketWin

PocketWin is an Android application that runs compatible Windows desktop software locally through a Wine/Proton and Box64 compatibility environment.

## Main workflow

1. Allow the initial Android system filesystem setup to finish.
2. Open **Components** and download a Wine or Proton runtime. Optional components such as Box64, DXVK and VKD3D can be installed there when needed.
3. Create a container. Each container has its own Wine prefix and virtual Windows C: drive.
4. Launch the container. PocketWin overlays a Windows 11-inspired taskbar, Start menu and desktop shortcuts on the Wine desktop.
5. Open Chrome from the desktop or taskbar. PocketWin can try installing the official Windows MSI under Wine, or use the built-in browser window with web search and popup-window support.

## Desktop and browser

The default Wine desktop wallpaper is the Windows 11 wallpaper bundled with PocketWin. Start, Search, Chrome, Explorer and MQL5 Market shortcuts are available from the desktop/taskbar. The integrated browser uses Android System WebView (Chromium-based), supports ordinary website scripts, cookies, forms, popup windows and downloads. Downloads are saved to Android Downloads, which the container exposes as drive D:.

The Chrome installer option downloads Google's 64-bit Windows Enterprise MSI and attempts to launch it through Wine. This is experimental: the tablet uses an ARM Mali platform, and running the Windows Chrome build through Wine/Box64 is not guaranteed. The integrated browser remains available if Windows Chrome cannot run.

## Compatibility notes

PocketWin is not Windows 11 and does not contain a Microsoft Windows license. The desktop surface, taskbar and Start menu are PocketWin's Windows-inspired UI layered over Wine, not Microsoft's Explorer shell. Compatible Windows programs still run inside Wine/Box64.

MetaTrader 5's own Market popup is separate: MetaQuotes says newer Market/Signals/VPS interfaces use Edge WebView2, while MT5 under Wine continues to use legacy MSHTML. The PocketWin MQL5 Market shortcut opens the public Market in the integrated browser, but it does not replace MT5's internal MSHTML control.

## Build

The GitHub Actions workflow builds the ARM64 APK with Java 17, Android SDK 34, CMake 3.22.1 and NDK 29.0.14206865. Pushes to `main` trigger the standard build and publish the APK after archive, package, launcher and native-library checks.

## Upstream and licensing

This project is based on Winlator Mali / Bionic and incorporates Wine/Proton, Box64, Mesa and related open-source components. Review upstream notices and included license files when redistributing.

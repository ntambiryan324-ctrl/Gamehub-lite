# PocketWin

PocketWin runs compatible Windows desktop software locally on Android through Wine/Proton and Box64.

## Desktop architecture

The desktop controls are a native Windows x86-64 executable running inside the Wine virtual desktop. Its taskbar, Start panel, search box, launchers and app icons are Windows windows and receive Windows mouse events. App icons are extracted from the actual executable files. The previous Android-drawn overlay and Android WebView browser are removed.

## Container setup

1. Complete the initial Android root filesystem setup.
2. Download a Wine or Proton runtime from **Components**.
3. Create a container with its own Wine prefix and virtual Windows C: drive.
4. On first desktop launch, PocketWin stages and starts Google's official Windows x64 Enterprise Chrome MSI from inside Wine. Chrome is intended to install into that container. This is a first-run setup action, not a separate manual installation step.
5. Chrome downloads are stored inside the container's Windows Downloads folder.

The default virtual desktop resolution is 1340×800 for the Galaxy Tab A9. Old stock 1280×720 container settings are migrated on load. The uploaded Windows 11-style wallpaper is regenerated at that resolution.

## Included Windows-compatible applications

The Start panel detects real executables and their real icons, including Chrome, File Explorer, Notepad, WordPad, Command Prompt, Task Manager, Control Panel, Registry Editor and Wine Configuration where those programs are present. No MetaTrader, MQL5 Market or trading software is preinstalled.

## Compatibility limits

PocketWin is not Windows 11 and does not contain a Microsoft Windows license. The native shell is a Windows program running inside Wine; it is designed to provide a mouse-driven Windows-style desktop, not to claim a complete copy of Microsoft's Explorer shell. Windows programs are limited by Wine and Box64 compatibility.

The bundled installer is the Windows x64 Chrome MSI. Chrome itself may still fail to run under Wine/Box64 on this ARM Mali device; the build can verify the installer is present but cannot prove Chrome runs on the tablet. If installed, it will be available from the Start panel. Microsoft Store/UWP apps are not a supported installation path in this Wine setup; ordinary EXE/MSI desktop installers are the target.

MetaTrader 5 is not bundled. You can download the Windows MT5 installer yourself through Chrome. MetaQuotes documents that newer MT5 Market features use Edge WebView2; MT5 under Wine uses the legacy MSHTML engine, so the newer embedded Market features may not work even when the public MQL5 website opens in Chrome.

## Build

GitHub Actions installs MinGW-w64, compiles the Win32 shell, downloads the Chrome Enterprise MSI and Linux root filesystem, then builds and validates the ARM64 APK.

## Upstream and licensing

This project is based on Winlator Mali/Bionic and uses Wine/Proton, Box64, Mesa and other open-source components. Review upstream notices and license files before redistribution.

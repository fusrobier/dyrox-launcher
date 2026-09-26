# Changelog

## 0.1.0 (unreleased)

First version: launcher, accounts, multi-instance and the Dyrox Client for Minecraft 26.3.

### Launcher
- Version manifest (cached offline), vanilla and Fabric installs, SHA-1-verified parallel downloads
- Mojang Java runtimes per version; launch commands for modern and legacy versions
- Instances with their own version, account, memory, JVM arguments, window size, Java, folder and
  profile; live console with filter and copy
- Run several games at once with status, PID, memory, logs, stop and kill; "Launch with accounts"
- Windows installer (MSI) and Linux package (DEB) with a bundled Java runtime

### Accounts
- Microsoft sign-in (browser PKCE or device code) → Xbox Live → XSTS → Minecraft, auto refresh
- Offline profiles; AES-256-GCM vault with DPAPI / Secret Service key; import and export without tokens
- In-game alt manager with session swap

### Dyrox Client
- Event bus, modules, typed settings, JSON profiles, chat commands (`.toggle`, `.bind`, `.set`,
  `.config`, `.friend`, ...)
- Liquid Glass ClickGUI (grid layout, search, tooltips), HUD with array list and notifications
- 40 modules, including KillAura with silent rotations, Scaffold, ESPs, and AntiAim with a smooth
  third-person camera

### Fixed during development
- Criticals and Blink respect 26.x servers' one-position-packet-per-tick rule (no kicks)
- JVM arguments containing quotes reach the game intact on Windows
- OreESP draws only the nearest ores (limit configurable), keeping the frame rate with common ores

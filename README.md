# Dyrox

A Minecraft Java Edition launcher (**Dyrox Launcher**) and Fabric utility client, targeting
Minecraft **26.3**. Windows 11 first, Linux second.

> Work in progress. Phases 2–5 of 8 are done: launcher core, accounts, multi-instance and the client core.
> See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/AUTH.md](docs/AUTH.md) and [docs/MULTI_INSTANCE.md](docs/MULTI_INSTANCE.md).

## Features so far

- Lists every Minecraft version from Mojang's manifest (releases, and snapshots on request), including offline via cache
- Installs vanilla or **Fabric** (loader + Fabric API) automatically
- Parallel downloads with SHA-1 verification and atomic writes for the client jar, libraries, natives and assets
- Installs the right Java runtime for each version from Mojang (Java 25 for 26.3, Java 8 for 1.8.9, ...)
- Correct launch commands for modern (1.13+) and legacy versions, with the access token redacted in logs
- Live, colour-coded game console (parses Minecraft's log4j XML output)
- **Alt manager** with skin heads, account type, last used and a token-validity indicator; add, remove, select, rename, import and export (never exports tokens)
- **Microsoft accounts** through the official OAuth flow (browser with PKCE, or device code) → Xbox Live → XSTS → Minecraft, with automatic token refresh
- **Offline profiles** (username only, clearly labelled) with vanilla-compatible offline UUIDs
- Accounts **encrypted on disk** (AES-256-GCM; key protected by Windows DPAPI or the Linux keyring)
- **Instances** with their own version, loader, account, RAM, JVM arguments, window size, Java, game folder and optional isolated storage
- **Multi-instance**: run several games at once, each with its own account. Live status (starting / running / crashed), PID, memory, uptime and a log per game; stop gracefully or kill
- **Launch with selected accounts**: one click starts one instance per chosen account
- Conflict-safe: game folders are locked while in use, an account can't play twice, and window titles show instance and account
- **Dyrox Client** (Fabric, 26.3), installed automatically: event bus, module system with typed settings, JSON config profiles, `.` chat commands (toggle, bind, config, help). See [docs/CLIENT.md](docs/CLIENT.md)

Microsoft sign-in needs an Azure app ID approved by Mojang. See [docs/AUTH.md](docs/AUTH.md#azure-app-id-required-for-microsoft-accounts).

## Requirements

- JDK 25 to build (Temurin recommended). Gradle is provided by the wrapper.
- Internet on first launch of a version (roughly 600 MB for 26.3 including Java and assets).

## Build and run

```bash
./gradlew build                 # compile + all unit tests
./gradlew :launcher:run         # start the launcher UI
```

Headless dev CLI:

```bash
./gradlew :launcher:runCli --args="versions"
./gradlew :launcher:runCli --args="accounts add-offline Steve"
./gradlew :launcher:runCli --args="accounts login"
./gradlew :launcher:runCli --args="launch 26.3 --fabric"
./gradlew :launcher:runCli --args="instances"
./gradlew :launcher:runCli --args="instances launch main --accounts Steve,Alex"
./gradlew :launcher:runCli --args="launch 1.8.9 --dry-run"
```

Data is stored in `%APPDATA%\DyroxLauncher` on Windows and `~/.local/share/dyrox-launcher` on Linux.
Set `DYROX_HOME` to use another folder.

## Project layout

| Path | What |
|---|---|
| `shared/` | Code shared by launcher and client: HTTP, hashing, platform, offline profiles, colour palette |
| `launcher/` | Compose Desktop launcher and its core (manifest, downloads, Java, Fabric, launch command) |
| `client/` | Dyrox Client Fabric mod (`gradlew :client:build`, `:client:runClient`) |
| `docs/` | Architecture and design notes |

## License

GPL-3.0-or-later. See [LICENSE](LICENSE) and [CREDITS.md](CREDITS.md).
Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

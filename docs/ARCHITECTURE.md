# Dyrox architecture

Dyrox is a Minecraft Java Edition launcher (**Dyrox Launcher**) plus a Fabric utility client
(**Dyrox Client**), modelled on LiquidBounce Nextgen's architecture and look.

## Decisions

| Topic | Decision |
|---|---|
| Minecraft target | **26.3** (Java 25, runtime `java-runtime-epsilon`). The game is unobfuscated from 26.1 on, so the client uses Mojang's class names (`Minecraft`, `LocalPlayer`, `Connection`, ...), not Yarn. The version lives in `gradle.properties` so more versions can be added later. |
| Platforms | Windows 11 first, Linux second. macOS paths exist but are untested. |
| Language | Kotlin 2.4.20, the version fabric-language-kotlin 1.14.1 loads in-game. Mixins in Java. |
| Launcher UI | Compose Multiplatform Desktop with Material 3 and a custom dark/lime theme. |
| ClickGUI | Native rendering through Minecraft's own render pipelines (SDF rounded rectangles). No embedded browser. |
| Microsoft login | Needs an Azure app ID approved by Mojang for Minecraft API access. There isn't one yet, so the ID is a setting and offline accounts are used for testing. |
| License | GPL-3.0-or-later, required to adapt LiquidBounce, Meteor or Wurst code. See `CREDITS.md`. |

## Modules

```
shared/    Pure JVM, no Minecraft classes. Bundled into the mod later, so it depends only on the JDK,
           kotlinx.coroutines and kotlinx.serialization (both provided in-game by fabric-language-kotlin),
           plus JNA as compileOnly (Minecraft ships it).
           http/ HttpService (JDK HttpClient), hash/, io/ AtomicFiles, platform/, theme/ DyroxPalette
           auth/     Microsoft OAuth (browser+PKCE, device code) → Xbox Live → XSTS → Minecraft services
           account/  AccountManager: accounts, sessions, auto-refresh, import/export
           vault/    AES-256-GCM account vault; key via DPAPI / Secret Service / key file
           See docs/AUTH.md.
launcher/  Compose Desktop app
           core/manifest  Mojang version manifest (cached on disk for offline use)
           core/version   version JSON model, inheritsFrom resolution and merging
           core/rules     OS/arch/feature rule evaluation
           core/library   Maven coordinates, per-platform library + natives resolution
           core/download  parallel, SHA-1 verified, atomic downloads
           core/install   client jar / libraries / assets / legacy natives
           core/java      Mojang Java runtime installer
           core/fabric    Fabric loader profile installer
           core/mods      Fabric API (Modrinth); the Dyrox client jar from Phase 5
           core/launch    LaunchCommandBuilder: a pure function, unit-tested against real JSONs
           core/process   GameProcess + log4j XML log parser
           core/settings  launcher.json (Azure client ID; overridable via DYROX_MS_CLIENT_ID)
           core/accounts  skin cache (textures.minecraft.net only)
           ui/            theme, components, screens (Play, Accounts, Settings)
           cli/           headless dev CLI
client/    Fabric mod (Phase 5)
docs/      this folder
```

Both `:shared` and `:launcher` compile with JDK 25 but emit Java 21 bytecode. That keeps `:shared`
loadable by Minecraft 1.21.x (Java 21) if older versions are supported later.

## On-disk layout

```
%APPDATA%\DyroxLauncher\            ($XDG_DATA_HOME/dyrox-launcher on Linux; override: DYROX_HOME)
├─ launcher.json                    settings (no secrets)
├─ accounts.vault                   encrypted accounts (+ .lock)
├─ vault.key.dpapi                  vault key, DPAPI-protected (Windows)
├─ cache\version_manifest_v2.json
├─ cache\skins\
├─ shared\                          shared by all instances
│  ├─ versions\<id>\<id>.json|.jar
│  ├─ libraries\...
│  ├─ assets\{indexes,objects,log_configs,virtual}
│  └─ runtimes\<component>\<platform>\
└─ instances\<id>\
   ├─ minecraft\                    gameDir: options.txt, saves, logs, mods, config
   └─ natives\                      per instance, so parallel instances never share it
```

## Launch pipeline

1. **Loader.** For Fabric, fetch `meta.fabricmc.net/v2/versions/loader/<game>/<loader>/profile/json`
   into `versions/`. The profile `inheritsFrom` the vanilla version.
2. **Resolve.** Load each JSON in the chain (vanilla JSONs are downloaded and SHA-1 checked against the
   manifest) and merge them:
   - child values win
   - arguments add up, parent's first
   - child libraries replace parent libraries with the same `group:artifact[:classifier]`, so the
     classpath never holds two ASM versions
3. **Install.** Download the asset index, then everything else in parallel: client jar, libraries,
   natives, log config and asset objects. Each file goes to a temp file, is checked for size and
   SHA-1, then moved into place. Assets are content-addressed, so for them an existing file of the
   right size is trusted.
4. **Java.** Install `javaVersion.component` from Mojang's runtime catalog. A marker file skips
   re-verification when nothing changed. On Windows the game runs with `javaw.exe`, so no console
   window opens.
5. **Mods.** Fabric API from Modrinth (SHA-1 verified). Older copies are removed.
6. **Command.** `LaunchCommandBuilder` builds the argument list in this order:
   - memory
   - the version's JVM arguments (or launcher defaults for pre-1.13)
   - log config
   - `-D` properties
   - user JVM arguments
   - main class
   - game arguments
   - user game arguments

   The access token is registered as a secret and redacted in the UI and logs.
7. **Run.** `GameProcess` streams stdout/stderr. Mojang's log config makes the game print log4j XML
   events, which are parsed into structured lines (level, thread, logger).

## Phase plan

| Phase | Scope | Status |
|---|---|---|
| 1 | Architecture | done |
| 2 | Launcher core: manifest, downloads, Java, Fabric, launch command | done |
| 3 | Accounts: Microsoft device-code/browser OAuth → XBL → XSTS → Minecraft; encrypted vault; alt manager | done (in-game part after Phase 5) |
| 4 | Multi-instance: instance repository, locks, supervisor, per-instance logs, IPC | next |
| 5 | Client core: event bus, modules, settings, config profiles, commands | |
| 6 | ClickGUI and HUD | |
| 7 | Starter modules per category | |
| 8 | Polish, packaging, docs | |

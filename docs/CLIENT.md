# Dyrox Client

Fabric mod for Minecraft **26.3** (`client/`), mod id `dyrox`. It is written in Kotlin; the mixins are
in Java. The launcher bundles it and installs it into Fabric instances of that version, together with
Fabric API and Fabric Language Kotlin.

## Build setup

- **Loom:** `net.fabricmc.fabric-loom` is the *non-remapping* Loom. 26.x ships unobfuscated, so the
  code uses Mojang's class names directly (`Minecraft`, `KeyboardHandler`, `Connection`) and there are
  no mappings.
- **Dependencies:** plain `implementation`, no `modImplementation`. The output is the normal `jar`.
- **`:shared`:** nested inside the mod jar (`include`). Kotlin stdlib, coroutines and serialization
  come from Fabric Language Kotlin at runtime.
- **Java:** 25 bytecode for the client. `:shared` stays at 21.
- **Decompiled game source:** `gradlew :client:genSources`. Every mixin target was checked against it.

## Architecture

| Piece | File | Notes |
|---|---|---|
| Entry point | `DyroxClient.kt` | Registers modules and commands, loads the profile, hooks Fabric events, connects to the launcher |
| Event bus | `event/EventBus.kt` | Typed, priority-ordered, cancellable. A failing handler is logged once and never breaks others. `handler<T> { }` DSL |
| Events | `event/Events.kt` | Tick, player tick, key, packet send/receive, chat send, HUD render, world render, world change, shutdown |
| Settings | `config/Values.kt` | Boolean, int, float (clamped and snapped to step), int/float range, choice, multi-choice, colour (ARGB + rainbow), text, keybind (toggle/hold), mode (with its own nested settings and handlers) |
| Configurable | `config/Configurable.kt` | Named group of settings with JSON (de)serialisation. Bad values are reported and skipped, not fatal |
| Profiles | `config/ConfigSystem.kt` | `config/dyrox/profiles/<name>.json`, autosaved 2 s after a change and on exit |
| Modules | `module/Module.kt` | Name, category, description, bind, enabled, settings; `onEnable`/`onDisable`; handlers only run while enabled |
| Commands | `command/*` | Prefix `.` (configurable). Quoted arguments, completion API |
| Launcher link | `integration/LauncherBridge.kt` | IPC client, instance name, profile, graceful shutdown |
| Mixin entry | `DyroxHooks.kt` | The only functions mixins call |

Categories: Combat, Movement, Player, Render, World, Misc, Exploit, Fun.
Phase 5 ships one module, **Sprint**, which exercises the whole path (tick event, keybind, settings,
config). The full set comes in Phase 7.

## Settings and profiles

```json
{
  "version": 1,
  "modules": {
    "Sprint": {
      "Enabled": true,
      "Bind": { "key": "key.keyboard.r", "mode": "Toggle" }
    }
  }
}
```

- **Sections:** `modules` is the only section so far. Phase 6 adds `gui` and `hud`.
- **Unknown or invalid values:** a warning is logged and the rest of the profile still loads.
- **Missing profiles:** loading one starts from defaults; it is created on the next save.
- **Which profile loads:**
  1. the launcher's per-instance profile (`-Ddyrox.profile`), otherwise
  2. the last one used (`config/dyrox/client.json`), otherwise
  3. `default`.
- **Keybinds** store Minecraft's *key names* (`key.keyboard.right.shift`), not codes. 26.x uses SDL
  scancodes internally (e.g. right shift = 229); names are stable across versions.

## Commands

| Command | Does |
|---|---|
| `.help [command]` (`.?`) | List commands / explain one |
| `.toggle <module> [on\|off]` (`.t`) | Toggle a module |
| `.bind <module> <key\|none> [toggle\|hold]` (`.b`) | Bind a key; `hold` = active only while held. Keys: `r`, `rshift`, `f5`, `num5`, … |
| `.binds` | List keybinds |
| `.modules [category]` | List modules |
| `.config <load\|save\|list\|delete> [name]` (`.profile`, `.cfg`) | Manage profiles; `save <name>` saves as and switches |
| `.prefix <prefix>` | Change the prefix (1–3 chars, not `/`) |

- **Privacy:** command messages never reach the server. The mod intercepts them through Fabric's
  `ClientSendMessageEvents.ALLOW_CHAT`, and they still go into chat history.
- **Scope:** keybinds are keyboard-only for now, and they never fire while a screen (chat, GUI) is open.

## Launcher integration

| From the launcher | Used for |
|---|---|
| `-Ddyrox.instance.name` | Window title: `Minecraft* 26.3 — <instance> · <account>` |
| `-Ddyrox.profile` | Config profile for the instance |
| `DYROX_IPC_PORT` / `DYROX_IPC_TOKEN` (environment) | IPC. `shutdown` → `Minecraft.stop()` on the render thread (same as closing the window, worlds are saved). Status is reported on start, world join and leave |

The in-game alt manager (Phase 6 UI) will use `IpcClient.requestAccounts()` and `requestSession()`.

## Mixins

See [MIXINS.md](MIXINS.md).

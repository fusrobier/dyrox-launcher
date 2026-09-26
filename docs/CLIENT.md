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

## ClickGUI, HUD and alt manager (Phase 6)

### Liquid Glass design

The Dyrox UI follows Apple's *Liquid Glass* style, built entirely on vanilla GUI pipelines (no custom
shaders):

| Element | How it's drawn (`render/Draw.kt`) |
|---|---|
| Frosting | Vanilla menu blur behind the screen (`Blur` setting), plus the menu panorama on the title screen so the glass always has something to frost |
| Glass surface | Translucent white body, a sheen fading down from the top, a thin light rim, a brighter specular top edge, and a soft layered drop shadow |
| Rounded corners | Anti-aliased: tinted blits of a generated disc texture for fills and per-radius ring textures for 1px rims, both linearly filtered. Nothing overlaps, so translucent layers blend evenly |
| Controls | Capsule rows (accent-tinted when enabled), iOS-style switches with a white knob, capsule sliders and range sliders, chips for choices, keys and modes, HSB colour picker |
| Typeface | **Inter** (SIL OFL 1.1, `assets/dyrox/font/`), the closest open match to Apple's SF Pro, which may not be redistributed. The `Font` setting switches back to the Minecraft font |

HUD surfaces sit over the unblurred game, so they use a darker glass tint to stay readable.

### ClickGUI (Right Shift)

- **Panels:** one per category. Drag by the header; right-click the header to collapse.
- **Modules:** left-click toggles, right-click shows the settings.
- **Scrolling:** mouse wheel, when a panel is taller than the screen.
- **Search:** click the bar or just start typing; it matches names and descriptions.
- **Tooltips:** hovering a module or setting shows its description.
- **Keybinds:** in a bind field, press a key; Esc cancels, Backspace/Delete unbinds, right-click switches toggle/hold.
- **Layout:** positions and collapsed state are saved in the profile (`gui` section). On first open,
  panels lay themselves out to fit the window.
- **Theme settings** (module *ClickGUI*): Accent colour (with rainbow), Font, Blur, Animation speed.

`ClickGUI` is a **trigger module**: its key opens the menu instead of toggling a state, and it never
appears as "enabled".

### HUD (module *HUD*, on by default)

- **Watermark:** glass capsule with version and FPS.
- **Array list:** enabled modules, widest first, sliding in and out. Accent or rainbow colours.
- **Notifications:** glass toasts with a time bar, including toggle notifications. Toggles caused by
  loading a profile don't notify.
- **F1:** hides everything, like the vanilla HUD.

### In-game alt manager

- **Button:** an **Alt Manager** button on the multiplayer screen.
- **Account list:** the launcher's accounts, fetched over IPC. The current account is highlighted, and
  accounts already playing in another instance are disabled.
- **Switching:** picking an account asks the launcher for a fresh session (the launcher refreshes
  tokens), then swaps it in without a restart. The window title follows.
- **Without the launcher:** only offline names can be used.
- **What the swap replaces:** the user, the user API service, the properties and profile fetches,
  chat-signing keys and the reporting context, exactly as 26.3's `Minecraft` constructor builds them
  (see [MIXINS.md](MIXINS.md#minecraftaccessor)).
- **What it doesn't replace:** the friends-list service and telemetry. They keep the launch account
  until restart.
- **Scope:** only from menus, never inside a world.

### Developer aids

Inert unless a `-Ddyrox.debug.*` property is set (per-instance JVM arguments in the launcher):

| Property | Effect |
|---|---|
| `dyrox.debug.world=true` | Create or reopen a creative "Dyrox Test" world from the title screen |
| `dyrox.debug.screen=clickgui\|altmanager` | Open that screen (on the title screen, or once in the world) |
| `dyrox.debug.expand=HUD,ClickGUI` | Modules whose settings start expanded in the ClickGUI |
| `dyrox.debug.toggle=Sprint` | Toggle a module after joining the world (HUD and toast check) |
| `dyrox.debug.switch=Alex` | With the alt manager open, switch to that launcher account |

Each run saves a framebuffer screenshot to `screenshots/dyrox-debug.png`. Framebuffer screenshots
show exact colours; Windows window captures can look brighter because of colour management.

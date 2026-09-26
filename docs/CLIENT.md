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
| Rotations | `rotation/RotationManager.kt` | Silent (server-side) rotations: highest-priority request wins, limited turn speed, mouse-step (GCD) rounding, glides back to the camera when released |
| Targets / friends | `combat/Targets.kt`, `combat/Friends.kt` | Players / hostile / passive classification; friends in `config/dyrox/friends.json` (shared by all profiles) |
| Third-person camera | `render/CameraController.kt` | Temporary, animated third person (AntiAim); hands control back if you press F5 |
| World shapes | `render/WorldShapes.kt` | Boxes and lines through Minecraft's gizmo renderer (see MIXINS.md) |

Categories: Combat, Movement, Player, Render, World, Misc, Exploit, Fun. The module list is in
[Modules](#modules-phase-7).

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
| `.set <module> <setting> [value]` (`.v`) | Show or change a setting: `.set killaura range 3.5`, `.set killaura turnspeed 60-90`, `.set esp entities items` (multi-choice entries toggle). Names ignore case and spaces |
| `.friend <add|remove|list|clear> [name]` (`.f`) | Manage friends (never attacked, own ESP colour). Middle-clicking a player also toggles them |

- **Privacy:** command messages never reach the server. The mod intercepts them through Fabric's
  `ClientSendMessageEvents.ALLOW_CHAT`, and they still go into chat history.
- **Scope:** keybinds are keyboard-only for now, and they never fire while a screen (chat, GUI) is open.

## Modules (Phase 7)

40 modules. They were checked in a real 26.3 singleplayer game with scripted runs
(`dyrox.debug.script`: commands, key presses, probes, screenshots). Not covered by those runs:
MiddleClickFriend (needs a second player), AutoReconnect (needs a server), InventoryMove (reads the
physical keyboard) and NoHurtCam (visual only).

Default keys: ClickGUI Right Shift, KillAura R, Zoom C (hold); everything else is unbound (`.bind`).

| Category | Module | What it does | Main settings |
|---|---|---|---|
| Combat | KillAura | Attacks the best target in range; silent rotations that turn at a limited, randomised speed; only hits when the server-side ray really hits the target | Range, Scan range, Targets, Priority, Rotations (Silent/Lock view/None), Turn speed, Timing (Cooldown/CPS), FOV, Through walls, Target ESP ring |
| | TriggerBot | Attacks the entity under the crosshair once the weapon has recharged | Targets, Cooldown, Delay |
| | Velocity | Scales knockback from hits and explosions | Horizontal %, Vertical %, Explosions |
| | Criticals | Every hit a critical: Packet (reports a 0.0625-block hop before the attack) or Jump | Mode |
| | AutoTotem | Keeps a totem in the off hand | Delay |
| Movement | Sprint | Always sprint when vanilla allows it | |
| | Fly | Vanilla (creative flight) or Motion; anti-kick | Mode, Speed, Vertical speed |
| | Speed | Strafe Hop or Ground speed | Mode, Speed |
| | Step | Walk up full blocks | Height |
| | NoSlow | Full speed and sprint while eating, blocking, drawing a bow | Speed, Sprint |
| | SafeWalk | Never walk off edges (sneak edge logic, no slowdown) | Only on ground |
| | AutoWalk | Holds forward | |
| | InventoryMove | Walk, jump and sprint with inventories, containers and the ClickGUI open (not while typing) | Sneak |
| | Spider | Climb walls | Speed |
| Player | NoFall | No fall damage (claims ground after 2 blocks of falling, below the damage threshold) | |
| | FastPlace | Shorter right-click delay | Delay |
| | AutoTool | Best hotbar tool while mining, switches back | Switch back |
| | ChestStealer | Empties chests, barrels and shulker boxes, then closes them | Delay (ms range), Auto close |
| | AutoRespawn | Respawns after death | Delay |
| | AutoEat | Eats hotbar food below a hunger level, skips bad food | Hunger, Pause in combat |
| Render | ClickGUI, HUD | Phase 6 | |
| | ESP | Glow outline or boxes through walls, per-type colours, friends highlighted | Mode, Entities, colours, Box fill |
| | Tracers | Lines from the crosshair to entities | Targets, Color (distance/ESP), Max distance, Width |
| | StorageESP | Boxes around chests, ender chests, barrels, shulkers, hoppers, furnaces | Blocks, colours |
| | OreESP | X-ray for ores: scans nearby chunks, outlines selected ores | Ores, Radius, Chunks per tick |
| | Fullbright | Full brightness, no darkness effect | |
| | NoHurtCam | No hurt camera shake | |
| | Zoom | Hold C: smooth zoom with slower mouse | Zoom, Smooth, Slow mouse |
| | CameraClip | Third-person camera through walls | Distance |
| World | Scaffold | Places blocks under you (bridging, towering), silent rotations, target outline | Rotations, Turn speed, Safe walk, Swing, Restore slot |
| | Nuker | Breaks blocks around you (many per tick in creative, one at a time in survival) | Range, Shape (Flat/Sphere), Blocks per tick, Rotate |
| | FastBreak | Finishes blocks at 70 % (the vanilla server minimum) and skips the 5-tick pause | Break at, No delay |
| | Timer | Client tick speed | Speed |
| Exploit | Blink | Holds movement and action packets; others see you frozen (ghost box), then you catch up | Pulse, Show ghost |
| Misc | MiddleClickFriend | Middle-click a player to (un)friend them | |
| | AutoReconnect | "Reconnect" button on the disconnect screen; automatic after a delay while enabled | Delay |
| Fun | AntiAim | Spinbot / jitter / backwards / random yaw and pitch, server-side only. Smooth third-person camera while active (glides out, glides back and restores first person when disabled) and your model shows the spoofed rotation | Yaw, Spin speed, Jitter angle, Pitch, Third person, Camera distance, Camera glide |
| | Twerk | Sneak on and off | Interval |
| | SkinDerp | Random skin layers | Interval |

Rotation priorities: Scaffold > KillAura > Nuker > AntiAim, so AntiAim can stay on while you fight
or build.

Server notes: everything here works on vanilla servers. Anti-cheat plugins detect some modules
(Fly, Speed, Timer, Blink, Criticals Packet); the settings default to conservative values.

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
| `dyrox.debug.script=...` | Scripted steps once in the world (vanilla and Dyrox commands, key presses, probes to the log, screenshots). See `DebugScript.kt`. Used to verify Phase 7 modules in a real game |

Each run saves a framebuffer screenshot to `screenshots/dyrox-debug.png`. Framebuffer screenshots
show exact colours; Windows window captures can look brighter because of colour management.

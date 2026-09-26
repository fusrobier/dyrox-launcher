# Mixins (Minecraft 26.3)

Every target below was checked against the decompiled 26.3 source (`gradlew :client:genSources`)
before it was written. `defaultRequire: 1` makes a missed injection crash at startup instead of
silently doing nothing. Each mixin body is a single call into `DyroxHooks`, so re-targeting for a new
Minecraft version touches only the mixin.

Fabric API is used instead of a mixin wherever it offers the hook:

| Need | Fabric API |
|---|---|
| Client tick | `ClientTickEvents.START/END_CLIENT_TICK` |
| Chat commands | `ClientSendMessageEvents.ALLOW_CHAT` |
| HUD | `HudElementRegistry.addLast` (26.x extracts render state via `GuiGraphicsExtractor`) |
| World render | `LevelRenderEvents.END_MAIN` |
| Join/leave | `ClientPlayConnectionEvents.JOIN/DISCONNECT` |
| Start/stop | `ClientLifecycleEvents.CLIENT_STARTED/CLIENT_STOPPING` |

## MinecraftMixin

| | |
|---|---|
| Target | `net.minecraft.client.Minecraft#createTitle()` → `String` (private) |
| Injection | `@Inject(at = RETURN, cancellable)`, appends `" — <instance> · <account>"` |
| Why here | `updateTitle()` calls it for every vanilla title change (world join/leave), so the suffix always survives |

## KeyboardHandlerMixin

| | |
|---|---|
| Target | `net.minecraft.client.KeyboardHandler#keyPress(long handle, int action, KeyEvent event)` |
| Injection | `@Inject(at = HEAD)`. Same window-handle guard as vanilla |
| 26.3 specifics | `KeyEvent` is a record `(int key, int keycode, int modifiers)`. `key` is an **SDL scancode**, converted with `InputConstants.getKey(event).getName()`. `action`: `PRESS = 1`, `RELEASE = 0`, `REPEAT = -1` |

## ConnectionMixin

| | |
|---|---|
| Target (send) | `net.minecraft.network.Connection#send(Packet, ChannelFutureListener, boolean)`: the other public `send` overloads delegate to it |
| Target (receive) | `Connection#channelRead0(ChannelHandlerContext, Packet)`: runs on the Netty thread before `genericsFtw` hands the packet to its listener |
| Injection | `@Inject(at = HEAD, cancellable)` on both |
| Filter | Only when `receiving == PacketFlow.CLIENTBOUND`, i.e. the client's own connection. In singleplayer the integrated server's connections go through the same class |
| Threading | Receive handlers run on the Netty thread; modules that touch world state must hop to the client thread (`Minecraft.execute`) |

## MinecraftAccessor

| | |
|---|---|
| Target | `net.minecraft.client.Minecraft` fields (26.3) |
| Kind | Accessor interface: `@Mutable @Accessor` setters for the final fields `user`, `userApiService`, `userPropertiesFuture`, `profileFuture`, `profileKeyPairManager`; a setter for `reportingContext`; getters for `proxy` and `services` |
| Why | The in-game account switch. In 26.3 these are built once in the constructor from the launch user: `createUserApiService(discoveryService, …)`, `ProfileKeyPairManager.create(userApiService, user, gameDir)`, `ReportingContext.create(ReportEnvironment.local(), userApiService)`. `SessionSwapper` rebuilds them the same way (`MinecraftServicesDiscoveryService.create(proxy, true)` for a new user API service, `UserApiService.OFFLINE` and `ProfileKeyPairManager.EMPTY_KEY_MANAGER` for offline accounts) and calls `updateTitle()` |
| Not swapped | `playerSocialManager` / friends service and `telemetryManager` (background threads started at launch) |

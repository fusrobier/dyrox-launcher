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
| Disconnect-screen button | `ScreenEvents.AFTER_INIT` + `Screens.getWidgets` |

MixinExtras (bundled with Fabric Loader) is used for `@WrapOperation` and `@WrapMethod`, which
chain with other mods instead of replacing their changes.

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
| Also | Getter/setter for `rightClickDelay` (FastPlace) |

## Phase 7 mixins

All targets are Minecraft 26.3 (Mojang names). "Local player only" means the hook checks
`entity == Minecraft.player` before changing anything.

| Mixin | Target | Injection | Used by |
|---|---|---|---|
| `LocalPlayerMixin` | `LocalPlayer#tick()` | `@Inject HEAD` → `PlayerPreTickEvent` | Fly, Speed, Spider, AutoWalk, InventoryMove, AutoEat, Twerk, Criticals |
| | `LocalPlayer#sendPosition()` (private) | `@Inject HEAD` + `RETURN`: `PreMotionEvent`, swap yRot/xRot/onGround for the packet, restore, `PostMotionEvent` | RotationManager (silent rotations), NoFall, KillAura, Scaffold, Nuker, AntiAim |
| | `LocalPlayer#itemUseSpeedMultiplier()`, `#isSlowDueToUsingItem()` (private) | `@Inject RETURN` | NoSlow |
| `PlayerMixin` | `Player#isStayingOnGroundSurface()` (protected; what sneaking uses in `maybeBackOffFromEdge`) | `@Inject RETURN`, local player only | SafeWalk, Scaffold |
| `EntityMixin` | `Entity#pushFromExplosion(Vec3)` | `@ModifyVariable HEAD` on the impulse, local player only | Velocity |
| `ClientPacketListenerMixin` | `handleSetEntityMotion` → `Entity#lerpMotion(Vec3)` | `@WrapOperation`, local player only | Velocity |
| `MultiPlayerGameModeMixin` | `MultiPlayerGameMode#attack(Player, Entity)` | `@Inject HEAD` → `AttackEvent` | Criticals |
| `MultiPlayerGameModeAccessor` | fields `destroyProgress`, `destroyDelay` | accessors | FastBreak |
| `CameraMixin` | `Camera#getMaxZoom(float)` (private, third-person wall check) | `@WrapMethod`: change the wanted distance, optionally skip the wall check | AntiAim camera (CameraController), CameraClip |
| | `Camera#calculateFov(float)` (private) | `@Inject RETURN` | Zoom |
| `MouseHandlerMixin` | `MouseHandler#turnPlayer` → `LocalPlayer#turn(DD)V` | `@ModifyArg` ×2 | Zoom (slower mouse) |
| `LevelExtractorMixin` | `LevelExtractor#extract(DeltaTracker, Camera, float)` before `extractGizmos()` | `@Inject INVOKE` → `WorldGizmoEvent` | ESP boxes, Tracers, StorageESP, OreESP, KillAura target ring, Scaffold/Nuker/Blink boxes |
| `LivingEntityRendererMixin` | `LivingEntityRenderer#extractRenderState(LivingEntity, LivingEntityRenderState, float)` | `@Inject RETURN`, local player only | Shows the server-side rotation on your model (third person) |
| `EntityRendererMixin` | `EntityRenderer#extractRenderState(Entity, EntityRenderState, float)` | `@Inject RETURN`, recolours `outlineColor` | ESP glow colours |
| `MinecraftMixin` | `Minecraft#shouldEntityAppearGlowing(Entity)` | `@Inject RETURN` | ESP glow |
| `GameRendererMixin` | `GameRenderer#bobHurt(CameraRenderState, PoseStack)` (private) | `@Inject HEAD cancellable` | NoHurtCam |
| `DeltaTrackerTimerMixin` | `DeltaTracker.Timer#advanceGameTime(long)` → `FloatUnaryOperator#apply(F)F` (target ms per tick) | `@Redirect`, divides by the speed | Timer |
| `LightmapRenderStateExtractorMixin` | `LightmapRenderStateExtractor#extract(LightmapRenderState, float)` | `@Inject TAIL` when `needsUpdate` | Fullbright |

### Why gizmos for world rendering

26.x renders the world from extracted render state. Minecraft's own debug shapes ("gizmos": lines,
boxes, circles, text) are collected per frame while `LevelExtractor.extract` runs and handed to
`LevelRenderer` in `extractGizmos()`. Emitting ours just before that call puts them in the same frame,
with vanilla's line rendering and `setAlwaysOnTop()` for see-through-walls ESP, without touching
render pipelines.

### Silent rotations

`sendPosition()` builds the movement packet from `getYRot()`, `getXRot()` and `onGround()`, then
remembers them as `yRotLast`/`xRotLast`. Swapping the fields at HEAD and restoring at RETURN means the
server (and the next tick's "did the rotation change?" check) sees the spoofed values, while the
camera, movement input and rendering keep the real ones.

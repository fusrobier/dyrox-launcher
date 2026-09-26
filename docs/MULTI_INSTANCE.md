# Multi-instance

Dyrox runs any number of Minecraft instances at the same time, each in its own process, with its own
account, game folder, settings, log and IPC token.

## Instances

Each instance lives in `instances/<id>/instance.json`:

| Setting | Notes |
|---|---|
| Name, Minecraft version, loader (Vanilla/Fabric, optional loader version) | |
| Account | Its own choice, or the launcher's default account |
| Memory, extra JVM arguments, window size, Java path | Java path empty = Mojang's runtime for the version |
| Game folder | Default `instances/<id>/minecraft`, or a custom path. **Must be unique**: saving is refused if it equals or nests with another instance's folder |
| Isolated storage | Off: versions/libraries/assets are shared (saves disk). On: own copy under `instances/<id>/storage`. Java runtimes are always shared |
| Dyrox config profile | Passed to the client mod as `-Ddyrox.profile` (Phase 5) |

The Phase 2–3 folder `instances/default` is adopted automatically as "Default". Deleting an instance
moves its folder to the Recycle Bin. A custom game folder outside the instance folder is never deleted.

## Conflict prevention

| Risk | Prevention |
|---|---|
| Two games writing `options.txt`, saves, logs | Every instance has its own game folder, and it's **locked** while its game runs: an OS file lock plus the game's PID in `.dyrox-instance.lock`. The PID still protects the folder if the launcher was closed while the game kept running |
| Same instance started twice | Refused while its session is active |
| Same account in two games | Refused (Mojang would disconnect one of them anyway). The in-game alt manager is refused too |
| Parallel installs into the shared store | Installs are serialised. A second launch waits ("Waiting for another instance to finish installing") and then finds everything present |
| Shared natives folder | `instances/<id>/natives` per instance |
| Indistinguishable windows | The Dyrox client (Phase 5) sets the title from `-Ddyrox.instance.name`. Without it (vanilla, other versions) the launcher appends ` — <instance> · <account>` on Windows |
| Local ports | The only port the launcher opens is its IPC server (random loopback port, shared by all instances, one token each). Game-side features must use port 0 (OS-assigned) |

## Lifecycle

`Preparing → Starting → Running → Stopping → Exited | Crashed | Failed`

- **Running**: the log shows `Sound engine started` / LWJGL init, or the Dyrox client connects over IPC.
- **Crashed**: a non-zero exit the user didn't ask for, or a crash message in the log. The newest crash
  report (or `hs_err_pid*.log`) written during the session is linked.
- **Stop** is graceful, first option that applies:
  1. IPC `shutdown`, if the Dyrox client is connected
  2. `WM_CLOSE` to the game window on Windows. SDL/GLFW treat it as a normal quit, so worlds are saved
  3. SIGTERM elsewhere
- **Kill** force-ends the process tree.
- Memory (working set / VmRSS) is sampled every 2 s. The Running screen shows PID, memory and uptime.
- Each session keeps its log (last 20,000 lines) after the game exits, so crashes can be inspected.

## Launch with selected accounts

Pick an instance and tick accounts. The first account plays in the instance itself. Each further
account gets a **sibling**: `<name> #2`, `#3`, … (`linkedTo` = the template), reused on later runs.

- **Every run:** siblings are re-synced to the template's version, loader, memory, JVM, Java and
  storage settings.
- **First creation only:** siblings get a copy of `options.txt`, `servers.dat`, `config/` and `mods/`,
  so keybinds, servers and mods match.
- **Launch order:** games start 2 s apart, and instances already running are skipped.

## Launcher ⇄ game IPC

The launcher runs one loopback TCP server (`IpcServer`). Each game process gets `DYROX_IPC_PORT`,
`DYROX_IPC_TOKEN` and `DYROX_INSTANCE_ID` **as environment variables** (not arguments, so the token
doesn't show in process lists). The protocol is JSON, one message per line (`IpcProtocol.kt`).
The game must send `hello` with its token within 5 s, or it's disconnected.

| Direction | Message | Purpose |
|---|---|---|
| game → launcher | `hello` | Identify with the token → launcher replies `welcome` |
| game → launcher | `status` | Progress info for the launcher log |
| game → launcher | `accounts_request` | Account list for the in-game alt manager (`inUse` flags accounts playing elsewhere) |
| game → launcher | `session_request` | Fresh `GameSession` for an account; the launcher refreshes tokens, so they are never refreshed in two places |
| launcher → game | `shutdown` | Graceful quit |

`IpcClient` (in `:shared`) is the game-side implementation the Dyrox client mod will use.

## Closing the launcher

If games are running, the launcher asks:

- **Stop all & close:** graceful stop, 30 s grace period, then kill.
- **Leave running:** the games continue, but their logs, status and IPC stop until relaunched.
- **Cancel.**

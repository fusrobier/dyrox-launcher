# Credits

Dyrox is licensed under the GNU General Public License v3.0 or later (see `LICENSE`).

## Adapted code

Every file adapted from another project is listed here with its source and commit, and keeps its
original copyright header plus a "Modified by Dyrox" note.

| Dyrox file | Source project | Source file | Commit | License |
|---|---|---|---|---|
| _none yet_ | | | | |

The client modules (Phase 7) will study the structure of:

- [LiquidBounce](https://github.com/CCBlueX/LiquidBounce) (GPL-3.0)
- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) (GPL-3.0)
- [Wurst Client](https://github.com/Wurst-Imperium/Wurst7) (GPL-3.0)

## Services and data formats

The launcher talks to these public APIs. No code from them is included.

- Mojang version manifest, version JSONs, assets and Java runtime catalog (`piston-meta.mojang.com`, `launchermeta.mojang.com`, `resources.download.minecraft.net`)
- Fabric meta API (`meta.fabricmc.net`) for loader profiles
- Modrinth API (`api.modrinth.com`) for Fabric API downloads
- Microsoft identity platform, Xbox Live / XSTS and Minecraft services, for Microsoft account sign-in (official, documented flow)

## Libraries

- [Kotlin](https://kotlinlang.org/), kotlinx.coroutines, kotlinx.serialization (Apache-2.0)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) (Apache-2.0)
- [Fabric Loader, Fabric API, Fabric Loom, Fabric Language Kotlin](https://fabricmc.net/) (Apache-2.0), mod platform (not redistributed; downloaded from Modrinth at install time)
- [Mixin](https://github.com/SpongePowered/Mixin) (MIT), via Fabric Loader
- [Inter](https://github.com/rsms/inter) typeface v4.1 by Rasmus Andersson (SIL Open Font License 1.1), bundled in the client (`assets/dyrox/font/`, licence in `licenses/Inter-OFL.txt`)
- [JNA](https://github.com/java-native-access/jna) (Apache-2.0 / LGPL-2.1), for Windows DPAPI
- [JUnit 5](https://junit.org/junit5/) (EPL-2.0, test only)

Minecraft is a trademark of Mojang Studios. Dyrox is not an official Minecraft product and is not
approved by or associated with Mojang or Microsoft. Game files are always downloaded from Mojang's
servers and are never redistributed.

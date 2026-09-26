package net.dyrox.client.altmanager

import com.mojang.authlib.exceptions.AuthenticationException
import com.mojang.authlib.minecraft.UserApiService
import com.mojang.authlib.services.MinecraftServicesDiscoveryService
import com.mojang.authlib.services.ProfileResult
import net.dyrox.client.mixin.MinecraftAccessor
import net.dyrox.shared.account.AccountType
import net.dyrox.shared.ipc.IpcSession
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.client.multiplayer.ProfileKeyPairManager
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment
import net.minecraft.client.multiplayer.chat.report.ReportingContext
import net.minecraft.util.Util
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Replaces the logged-in account without restarting. Only allowed outside a world (the alt manager
 * lives on the multiplayer screen): joining a server afterwards authenticates as the new account, and
 * chat signing uses its keys.
 *
 * Not swapped: the social/friends service and telemetry, which 26.3 wires up once at start-up with
 * background threads; they keep referring to the launch account until restart.
 */
object SessionSwapper {
    private val logger = LoggerFactory.getLogger("Dyrox/AltManager")

    fun swap(session: IpcSession) {
        val minecraft = Minecraft.getInstance()
        check(minecraft.isSameThread) { "Session swaps must run on the client thread" }
        check(minecraft.level == null) { "Leave the world before switching accounts" }

        val uuid = parseUuid(session.uuid)
        val user = User(session.username, uuid, session.accessToken, Optional.ofNullable(session.xuid), Optional.empty())
        val accessor = minecraft as Any as MinecraftAccessor
        val offline = session.type == AccountType.OFFLINE

        val api: UserApiService = if (offline) {
            UserApiService.OFFLINE
        } else {
            try {
                MinecraftServicesDiscoveryService.create(accessor.`dyrox$getProxy`(), true).createUserApiService(session.accessToken)
            } catch (e: Exception) {
                logger.warn("Could not create the user API service; chat features may be limited", e)
                UserApiService.OFFLINE
            }
        }

        accessor.`dyrox$setUser`(user)
        accessor.`dyrox$setUserApiService`(api)
        accessor.`dyrox$setUserPropertiesFuture`(
            CompletableFuture.supplyAsync({
                try {
                    api.fetchProperties()
                } catch (_: AuthenticationException) {
                    UserApiService.OFFLINE_PROPERTIES
                }
            }, Util.nonCriticalIoPool()),
        )
        val sessionService = accessor.`dyrox$getServices`().sessionService()
        accessor.`dyrox$setProfileFuture`(
            if (offline) {
                CompletableFuture.completedFuture(null)
            } else {
                CompletableFuture.supplyAsync<ProfileResult?>({ sessionService.fetchProfile(uuid, true) }, Util.nonCriticalIoPool())
            },
        )
        accessor.`dyrox$setProfileKeyPairManager`(
            if (offline) ProfileKeyPairManager.EMPTY_KEY_MANAGER else ProfileKeyPairManager.create(api, user, minecraft.gameDirectory.toPath()),
        )
        accessor.`dyrox$setReportingContext`(ReportingContext.create(ReportEnvironment.local(), api))
        minecraft.updateTitle()
        logger.info("Switched account to {} ({})", session.username, session.type)
    }

    /** Accepts undashed (Mojang API style) and dashed UUIDs. */
    fun parseUuid(value: String): UUID {
        val hex = value.replace("-", "")
        require(hex.length == 32) { "Invalid UUID $value" }
        return UUID(java.lang.Long.parseUnsignedLong(hex.substring(0, 16), 16), java.lang.Long.parseUnsignedLong(hex.substring(16), 16))
    }
}

package net.dyrox.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.ProfileResult;
import java.net.Proxy;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Session swap for the in-game alt manager. In 26.3 these fields are set once in the constructor from
 * the launch user (see {@code Minecraft.<init>}): the user itself, the user API service built from its
 * token, the async user-properties and profile fetches, the chat-signing key manager and the reporting
 * context. A swap replaces all of them the same way the constructor builds them.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Mutable
	@Accessor("user")
	void dyrox$setUser(User user);

	@Mutable
	@Accessor("userApiService")
	void dyrox$setUserApiService(UserApiService service);

	@Mutable
	@Accessor("userPropertiesFuture")
	void dyrox$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

	@Mutable
	@Accessor("profileFuture")
	void dyrox$setProfileFuture(CompletableFuture<ProfileResult> future);

	@Mutable
	@Accessor("profileKeyPairManager")
	void dyrox$setProfileKeyPairManager(ProfileKeyPairManager manager);

	@Accessor("reportingContext")
	void dyrox$setReportingContext(ReportingContext context);

	@Accessor("proxy")
	Proxy dyrox$getProxy();

	@Accessor("services")
	Services dyrox$getServices();
}

package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Window title: appends " — <instance> · <account>" so parallel instances are distinguishable.
 * Target (26.3): {@code private String Minecraft.createTitle()}, called by {@code updateTitle()}
 * whenever the vanilla title changes (world join/leave, etc.).
 * <p>
 * ESP glow: {@code Minecraft.shouldEntityAppearGlowing(Entity)} decides whether an entity gets the
 * vanilla outline (the Glowing effect look).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void dyrox$decorateTitle(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(DyroxHooks.decorateTitle(cir.getReturnValue()));
	}

	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void dyrox$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && DyroxHooks.shouldGlow(entity)) cir.setReturnValue(true);
	}
}

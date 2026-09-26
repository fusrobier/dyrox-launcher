package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Targets (26.3):
 * <ul>
 * <li>{@code tick()} HEAD: PlayerPreTickEvent, before input and movement.</li>
 * <li>{@code private void sendPosition()}: builds the movement packet from yRot/xRot/onGround. HEAD
 * swaps in the server-side rotation (RotationManager) and ground flag, RETURN restores them, so the
 * camera never moves.</li>
 * <li>{@code private float itemUseSpeedMultiplier()} and {@code private boolean isSlowDueToUsingItem()}:
 * item-use slowdown and sprint stop (NoSlow).</li>
 * </ul>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void dyrox$preTick(CallbackInfo ci) {
		DyroxHooks.onPlayerPreTick();
	}

	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void dyrox$preMotion(CallbackInfo ci) {
		DyroxHooks.preMotion((LocalPlayer) (Object) this);
	}

	@Inject(method = "sendPosition", at = @At("RETURN"))
	private void dyrox$postMotion(CallbackInfo ci) {
		DyroxHooks.postMotion((LocalPlayer) (Object) this);
	}

	@Inject(method = "itemUseSpeedMultiplier", at = @At("RETURN"), cancellable = true)
	private void dyrox$itemUseSpeed(CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(DyroxHooks.itemUseSpeed(cir.getReturnValueF()));
	}

	@Inject(method = "isSlowDueToUsingItem", at = @At("RETURN"), cancellable = true)
	private void dyrox$itemUseSprint(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(DyroxHooks.itemUseBlocksSprint(cir.getReturnValueZ()));
	}
}

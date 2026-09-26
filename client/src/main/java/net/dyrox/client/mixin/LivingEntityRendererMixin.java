package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spoofed rotations on our own model: {@code extractRenderState(LivingEntity, LivingEntityRenderState, float)}
 * sets bodyRot/yRot/xRot; at RETURN they are replaced with the server-side rotation (only visible in
 * third person, since first person does not draw the local player).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("RETURN")
	)
	private void dyrox$modelRotation(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		DyroxHooks.modifyModel(entity, state, partialTicks);
	}
}

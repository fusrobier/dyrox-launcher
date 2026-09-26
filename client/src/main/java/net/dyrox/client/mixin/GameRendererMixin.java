package net.dyrox.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.dyrox.client.DyroxHooks;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoHurtCam: {@code private void bobHurt(CameraRenderState, PoseStack)} tilts the view when hurt. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void dyrox$noHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		if (DyroxHooks.cancelHurtCamera()) ci.cancel();
	}
}

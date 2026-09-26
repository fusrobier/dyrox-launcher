package net.dyrox.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.dyrox.client.DyroxHooks;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Targets (26.3):
 * <ul>
 * <li>{@code private float getMaxZoom(float cameraDist)}: third-person distance, shortened by walls.
 * We change the wanted distance (smooth AntiAim camera, CameraClip) and can skip the wall check.</li>
 * <li>{@code private float calculateFov(float partialTicks)}: Zoom.</li>
 * </ul>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@WrapMethod(method = "getMaxZoom")
	private float dyrox$cameraDistance(float cameraDist, Operation<Float> original) {
		float wanted = DyroxHooks.cameraDistance(cameraDist);
		return DyroxHooks.cameraClipsThroughWalls() ? wanted : original.call(wanted);
	}

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void dyrox$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(DyroxHooks.modifyFov(cir.getReturnValueF()));
	}
}

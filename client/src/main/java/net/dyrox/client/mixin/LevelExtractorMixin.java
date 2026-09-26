package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World gizmos (ESP boxes, tracers): {@code LevelExtractor.extract(...)} runs inside the per-frame
 * gizmo collector and hands the collected gizmos to the renderer in {@code extractGizmos()}. Emitting
 * right before that call draws them in this frame.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(
		method = "extract",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractGizmos()V")
	)
	private void dyrox$gizmos(DeltaTracker deltaTracker, Camera camera, float worldPartialTicks, CallbackInfo ci) {
		DyroxHooks.emitWorldGizmos(worldPartialTicks, camera);
	}
}

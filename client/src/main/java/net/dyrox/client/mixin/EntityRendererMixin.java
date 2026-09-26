package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ESP glow colour: {@code extractRenderState} sets {@code outlineColor} from the team colour. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("RETURN")
	)
	private void dyrox$glowColor(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.outlineColor != 0) state.outlineColor = DyroxHooks.glowColor(entity, state.outlineColor);
	}
}

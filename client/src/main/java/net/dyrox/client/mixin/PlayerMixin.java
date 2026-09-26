package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SafeWalk / Scaffold: {@code protected boolean Player.isStayingOnGroundSurface()} is what sneaking
 * uses to stop at block edges ({@code maybeBackOffFromEdge}). Only changed for the local player.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "isStayingOnGroundSurface", at = @At("RETURN"), cancellable = true)
	private void dyrox$stayOnEdge(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(DyroxHooks.stayOnEdge((Player) (Object) this, cir.getReturnValueZ()));
	}
}

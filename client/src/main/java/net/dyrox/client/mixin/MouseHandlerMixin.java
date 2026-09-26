package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Zoom: slower mouse turning while zoomed. {@code turnPlayer} ends with {@code player.turn(xo, yo)}. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double dyrox$turnX(double xo) {
		return xo * DyroxHooks.mouseSensitivityScale();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double dyrox$turnY(double yo) {
		return yo * DyroxHooks.mouseSensitivityScale();
	}
}

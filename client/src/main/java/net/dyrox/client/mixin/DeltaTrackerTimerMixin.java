package net.dyrox.client.mixin;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.dyrox.client.DyroxHooks;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Timer: {@code DeltaTracker.Timer.advanceGameTime(long)} divides elapsed time by the target
 * milliseconds per tick ({@code targetMsptProvider.apply(msPerTick)}); a shorter tick runs the client faster.
 */
@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
	@Redirect(
		method = "advanceGameTime",
		at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F")
	)
	private float dyrox$timer(FloatUnaryOperator provider, float msPerTick) {
		return provider.apply(msPerTick) / DyroxHooks.gameSpeed();
	}
}

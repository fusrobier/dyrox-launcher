package net.dyrox.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** FastBreak: block-breaking progress (0..1) and the 5-tick delay between broken blocks. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
	@Accessor("destroyProgress")
	float dyrox$getDestroyProgress();

	@Accessor("destroyProgress")
	void dyrox$setDestroyProgress(float progress);

	@Accessor("destroyDelay")
	int dyrox$getDestroyDelay();

	@Accessor("destroyDelay")
	void dyrox$setDestroyDelay(int ticks);
}

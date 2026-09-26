package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AttackEvent: {@code attack(Player, Entity)} sends the attack packet; used by vanilla clicks and modules. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void dyrox$attack(Player player, Entity entity, CallbackInfo ci) {
		DyroxHooks.onAttack(entity);
	}
}

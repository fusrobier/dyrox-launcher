package net.dyrox.client.mixin;

import net.dyrox.client.DyroxHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Velocity: {@code Entity.pushFromExplosion(Vec3)} applies explosion knockback from
 * {@code ClientboundExplodePacket} to the local player.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@ModifyVariable(method = "pushFromExplosion", at = @At("HEAD"), argsOnly = true)
	private Vec3 dyrox$explosionKnockback(Vec3 impulse) {
		return DyroxHooks.explosionKnockback((Entity) (Object) this, impulse);
	}
}

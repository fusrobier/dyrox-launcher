package net.dyrox.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dyrox.client.DyroxHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Velocity: {@code handleSetEntityMotion} applies server knockback via {@code Entity.lerpMotion(Vec3)}
 * on the client thread.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@WrapOperation(
		method = "handleSetEntityMotion",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V")
	)
	private void dyrox$velocity(Entity entity, Vec3 motion, Operation<Void> original) {
		original.call(entity, DyroxHooks.entityVelocity(entity, motion));
	}
}

package net.dyrox.client.combat

import net.dyrox.client.config.NamedChoice
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.AmbientCreature
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.fish.WaterAnimal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player

enum class TargetType(override val choiceName: String) : NamedChoice {
    PLAYERS("Players"),
    HOSTILE("Hostile"),
    PASSIVE("Passive"),
}

/** Shared target classification for combat and render modules. */
object Targets {
    fun typeOf(entity: Entity): TargetType? = when (entity) {
        is Player -> TargetType.PLAYERS
        is Enemy -> TargetType.HOSTILE
        is Animal, is AgeableMob, is WaterAnimal, is AmbientCreature -> TargetType.PASSIVE
        else -> null
    }

    fun isFriend(entity: Entity): Boolean = entity is Player && Friends.isFriend(entity.gameProfile.name)

    /**
     * Whether a combat module may attack [entity]: alive, not us, of an allowed type, not a friend,
     * not a spectator and (unless [invisible]) visible.
     */
    fun isAttackable(entity: Entity, types: Set<TargetType>, invisible: Boolean = true): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (entity === player || entity !is LivingEntity || !entity.isAlive || entity.isDeadOrDying) return false
        if (entity.isSpectator || (!invisible && entity.isInvisible)) return false
        if (entity is Player && (entity.isCreative || isFriend(entity))) return false
        return typeOf(entity) in types
    }
}

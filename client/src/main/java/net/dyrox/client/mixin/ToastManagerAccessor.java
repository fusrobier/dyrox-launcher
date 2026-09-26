package net.dyrox.client.mixin;

import java.util.BitSet;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * HUD array list: vanilla toasts (advancements, recipes, tutorial) occupy 32-pixel slots from the
 * top right; {@code occupiedSlots} (5 bits) says which. The array list moves below them.
 */
@Mixin(ToastManager.class)
public interface ToastManagerAccessor {
	@Accessor("occupiedSlots")
	BitSet dyrox$getOccupiedSlots();
}

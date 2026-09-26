package net.dyrox.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.dyrox.client.DyroxHooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Key presses for module keybinds.
 * Target (26.3): {@code public void KeyboardHandler.keyPress(long handle, int action, KeyEvent event)};
 * {@code event.key()} is an SDL scancode, converted to Minecraft's stable key name here.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"))
	private void dyrox$onKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
		// Same guard as vanilla: only events for the game window.
		if (handle != 0L && handle == minecraft.getWindow().handle()) {
			DyroxHooks.onKey(InputConstants.getKey(event).getName(), action);
		}
	}
}

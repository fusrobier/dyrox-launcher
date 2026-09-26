package net.dyrox.client.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.dyrox.client.DyroxHooks;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet events. {@code Connection} is used by both sides; in singleplayer the integrated server has
 * its own, so only the client's connection (the one receiving CLIENTBOUND packets) is hooked.
 * Targets (26.3):
 * <ul>
 *   <li>{@code public void send(Packet, ChannelFutureListener, boolean)}: every public send overload ends here</li>
 *   <li>{@code protected void channelRead0(ChannelHandlerContext, Packet)}: every received packet, on the Netty thread</li>
 * </ul>
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
	@Shadow
	@Final
	private PacketFlow receiving;

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void dyrox$onSend(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (receiving == PacketFlow.CLIENTBOUND && DyroxHooks.onPacketSend(packet)) {
			ci.cancel();
		}
	}

	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void dyrox$onReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
		if (receiving == PacketFlow.CLIENTBOUND && DyroxHooks.onPacketReceive(packet)) {
			ci.cancel();
		}
	}
}

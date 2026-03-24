package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientEnvironmentEffects;

import java.util.function.Supplier;

public class S2CSetFogPresetPacket {
	private final String preset;
	private final float r, g, b, near, far;

	public S2CSetFogPresetPacket(String preset, float r, float g, float b, float near, float far) {
	    this.preset = preset;
	    this.r = r;
	    this.g = g;
	    this.b = b;
	    this.near = near;
	    this.far = far;
	}

	public static void encode(S2CSetFogPresetPacket msg, FriendlyByteBuf buf) {
	    buf.writeUtf(msg.preset);
	    buf.writeFloat(msg.r);
	    buf.writeFloat(msg.g);
	    buf.writeFloat(msg.b);
	    buf.writeFloat(msg.near);
	    buf.writeFloat(msg.far);
	}

	public static S2CSetFogPresetPacket decode(FriendlyByteBuf buf) {
	    return new S2CSetFogPresetPacket(buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
	}

	public static void handle(S2CSetFogPresetPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
	    NetworkEvent.Context context = contextSupplier.get();
	    context.enqueueWork(() -> {
	        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
	            ClientEnvironmentEffects.setFogPreset(msg.preset, msg.r, msg.g, msg.b, msg.near, msg.far);
	        });
	    });
	    context.setPacketHandled(true);
	}
}
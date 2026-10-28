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
	private final boolean outdoorsOnly;
    private net.minecraft.nbt.CompoundTag options;

	public S2CSetFogPresetPacket(String preset, float r, float g, float b, float near, float far) {
	    this(preset, r, g, b, near, far, false);
	}

	public S2CSetFogPresetPacket(String preset, float r, float g, float b, float near, float far, boolean outdoorsOnly) {
	    this.preset = preset;
	    this.r = r;
	    this.g = g;
	    this.b = b;
	    this.near = near;
	    this.far = far;
	    this.outdoorsOnly = outdoorsOnly;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        options=server!=null&&server.isSameThread()?me.cryo.zombierool.config.WorldConfig.get(server.overworld()).getFogOptions():new net.minecraft.nbt.CompoundTag();
	}

	public static void encode(S2CSetFogPresetPacket msg, FriendlyByteBuf buf) {
	    buf.writeUtf(msg.preset);
	    buf.writeFloat(msg.r);
	    buf.writeFloat(msg.g);
	    buf.writeFloat(msg.b);
	    buf.writeFloat(msg.near);
	    buf.writeFloat(msg.far);
	    buf.writeBoolean(msg.outdoorsOnly);
        buf.writeNbt(msg.options);
	}

	public static S2CSetFogPresetPacket decode(FriendlyByteBuf buf) {
        var packet=new S2CSetFogPresetPacket(buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
        packet.options=buf.readNbt();return packet;
	}

	public static void handle(S2CSetFogPresetPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
	    NetworkEvent.Context context = contextSupplier.get();
	    context.enqueueWork(() -> {
	        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
	            ClientEnvironmentEffects.setFogPreset(msg.preset, msg.r, msg.g, msg.b, msg.near, msg.far, msg.outdoorsOnly);
                ClientEnvironmentEffects.setFogOptions(msg.options);
	        });
	    });
	    context.setPacketHandled(true);
	}
}
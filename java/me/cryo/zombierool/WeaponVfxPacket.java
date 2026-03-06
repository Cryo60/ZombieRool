package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import me.cryo.zombierool.client.ClientWeaponVfxHandler;

public class WeaponVfxPacket {
    private final String vfxType;
    private final double sx, sy, sz;
    private final double ex, ey, ez;
    private final boolean isPap;
    private final boolean hitHeadshot;

    public WeaponVfxPacket(String vfxType, Vec3 start, Vec3 end, boolean isPap, boolean hitHeadshot) {
        this.vfxType = vfxType;
        this.sx = start.x; this.sy = start.y; this.sz = start.z;
        this.ex = end.x;   this.ey = end.y;   this.ez = end.z;
        this.isPap = isPap;
        this.hitHeadshot = hitHeadshot;
    }

    public static void encode(WeaponVfxPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.vfxType);
        buf.writeDouble(msg.sx); buf.writeDouble(msg.sy); buf.writeDouble(msg.sz);
        buf.writeDouble(msg.ex); buf.writeDouble(msg.ey); buf.writeDouble(msg.ez);
        buf.writeBoolean(msg.isPap);
        buf.writeBoolean(msg.hitHeadshot);
    }

    public static WeaponVfxPacket decode(FriendlyByteBuf buf) {
        return new WeaponVfxPacket(
            buf.readUtf(),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    public static void handle(WeaponVfxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientWeaponVfxHandler.handleVfx(msg.vfxType, new Vec3(msg.sx, msg.sy, msg.sz), new Vec3(msg.ex, msg.ey, msg.ez), msg.isPap, msg.hitHeadshot);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.core.system.OverlaySystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CUpdateOverlayPacket {
    private final BlockPos pos;
    private final Direction face;
    private final String texturePath;
    private final int rotation;
    private final boolean add;

    public S2CUpdateOverlayPacket(BlockPos pos, Direction face, String texturePath, int rotation, boolean add) {
        this.pos = pos;
        this.face = face;
        this.texturePath = texturePath;
        this.rotation = rotation;
        this.add = add;
    }

    public S2CUpdateOverlayPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.face = buf.readEnum(Direction.class);
        this.texturePath = buf.readUtf();
        this.rotation = buf.readInt();
        this.add = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(face);
        buf.writeUtf(texturePath);
        buf.writeInt(rotation);
        buf.writeBoolean(add);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (add) {
                    OverlaySystem.addOverlay(pos, face, texturePath, rotation);
                } else {
                    OverlaySystem.removeOverlay(pos, face);
                }
            });

            if (ctx.get().getDirection().getReceptionSide().isServer()) {
                var sender = ctx.get().getSender();
                if (sender != null && sender.level() instanceof ServerLevel serverLevel) {
                    WorldConfig config = WorldConfig.get(serverLevel);
                    String key = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + face.getName();
                    if (add) {
                        config.addMapOverlay(key, texturePath + ";" + rotation);
                    } else {
                        config.removeMapOverlay(key);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
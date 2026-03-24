package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlockEntity;
import net.minecraft.client.Minecraft;

public class S2CSyncMysteryBoxStatePacket {
    private final BlockPos pos;
    private final int state;
    private final int timer;
    private final ItemStack finalWeapon;
    private final boolean isTeddy;

    public S2CSyncMysteryBoxStatePacket(BlockPos pos, int state, int timer, ItemStack finalWeapon, boolean isTeddy) {
        this.pos = pos;
        this.state = state;
        this.timer = timer;
        this.finalWeapon = finalWeapon;
        this.isTeddy = isTeddy;
    }

    public S2CSyncMysteryBoxStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.state = buf.readInt();
        this.timer = buf.readInt();
        this.finalWeapon = buf.readItem();
        this.isTeddy = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(state);
        buf.writeInt(timer);
        buf.writeItem(finalWeapon);
        buf.writeBoolean(isTeddy);
    }
    
    public static S2CSyncMysteryBoxStatePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncMysteryBoxStatePacket(buf);
    }

    public static void handle(S2CSyncMysteryBoxStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().level != null) {
                    net.minecraft.world.level.block.entity.BlockEntity be = Minecraft.getInstance().level.getBlockEntity(msg.pos);
                    if (be instanceof MysteryBoxBlockEntity box) {
                        box.syncStateFromClient(msg.state, msg.timer, msg.finalWeapon, msg.isTeddy);
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
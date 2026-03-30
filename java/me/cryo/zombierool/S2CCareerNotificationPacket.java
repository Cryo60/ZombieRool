package me.cryo.zombierool.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CCareerNotificationPacket {
    private final int amount;
    private final String reasonKey;

    public S2CCareerNotificationPacket(int amount, String reasonKey) {
        this.amount = amount;
        this.reasonKey = reasonKey;
    }

    public S2CCareerNotificationPacket(FriendlyByteBuf buf) {
        this.amount = buf.readInt();
        this.reasonKey = buf.readUtf();
    }

    public static void encode(S2CCareerNotificationPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.amount);
        buf.writeUtf(msg.reasonKey);
    }

    public static S2CCareerNotificationPacket decode(FriendlyByteBuf buf) {
        return new S2CCareerNotificationPacket(buf);
    }

    public static void handle(S2CCareerNotificationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable(msg.reasonKey, msg.amount).withStyle(net.minecraft.ChatFormatting.GOLD), true);
                    mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.5f, 2.0f);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
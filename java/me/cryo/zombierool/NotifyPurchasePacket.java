package me.cryo.zombierool.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.renderer.BuyWallWeaponRenderer;

import java.util.function.Supplier;

public class NotifyPurchasePacket {
    private final String weaponId;

    public NotifyPurchasePacket(String weaponId) {
        this.weaponId = weaponId;
    }

    public NotifyPurchasePacket(FriendlyByteBuf buf) {
        this.weaponId = buf.readUtf();
    }

    public static void encode(NotifyPurchasePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.weaponId);
    }

    public static NotifyPurchasePacket decode(FriendlyByteBuf buf) {
        return new NotifyPurchasePacket(buf);
    }

    public static void handle(NotifyPurchasePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                BuyWallWeaponRenderer.markAsPurchased(player, pkt.weaponId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
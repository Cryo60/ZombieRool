package me.cryo.zombierool.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.renderer.BuyWallWeaponRenderer;

import java.util.function.Supplier;

/**
 * Paquet envoyé du serveur vers le client pour notifier qu'un item a été acheté
 */
public class NotifyPurchasePacket {
    private final ResourceLocation itemRL;

    public NotifyPurchasePacket(ResourceLocation itemRL) {
        this.itemRL = itemRL;
    }

    public NotifyPurchasePacket(FriendlyByteBuf buf) {
        this.itemRL = buf.readResourceLocation();
    }

    public static void encode(NotifyPurchasePacket pkt, FriendlyByteBuf buf) {
        buf.writeResourceLocation(pkt.itemRL);
    }

    public static NotifyPurchasePacket decode(FriendlyByteBuf buf) {
        return new NotifyPurchasePacket(buf);
    }

    public static void handle(NotifyPurchasePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Côté CLIENT uniquement
            var player = Minecraft.getInstance().player;
            if (player != null) {
                BuyWallWeaponRenderer.markAsPurchased(player, pkt.itemRL);
            }
        });
        ctx.setPacketHandled(true);
    }
}
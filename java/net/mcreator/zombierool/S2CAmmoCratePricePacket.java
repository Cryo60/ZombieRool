package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import net.mcreator.zombierool.handlers.KeyInputHandler;

import java.util.function.Supplier;

/**
 * Packet envoyé du serveur au client pour mettre à jour le prix de l'AmmoCrate
 */
public class S2CAmmoCratePricePacket {
    
    private final int price;
    private final boolean canPurchase;
    private final String hudMessage;
    
    public S2CAmmoCratePricePacket(int price, boolean canPurchase, String hudMessage) {
        this.price = price;
        this.canPurchase = canPurchase;
        this.hudMessage = hudMessage;
    }
    
    public S2CAmmoCratePricePacket(FriendlyByteBuf buf) {
        this.price = buf.readInt();
        this.canPurchase = buf.readBoolean();
        this.hudMessage = buf.readUtf();
    }
    
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(price);
        buf.writeBoolean(canPurchase);
        buf.writeUtf(hudMessage);
    }
    
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Met à jour les informations côté client
            KeyInputHandler.updateAmmoCrateInfo(price, canPurchase, hudMessage);
        });
        return true;
    }
}
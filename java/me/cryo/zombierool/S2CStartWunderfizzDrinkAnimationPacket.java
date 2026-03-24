package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CStartWunderfizzDrinkAnimationPacket {
    private final String perkId;

    public S2CStartWunderfizzDrinkAnimationPacket(String perkId) {
        this.perkId = perkId;
    }

    public S2CStartWunderfizzDrinkAnimationPacket(FriendlyByteBuf buf) {
        this.perkId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(perkId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Côté client : lancer l'animation
            me.cryo.zombierool.client.DrinkPerkAnimationHandler.startAnimation(() -> {
                // L'animation est terminée, on ne fait rien de spécial
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
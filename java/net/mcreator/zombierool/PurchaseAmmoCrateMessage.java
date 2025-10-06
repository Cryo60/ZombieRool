package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import net.mcreator.zombierool.AmmoCrateManager;
import net.mcreator.zombierool.WaveManager;

import java.util.function.Supplier;

public class PurchaseAmmoCrateMessage {

    public PurchaseAmmoCrateMessage() {
    }

    public PurchaseAmmoCrateMessage(FriendlyByteBuf buffer) {
        // Pas de données à lire pour ce packet simple
    }

    public void encode(FriendlyByteBuf buffer) {
        // Pas de données à écrire pour ce packet simple
    }

    public void handler(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            AmmoCrateManager manager = AmmoCrateManager.get(level);
            int currentWave = WaveManager.getCurrentWave();

            // Tente l'achat et récupère le résultat
            boolean purchaseSuccessful = manager.tryPurchaseAmmo(player, level, currentWave);
            
            // Envoie les informations mises à jour au client SEULEMENT si l'achat a réussi
            // Sinon le prix reste inchangé côté client
            if (purchaseSuccessful) {
                manager.sendPriceInfoToClient(player, currentWave);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
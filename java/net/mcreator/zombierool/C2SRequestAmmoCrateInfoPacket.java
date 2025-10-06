package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import net.mcreator.zombierool.AmmoCrateManager;
import net.mcreator.zombierool.WaveManager;

import java.util.function.Supplier;

/**
 * Packet envoyé du client au serveur pour demander les infos actuelles de l'AmmoCrate
 */
public class C2SRequestAmmoCrateInfoPacket {

    public C2SRequestAmmoCrateInfoPacket() {
    }

    public C2SRequestAmmoCrateInfoPacket(FriendlyByteBuf buffer) {
        // Pas de données à lire
    }

    public void encode(FriendlyByteBuf buffer) {
        // Pas de données à écrire
    }

    public void handler(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            AmmoCrateManager manager = AmmoCrateManager.get(level);
            int currentWave = WaveManager.getCurrentWave();

            // Envoie les informations actuelles au client
            manager.sendPriceInfoToClient(player, currentWave);
        });
        ctx.get().setPacketHandled(true);
    }
}
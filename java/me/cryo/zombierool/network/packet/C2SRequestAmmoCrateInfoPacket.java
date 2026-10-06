package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import me.cryo.zombierool.gameplay.AmmoCrateManager;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.network.Packets;

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

    public static void handle(C2SRequestAmmoCrateInfoPacket msg, Supplier<NetworkEvent.Context> ctx) {
        msg.handler(ctx);
    }

    public void handler(Supplier<NetworkEvent.Context> ctx) {
        Packets.onServer(ctx, player -> {
            AmmoCrateManager manager = AmmoCrateManager.get(player.serverLevel());
            manager.sendPriceInfoToClient(player, WaveManager.getCurrentWave());
        });
    }
}
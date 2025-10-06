package net.mcreator.zombierool.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent

import net.mcreator.zombierool.handlers.KeyInputHandler;
import net.mcreator.zombierool.client.ClientPlayerDownSoundManager;

import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class PlayerDownPacket {
    private final boolean isDown;
    private final UUID playerUUID; // L'UUID du joueur dont l'état change

    // Constructeur unique, exigeant l'UUID du joueur.
    public PlayerDownPacket(boolean isDown, UUID playerUUID) {
        this.isDown = isDown;
        this.playerUUID = playerUUID;
    }

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    public static void encode(PlayerDownPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isDown);
        buf.writeUUID(msg.playerUUID); // Toujours écrire l'UUID fourni par le message
    }

    public static PlayerDownPacket decode(FriendlyByteBuf buf) {
        boolean isDown = buf.readBoolean();
        UUID playerUUID = buf.readUUID();
        return new PlayerDownPacket(isDown, playerUUID);
    }

    public static void handle(PlayerDownPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    // Si le paquet concerne le joueur local
                    if (mc.player.getUUID().equals(msg.playerUUID)) {
                        KeyInputHandler.isLocalPlayerDown = msg.isDown; // Met à jour l'état client-side local

                        if (msg.isDown) {
                            mc.player.sendSystemMessage(getTranslatedComponent(
                                "§cVous êtes tombé au combat ! Attendez qu'un allié vous réanime.",
                                "§cYou are down! Wait for an ally to revive you."
                            ));
                            ClientPlayerDownSoundManager.startLastStandSound();
                        } else {
                            mc.player.sendSystemMessage(getTranslatedComponent(
                                "§aVous n'êtes plus en état de combat.",
                                "§aYou are no longer down."
                            ));
                            ClientPlayerDownSoundManager.stopLastStandSound();
                        }
                    }

                    // Mise à jour du Set downPlayers pour TOUS les joueurs (y compris le joueur local si le paquet le concerne)
                    if (msg.isDown) {
                        KeyInputHandler.downPlayers.add(msg.playerUUID);
                    } else {
                        KeyInputHandler.downPlayers.remove(msg.playerUUID);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        // Enregistrement du paquet dans NetworkHandler
    }
}

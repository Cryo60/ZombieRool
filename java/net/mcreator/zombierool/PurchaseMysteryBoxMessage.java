package net.mcreator.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.MysteryBoxManager; // Assurez-vous que l'importation est correcte
import net.mcreator.zombierool.init.ZombieroolModBlocks;
import net.mcreator.zombierool.block.MysteryBoxBlock;
import net.mcreator.zombierool.block.EmptymysteryboxBlock;
import net.mcreator.zombierool.item.IngotSaleItem;
import net.minecraft.server.level.ServerLevel; // Import pour ServerLevel

import java.util.function.Supplier;

public class PurchaseMysteryBoxMessage {
    private final BlockPos pos;

    public PurchaseMysteryBoxMessage(BlockPos pos) {
        this.pos = pos;
    }

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient(ServerPlayer player) {
        // For server-side, we can't directly access client language settings.
        // This is a placeholder. In a real scenario, you'd need client-server sync
        // to pass the player's language preference to the server.
        // For this example, we'll assume English if a player context is available.
        return true; 
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(ServerPlayer player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    public static void encode(PurchaseMysteryBoxMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PurchaseMysteryBoxMessage decode(FriendlyByteBuf buf) {
        return new PurchaseMysteryBoxMessage(buf.readBlockPos());
    }

    public static void handle(PurchaseMysteryBoxMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            // CAST: Convertir Level en ServerLevel car MysteryBoxManager.get() l'attend probablement
            ServerLevel level = (ServerLevel) sender.level();
            BlockPos interactedBoxPos = msg.pos;
            BlockState state = level.getBlockState(interactedBoxPos);

            MysteryBoxManager manager = MysteryBoxManager.get(level); // Utilise maintenant ServerLevel

            // Vérifier si la boîte est en mouvement OU en jingle avant de permettre l'interaction
            if (manager.isMysteryBoxMoving) {
                sender.sendSystemMessage(getTranslatedComponent(sender, "§cLa Mystery Box est en train de se déplacer... attendez !", "§cThe Mystery Box is moving... wait!").withStyle(ChatFormatting.RED));
                return;
            }
            if (manager.isAwaitingWeapon) { // Si elle est en jingle, bloquer aussi
                sender.sendSystemMessage(getTranslatedComponent(sender, "§cLa Mystery Box prépare déjà votre arme... un instant !", "§cThe Mystery Box is already preparing your weapon... just a moment!").withStyle(ChatFormatting.YELLOW));
                return;
            }

            if (!(state.getBlock() instanceof MysteryBoxBlock) || state.getValue(MysteryBoxBlock.PART)) {
                sender.sendSystemMessage(getTranslatedComponent(sender, "§cCe n'est pas une Mystery Box active ou c'est la mauvaise partie !", "§cThis is not an active Mystery Box or it's the wrong part!").withStyle(ChatFormatting.RED));
                return;
            }

            int cost = 950;
            
            // --- LOGIQUE DE PAIEMENT ---
            boolean hasIngot = sender.getInventory().items.stream()
                .anyMatch(s -> s.getItem() instanceof IngotSaleItem);

            if (hasIngot) {
                // Le joueur a un lingot, on procède au paiement par lingot.
                // Assurez-vous que la méthode startMysteryBoxInteraction dans MysteryBoxManager
                // accepte bien (ServerLevel level, ServerPlayer player, boolean useIngot)
                manager.startMysteryBoxInteraction(level, sender, true); // true = paiement par lingot
            } else {
                // Pas de lingot, on vérifie les points
                if (PointManager.getScore(sender) < cost) {
                    sender.sendSystemMessage(getTranslatedComponent(sender, "§cPas assez de points ! (" + cost + " points requis)", "§cNot enough points! (" + cost + " points required)").withStyle(ChatFormatting.RED));
                    return;
                }
                // Le joueur a assez de points, on procède au paiement par points.
                manager.startMysteryBoxInteraction(level, sender, false); // false = paiement par points
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

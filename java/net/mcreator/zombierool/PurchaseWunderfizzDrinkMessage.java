package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack; // Import for ItemStack
import net.minecraft.world.item.Item; // Import for Item
import net.minecraft.world.entity.player.Player; // Import for Player

import net.mcreator.zombierool.WunderfizzManager;
import net.mcreator.zombierool.init.ZombieroolModItems; // Assuming your IngotSaleItem is here

import java.util.function.Supplier;

public class PurchaseWunderfizzDrinkMessage {
    private final BlockPos pos;

    public PurchaseWunderfizzDrinkMessage(BlockPos pos) {
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

    public static void encode(PurchaseWunderfizzDrinkMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PurchaseWunderfizzDrinkMessage decode(FriendlyByteBuf buf) {
        return new PurchaseWunderfizzDrinkMessage(buf.readBlockPos());
    }

    public static void handler(PurchaseWunderfizzDrinkMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !(sender.level() instanceof ServerLevel level)) {
                return;
            }

            WunderfizzManager manager = WunderfizzManager.get(level);
            BlockPos activeWunderfizzPos = manager.getCurrentActiveWunderfizzLocation();

            System.out.println("Server Purchase Packet: Received request for " + msg.pos + ". Server active Wunderfizz: " + activeWunderfizzPos); // DEBUG PRINT

            // Normalize the client-sent position for comparison (Y is set to 0)
            BlockPos clientClickedPosNormalized = new BlockPos(msg.pos.getX(), 0, msg.pos.getZ());

            // Normalize the server's active Wunderfizz position for comparison (Y should already be 0, but for robustness)
            BlockPos activeWunderfizzPosNormalized = null;
            if (activeWunderfizzPos != null) {
                activeWunderfizzPosNormalized = new BlockPos(activeWunderfizzPos.getX(), 0, activeWunderfizzPos.getZ());
            }

            // Compare normalized positions
            if (activeWunderfizzPosNormalized == null || !activeWunderfizzPosNormalized.equals(clientClickedPosNormalized)) {
                sender.displayClientMessage(getTranslatedComponent(sender, "§cLa machine Wunderfizz n'est pas active ici, ou n'est pas alimentée !", "§cThe Wunderfizz machine is not active here, or is not powered!").withStyle(ChatFormatting.RED), true);
                return;
            }

            int cost = 1500;
            boolean purchasedWithIngot = false;

            // Check if the player has the IngotSaleItem in their inventory
            // We iterate through the inventory to find and consume the item
            for (int i = 0; i < sender.getInventory().getContainerSize(); i++) {
                ItemStack stack = sender.getInventory().getItem(i);
                // Ensure ZombieroolModItems.INGOT_SALE_ITEM.get() correctly references your item
                if (stack.getItem() == ZombieroolModItems.INGOT_SALE.get()) {
                    stack.shrink(1); // Consume one IngotSaleItem
                    sender.displayClientMessage(getTranslatedComponent(sender, "§aVous avez acheté une boisson Wunderfizz avec un Lingot de Vente !", "§aYou purchased a Wunderfizz drink with a Sale Ingot!").withStyle(ChatFormatting.GREEN), true);
                    purchasedWithIngot = true;
                    break; // Exit loop after consuming one ingot
                }
            }

            // If the purchase was not made with an ingot, proceed with the regular cost
            if (!purchasedWithIngot) {
                manager.purchaseWunderfizzDrink(sender, cost);
            }

        });
        ctx.get().setPacketHandled(true);
    }
}

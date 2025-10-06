package net.mcreator.zombierool.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;
import net.mcreator.zombierool.item.IngotSaleItem;
import net.minecraft.world.item.Item;
import net.mcreator.zombierool.api.IPackAPunchable;

public class PurchaseWallWeaponPacket {
    private final BlockPos pos;

    // Decode constructor
    public PurchaseWallWeaponPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    // Encode constructor
    public PurchaseWallWeaponPacket(BlockPos pos) {
        this.pos = pos;
    }

    // Helper method to check if the player's client language is English
    private static boolean isEnglishClient(ServerPlayer player) {
        // This is a simplified check. In a real scenario, you might need to get the player's client language setting.
        // For server-side packets, the server doesn't directly know client language.
        // A common approach is to send the client language to the server, or use a default.
        // For this example, we'll assume English for server-side messages for now, or implement client-side language sync.
        return true; // Placeholder: Assume English for server-side messages for now.
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(ServerPlayer player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    public static void encode(PurchaseWallWeaponPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
    }

    public static PurchaseWallWeaponPacket decode(FriendlyByteBuf buf) {
        return new PurchaseWallWeaponPacket(buf);
    }

    public static void handle(PurchaseWallWeaponPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
    
            Level world = player.level();
            BlockEntity te = world.getBlockEntity(pkt.pos);
            if (!(te instanceof BuyWallWeaponBlockEntity be)) return;
    
            // Prix de base
            int basePrice = be.getPrice();
            int price = basePrice;
    
            // Récupère l'item à vendre
            ResourceLocation itemRl = be.getItemToSell();
            if (itemRl == null) return;
            Item item = ForgeRegistries.ITEMS.getValue(itemRl);
            if (item == null) return;
    
            // Solde et ingot
            int balance = PointManager.getScore(player);
            boolean hasIngot = player.getInventory().items.stream()
                .anyMatch(s -> s.getItem() instanceof IngotSaleItem);
    
            // Cherche si le joueur a déjà cette arme
            ItemStack existing = player.getInventory().items.stream()
                .filter(s -> s.getItem() == item)
                .findFirst()
                .orElse(ItemStack.EMPTY);
    
            if (!existing.isEmpty() && item instanceof IReloadable reloadable) {
                //
                // —— RECHARGEMENT ——
                //
                // Prix moitié, plus 5000 si déjà Pack-a-Punch
                int halfPrice = Math.max(1, price / 2);
                if (existing.getItem() instanceof IPackAPunchable pap
                        && pap.isPackAPunched(existing)) {
                    halfPrice += 5000;
                }
    
                // Consomme un ingot si possible, sinon points
                if (hasIngot) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.getItem() instanceof IngotSaleItem) {
                            stack.shrink(1);
                            break;
                        }
                    }
                } else if (balance < halfPrice) {
                    player.sendSystemMessage(getTranslatedComponent(player,
                        "§cPas assez de points pour recharger (" +
                        balance + " / " + halfPrice + ")",
                        "§cNot enough points to reload (" +
                        balance + " / " + halfPrice + ")"
                    ));
                    return;
                } else {
                    PointManager.modifyScore(player, -halfPrice);
                }
    
                // Réinitialise les munitions
                reloadable.initializeIfNeeded(existing);
                reloadable.setAmmo(existing, reloadable.getMaxAmmo());
                reloadable.setReserve(existing, reloadable.getMaxReserve());
    
                // Son et message
                player.level().playSound(
                    null, pkt.pos,
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")),
                    SoundSource.PLAYERS, 1f, 1f
                );
                player.sendSystemMessage(getTranslatedComponent(player,
                    "§aRechargé : " + existing.getHoverName().getString() +
                    (hasIngot ? " pour 1 ingot" : " pour " + halfPrice + " points"),
                    "§aReloaded: " + existing.getHoverName().getString() +
                    (hasIngot ? " for 1 ingot" : " for " + halfPrice + " points")
                ));
    
            } else {
                //
                // —— ACHAT NOUVELLE ARME ——
                //
                // Si le joueur possède déjà une version packée, ajuste le prix
                if (item instanceof IPackAPunchable pap) {
                    boolean invUpgraded = player.getInventory().items.stream()
                        .anyMatch(s -> s.getItem() == item && pap.isPackAPunched(s));
                    if (invUpgraded) {
                        price = 5000 + basePrice / 2;
                    }
                }
    
                // Consomme un ingot si possible, sinon points
                if (hasIngot) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.getItem() instanceof IngotSaleItem) {
                            stack.shrink(1);
                            break;
                        }
                    }
                } else if (balance < price) {
                    player.sendSystemMessage(getTranslatedComponent(player,
                        "§cPas assez de points (" + balance + " / " + price + ")",
                        "§cNot enough points (" + balance + " / " + price + ")"
                    ));
                    return;
                } else {
                    PointManager.modifyScore(player, -price);
                }
    
                // Donne l'arme
                ItemStack stack = new ItemStack(item, 1);
                if (stack.getItem() instanceof IReloadable r) {
                    r.initializeIfNeeded(stack);
                }
                boolean added = player.getInventory().add(stack);
                if (!added) {
                    player.drop(stack, false);
                }
    
                // Son et message
                player.level().playSound(
                    null, pkt.pos,
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")),
                    SoundSource.PLAYERS, 1f, 1f
                );
                player.sendSystemMessage(getTranslatedComponent(player,
                    "§aAcheté : " + stack.getHoverName().getString() +
                    (hasIngot ? " pour 1 ingot" : " pour " + price + " points"),
                    "§aPurchased: " + stack.getHoverName().getString() +
                    (hasIngot ? " for 1 ingot" : " for " + price + " points")
                ));
            }
        });
        ctx.setPacketHandled(true);
    }
}

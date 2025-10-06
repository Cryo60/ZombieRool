package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import net.mcreator.zombierool.block.PerksLowerBlock;
import net.mcreator.zombierool.block.entity.PerksLowerBlockEntity;
import net.mcreator.zombierool.item.IngotSaleItem;
import net.mcreator.zombierool.PerksManager;
import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.init.ZombieroolModSounds; 

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance; 
import net.mcreator.zombierool.potion.PerksEffectSpeedColaMobEffect; // Keep this import
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player; // Import for Player

import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PurchasePerkMessage {
    private final BlockPos pos;
    private static final RandomSource RANDOM = RandomSource.create();

    public PurchasePerkMessage(BlockPos pos) {
        this.pos = pos;
    }

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient(Player player) {
        // For server-side, we can't directly access client language settings.
        // This is a placeholder. In a real scenario, you'd need client-server sync
        // to pass the player's language preference to the server.
        // For this example, we'll assume English if a player context is available.
        return true; 
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    public static void encode(PurchasePerkMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PurchasePerkMessage decode(FriendlyByteBuf buf) {
        return new PurchasePerkMessage(buf.readBlockPos());
    }

    public static void handler(PurchasePerkMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !(player.level() instanceof ServerLevel level))
                return;

            BlockEntity be = level.getBlockEntity(msg.pos);
            if (!(be instanceof PerksLowerBlockEntity perksBE))
                return;

            BlockState state = level.getBlockState(msg.pos);
            if (!state.getValue(PerksLowerBlock.POWERED))
                return;

            String perkId = perksBE.getSavedPerkId();
            PerksManager.Perk perkToPurchase = PerksManager.ALL_PERKS.get(perkId);

            if (perkToPurchase == null)
                return;

            // Check if the player already has the perk
            if (player.hasEffect(perkToPurchase.getAssociatedEffect())) {
                player.displayClientMessage(getTranslatedComponent(player, "§cVous avez déjà cette perk !", "§cYou already have this perk!"), true);
                return;
            }

            // Check the general perk limit
            int currentPerkCount = PerksManager.getPerkCount(player);
            if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
                player.displayClientMessage(getTranslatedComponent(player, "§cVous avez atteint la limite de " + PerksManager.MAX_PERKS_LIMIT + " perks !", "§cYou have reached the limit of " + PerksManager.MAX_PERKS_LIMIT + " perks!"),
                        true);
                return;
            }

            // New: Check specific perk limits
            if (PerksManager.isPerkLimited(perkId, player)) {
                int currentPerkPurchases = PerksManager.getCurrentPerkPurchases(perkId, player);
                int perkLimit = PerksManager.getPerkLimit(perkId, player);

                if (currentPerkPurchases >= perkLimit) {
                    String perkName = perkToPurchase.getName();
                    player.displayClientMessage(getTranslatedComponent(player, "§cVous avez atteint la limite de " + perkLimit + " achats pour " + perkName + " !", "§cYou have reached the limit of " + perkLimit + " purchases for " + perkName + "!"), true);
                    return;
                }
            }

            int price = perksBE.getSavedPrice();
            int balance = PointManager.getScore(player);
            boolean hasIngot = player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof IngotSaleItem);

            if (!hasIngot && balance < price) {
                player.displayClientMessage(getTranslatedComponent(player, "§cVous n'avez pas assez de points ou de lingots !", "§cYou don't have enough points or ingots!"), true);
                return;
            }

            // Payment
            String paymentMessage;
            if (hasIngot) {
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() instanceof IngotSaleItem) {
                        stack.shrink(1);
                        break;
                    }
                }
                paymentMessage = getTranslatedComponent(player, " (1 lingot)", " (1 ingot)").getString();
            } else {
                PointManager.modifyScore(player, -price);
                paymentMessage = getTranslatedComponent(player, " (" + price + " points)", " (" + price + " points)").getString();
            }

            // Declare speedColaAmplifier and dejaVuProcced outside the commented block
            int speedColaAmplifier = 3; // Default amplifier for Speed Cola (0.20D boost)
            boolean dejaVuProcced = false; // Will remain false as the logic is commented out

            // --- Deja Vu Easter Egg Logic (Commented Out) ---
            /*
            // For Speed Cola, amplifier 3 gives 0.05D * (3 + 1) = 0.20D
            // For Deja Vu, amplifier 4 gives 0.05D * (4 + 1) = 0.25D
            if ("speed_cola".equals(perkId)) {
                if (RANDOM.nextInt(20) == 0) { // 1 in 20 chance (5%) for Deja Vu
                    speedColaAmplifier = 4; // Set amplifier for enhanced speed (0.25D boost)
                    dejaVuProcced = true;
                    level.playSound(
                        null,
                        player.getX(), player.getY(), player.getZ(),
                        ZombieroolModSounds.DEJA_VU.get(), // Play Deja Vu sound
                        SoundSource.PLAYERS,
                        1.0F, // Volume
                        1.0F + (RANDOM.nextFloat() * 0.2F - 0.1F) // Slight pitch variation
                    );
                    player.displayClientMessage(getTranslatedComponent(player, "§bVous avez une sensation de déjà vu...", "§bYou have a sense of déjà vu..."), true);
                }
            }
            */
            // --- End Deja Vu Easter Egg Logic ---

            // Apply the perk
            // Use the calculated amplifier for Speed Cola, otherwise use existing applyEffect for other perks.
            if (perkId.equals("speed_cola")) {
                 player.addEffect(new MobEffectInstance(perkToPurchase.getAssociatedEffect(), Integer.MAX_VALUE, speedColaAmplifier, false, false, true));
            } else {
                 perkToPurchase.applyEffect(player); // Use existing applyEffect for other perks
            }

            // New: Increment the specific perk purchase count
            PerksManager.incrementPerkPurchases(perkId, player);

            MutableComponent finalMessage = getTranslatedComponent(player, "§aAtout activé : ", "§aPerk activated: ")
                .append(perkToPurchase.getName())
                .append(paymentMessage);
            // Comment out this line as dejaVuProcced will always be false
            // if (dejaVuProcced) {
            //     finalMessage.append(getTranslatedComponent(player, " §e(Vitesse augmentée !)", " §e(Speed increased!)"));
            // }
            player.displayClientMessage(finalMessage, true);
            level.playSound(null, msg.pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:buy")), SoundSource.BLOCKS, 1f,
                    1f);
        });
        ctx.get().setPacketHandled(true);
    }
}

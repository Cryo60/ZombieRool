package net.mcreator.zombierool.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.zombierool.api.IPackAPunchable;
import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.block.PunchPackBlock;
import net.mcreator.zombierool.item.IngotSaleItem;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PackAPunchManager {

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

    // État global
    private static boolean isInUse = false;
    private static UUID currentUser = null;
    private static ItemStack upgradingStack = ItemStack.EMPTY;
    private static long startTick = 0;
    private static BlockPos currentBlock = null;

    public static void tryUsePack(Player player, Level level, BlockPos pos) {
        if (isInUse) {
            player.displayClientMessage(getTranslatedComponent(player, "Quelqu'un utilise déjà la machine.", "Someone is already using the machine."), true);
            return;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof PunchPackBlock)) return;
        if (!level.getBlockState(pos).getValue(PunchPackBlock.POWERED)) {
            player.displayClientMessage(getTranslatedComponent(player, "Vous devez activer le courant pour améliorer votre arme !", "You must activate the power to upgrade your weapon!"), true);
            return;
        }

        // On recherche un ingot dans l'inventaire
        boolean hasIngot = player.getInventory().items.stream()
            .anyMatch(s -> s.getItem() instanceof IngotSaleItem);

        // Si pas d'ingot, on vérifie le score
        if (!hasIngot && PointManager.getScore(player) < 5000) {
            player.displayClientMessage(getTranslatedComponent(player, "Vous n'avez pas assez de points.", "You don't have enough points."), true);
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.displayClientMessage(getTranslatedComponent(player, "Vous devez tenir une arme !", "You must hold a weapon!"), true);
            return;
        }

        // --- Refus si non pack-a-punchable ou déjà packée ---
        if (!(held.getItem() instanceof IPackAPunchable up)) {
            player.displayClientMessage(getTranslatedComponent(player, "Cette arme ne peut pas être améliorée.", "This weapon cannot be upgraded."), true);
            return;
        }
        if (!up.canBePackAPunched(held)) {
            player.displayClientMessage(getTranslatedComponent(player, "Votre arme est déjà pack-a-punchée.", "Your weapon is already Pack-a-Punched."), true);
            return;
        }

        // On retire soit 1 ingot, soit 5000 points
        if (hasIngot) {
            // Consommation d'un ingot
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof IngotSaleItem) {
                    stack.shrink(1);
                    break;
                }
            }
            player.displayClientMessage(getTranslatedComponent(player, "Un ingot a été consommé pour l’amélioration.", "An ingot has been consumed for the upgrade."), true);
        } else {
            PointManager.modifyScore(player, -5000);
            player.displayClientMessage(getTranslatedComponent(player, "5000 points ont été retirés pour l’amélioration.", "5000 points have been deducted for the upgrade."), true);
        }

        // On stocke l’état d’amélioration
        upgradingStack = held.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        isInUse = true;
        currentUser = player.getUUID();
        currentBlock = pos;
        startTick = level.getGameTime();  // GameTime du niveau

        // Feedback initial
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 1f);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.ENCHANT,
                pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5,
                30, .3, .5, .3, .05
            );
        }
        player.displayClientMessage(getTranslatedComponent(player, "Amélioration en cours…", "Upgrade in progress…"), true);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Level level = event.level;
        if (level.isClientSide || !isInUse) return;

        long now = level.getGameTime();
        if (now - startTick >= 60) {  // 60 ticks = 3s
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(currentUser);
            if (player != null && !upgradingStack.isEmpty()) {
                if (upgradingStack.getItem() instanceof IPackAPunchable up 
                    && up.canBePackAPunched(upgradingStack)) {
                    up.applyPackAPunch(upgradingStack);
                }
                ItemStack currentInHand = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (currentInHand.isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, upgradingStack);
                } else {
                    boolean added = player.getInventory().add(upgradingStack);
                    if (!added) {
                        player.drop(upgradingStack, false);
                    }
                }
                player.displayClientMessage(getTranslatedComponent(player, "§aVotre arme a été améliorée !", "§aYour weapon has been upgraded!"), true);
            }

            // Reset de l’état
            isInUse = false;
            upgradingStack = ItemStack.EMPTY;
            currentUser = null;
            currentBlock = null;
        }
    }
}

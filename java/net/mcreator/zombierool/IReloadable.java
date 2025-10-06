package net.mcreator.zombierool.api;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType; // Importez GameType

public interface IReloadable {
    int getAmmo(ItemStack stack);
    void setAmmo(ItemStack stack, int ammo);

    int getReserve(ItemStack stack);
    void setReserve(ItemStack stack, int reserve);

    int getReloadTimer(ItemStack stack);
    void setReloadTimer(ItemStack stack, int timer);

    void initializeIfNeeded(ItemStack stack);
    void startReload(ItemStack stack, Player player);
    void tickReload(ItemStack stack, Player player, Level level);

    int getMaxAmmo();
    int getMaxReserve();

    default int getAdjustedReloadTimer(ItemStack stack, Player player) {
        int base = getReloadTimer(stack);
        if (player != null && player.hasEffect(net.mcreator.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
            return Math.max(1, base / 2);
        }
        return base;
    }

    /**
     * Consomme une certaine quantité de munitions. Si le joueur est en mode créatif, ne fait rien.
     * @param stack L'ItemStack de l'arme.
     * @param player Le joueur utilisant l'arme.
     * @param amount La quantité de munitions à consommer.
     */
    default void consumeAmmo(ItemStack stack, Player player, int amount) {
        // Si le joueur est en mode créatif, ne consommez pas de munitions
        if (player != null && player.isCreative()) {
            return;
        }
        // Sinon, consommez normalement
        setAmmo(stack, getAmmo(stack) - amount);
    }
}
package me.cryo.zombierool.api;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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

	int getMaxAmmo(ItemStack stack);
	int getMaxReserve(ItemStack stack);

	default boolean isInfinite(ItemStack stack) {
	    return getMaxReserve(stack) < 0;
	}

	default int getAdjustedReloadTimer(ItemStack stack, Player player) {
	    int base = getReloadTimer(stack);
	    if (player != null && player.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get())) {
	        return Math.max(1, base / 2);
	    }
	    return base;
	}

	default void consumeAmmo(ItemStack stack, Player player, int amount) {
	    if (player != null && player.isCreative()) {
	        return;
	    }
	    setAmmo(stack, getAmmo(stack) - amount);
	}
}
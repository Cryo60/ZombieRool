package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Player tosses stay on the ground for their owner until that inventory slot is filled again. */
public final class PrivateDrops {
    public static final String OWNER = "zr_drop_owner";
    public static final String SLOT = "zr_drop_slot";
    private static final String PENDING_SLOT = "zr_pending_drop_slot";
    private static final String PENDING_TICK = "zr_pending_drop_tick";

    private PrivateDrops() {}

    public static void markPendingSlot(Player player, int slot) {
        player.getPersistentData().putInt(PENDING_SLOT, slot);
        player.getPersistentData().putLong(PENDING_TICK, player.level().getGameTime());
    }

    public static void tagToss(Player player, ItemEntity tossed) {
        tossed.getPersistentData().putUUID(OWNER, player.getUUID());
        tossed.setUnlimitedLifetime();
        if (player.getPersistentData().getLong(PENDING_TICK) == player.level().getGameTime()) {
            tossed.getPersistentData().putInt(SLOT, player.getPersistentData().getInt(PENDING_SLOT));
        }
        player.getPersistentData().remove(PENDING_TICK);
    }

    public static void reapReplacedSlots(ServerLevel level) {
        for (ItemEntity item : level.getEntities(EntityType.ITEM, entity ->
                entity.getPersistentData().hasUUID(OWNER) && entity.getPersistentData().contains(SLOT))) {
            if (!WeaponFacade.isWeapon(item.getItem())) continue;
            Player owner = level.getPlayerByUUID(item.getPersistentData().getUUID(OWNER));
            if (owner == null) continue;
            int slot = item.getPersistentData().getInt(SLOT);
            if (slot < 0 || slot >= owner.getInventory().getContainerSize()) continue;
            ItemStack occupied = owner.getInventory().getItem(slot);
            if (!occupied.isEmpty() && WeaponFacade.isWeapon(occupied)) {
                item.discard();
            }
        }
    }
}

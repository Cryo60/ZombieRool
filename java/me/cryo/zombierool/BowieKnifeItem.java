package me.cryo.zombierool.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BowieKnifeItem extends Item {
    public BowieKnifeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide && entity instanceof Player player) {
            if (!player.isCreative()) {
                // Donne le couteau Bowie de façon permanente au joueur
                player.getPersistentData().putBoolean("zr_has_bowie_knife", true);
                level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                // Consomme l'objet pour qu'il ne reste pas dans l'inventaire
                stack.shrink(stack.getCount());
            }
        }
    }
}
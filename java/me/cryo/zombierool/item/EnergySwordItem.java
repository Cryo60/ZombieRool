package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

public class EnergySwordItem extends WeaponImplementations.MeleeWeaponItem {

    public EnergySwordItem(WeaponSystem.Definition def) {
        super(def);
    }

    @Override protected float getCriticalChance() { return 0.15f; }
    @Override protected float getCriticalMultiplier() { return 1.5f; }
    @Override protected double getCleaveRadius() { return 2.5D; }
    @Override protected float getCleaveDamagePercentage() { return 0.75f; }
    @Override protected float getDashDistance() { return 5.0f; }
    @Override protected int getDashCooldownTicks() { return 30; } 
    @Override protected boolean canDash(ItemStack stack) { return true; } 

    // Force l'appel de vos sons précis dans tous les cas
    @Override
    protected SoundEvent getSwingSound(ItemStack stack) {
        return isPackAPunched(stack) 
            ? me.cryo.zombierool.init.ZombieroolModSounds.ENERGY_SWORD_SWING_UPGRADED.get() 
            : me.cryo.zombierool.init.ZombieroolModSounds.ENERGY_SWORD_SWING.get();
    }
}
package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponImplementations;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

public class OldSwordWeaponItem extends WeaponImplementations.MeleeWeaponItem {

    public OldSwordWeaponItem(WeaponSystem.Definition def) {
        super(def);
    }

    @Override protected float getCriticalChance() { return 0.10f; }
    @Override protected float getCriticalMultiplier() { return 1.25f; }
    @Override protected double getCleaveRadius() { return 1.5D; }
    @Override protected float getCleaveDamagePercentage() { return 0.50f; }
    @Override protected float getDashDistance() { return 3.0f; }
    @Override protected int getDashCooldownTicks() { return 60; } 
    
    @Override 
    protected boolean canDash(ItemStack stack) { 
        return isPackAPunched(stack); 
    } 

    // Force le son "old_sword_swing" qu'elle soit améliorée ou non
    @Override
    protected SoundEvent getSwingSound(ItemStack stack) {
        return me.cryo.zombierool.init.ZombieroolModSounds.OLD_SWORD_SWING.get();
    }
}
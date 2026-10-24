package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.gameplay.WonderWeaponEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

/** Uses the normal weapon, reload, wall, career and Pack-a-Punch systems. */
public final class ClassicWonderWeaponItem extends WeaponSystem.BaseGunItem {
    public ClassicWonderWeaponItem(WeaponSystem.Definition definition){super(definition);}
    @Override protected void performShooting(ItemStack stack,Player player,float charge){
        if(player.level().isClientSide)return;
        WonderWeaponEffects.fire(player,stack,def.id.contains("winters_howl"),getWeaponDamage(stack),def.stats.range);
    }
}

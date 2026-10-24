package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;

/** Ordinary ammunition-based fantasy weapons, with physical bolts and spell trails. */
public class FantasyRangedWeaponItem extends WeaponSystem.BaseGunItem {
    public FantasyRangedWeaponItem(WeaponSystem.Definition definition){super(definition);}
    @Override protected void performShooting(ItemStack stack,Player player,float charge,boolean left){
        if(player.level().isClientSide)return;
        var definition=getDefinition();boolean upgraded=isPackAPunched(stack);
        var arrow=new Arrow(player.level(),player);var at=getVisualMuzzlePos(player,left);arrow.setPos(at.x,at.y,at.z);
        arrow.setBaseDamage(0);arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        float draw=this instanceof MapBowItem?net.minecraft.util.Mth.clamp(charge,.1f,1):1;
        arrow.shootFromRotation(player,player.getXRot(),player.getYRot(),0,definition.ballistics.velocity*(.5f+.5f*draw),getDynamicSpread(stack,player));
        arrow.setNoGravity(!definition.ballistics.gravity);
        var data=arrow.getPersistentData();PackAPunchProjectiles.applyCustom(data,getWeaponDamage(stack)*(.3f+.7f*draw),upgraded);
        String element=definition.ballistics.trail_vfx;
        if(element.equals("FIREBALL")||element.equals("SHADOW")){PackAPunchProjectiles.applyInvisibleTrail(data,element);data.putString("zr_fantasy_element",element);arrow.setSilent(true);}
        player.level().addFreshEntity(arrow);
    }
    @Override protected void performShooting(ItemStack stack,Player player,float charge){performShooting(stack,player,charge,false);}
}

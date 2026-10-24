package me.cryo.zombierool.item;

import me.cryo.zombierool.core.system.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

/** Powder firearms retain the shared pellet, falloff, penetration, reload and perk rules. */
public final class MapPowderWeaponItem extends WeaponImplementations.HitscanGunItem {
    public MapPowderWeaponItem(WeaponSystem.Definition def){super(def);}
    @Override protected void performShooting(ItemStack stack,Player player,float charge,boolean left){
        if(player.level() instanceof net.minecraft.server.level.ServerLevel level){
            var def=getDefinition();boolean pap=isPackAPunched(stack);int pellets=pap&&def.pap.pellet_count_override>0?def.pap.pellet_count_override:def.ballistics.count;
            for(int i=0;i<Math.max(1,Math.min(16,pellets));i++)me.cryo.zombierool.core.manager.BallisticManager.fireBullet((net.minecraft.server.level.ServerPlayer)player,(float)def.stats.range,getWeaponDamage(stack),getDynamicSpread(stack,player),def.stats.penetration+(pap?def.pap.penetration_bonus:0),stack,0);
            var muzzle=getVisualMuzzlePos(player,left);level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,muzzle.x,muzzle.y,muzzle.z,8,.09,.09,.09,.025);level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,muzzle.x,muzzle.y,muzzle.z,3,.02,.02,.02,.01);
        }
    }
}

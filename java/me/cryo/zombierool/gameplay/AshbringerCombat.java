package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.core.manager.DamageManager;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;

/** Save-local holy greatsword. Every swing has a bounded number of secondary hits. */
public final class AshbringerCombat {
    private AshbringerCombat(){}
    public static boolean matches(ItemStack stack){return stack.getItem() instanceof WeaponSystem.BaseGunItem gun&&gun.getDefinition().id.equals("zombierool:stratholme_ashbringer");}
    public static boolean hit(Player player,ItemStack stack,LivingEntity target,float amount){
        boolean alive=target.isAlive();boolean hit=DamageManager.applyDamage(target,player.damageSources().playerAttack(player),damage(stack,target,amount));
        if(hit&&alive&&!target.isAlive()&&matches(stack))me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnAshbringerKill",player.getUUID().toString(),target.getUUID().toString(),target.getMobType()==MobType.UNDEAD);
        return hit;
    }
    public static float damage(ItemStack stack,LivingEntity target,float amount){
        return matches(stack)&&(target.getMobType()==MobType.UNDEAD||MapActors.owns(target)&&MapKnifeRules.profile(target).getBoolean("undead"))?amount*1.75f:amount;
    }
    public static void cleave(Player player,ItemStack stack,LivingEntity primary,float amount,boolean heavy){
        if(!(player.level() instanceof ServerLevel level))return;
        long now=level.getGameTime();var tag=stack.getOrCreateTag();
        boolean burst=heavy&&now>=tag.getLong("zr_ashbringer_burst_until");
        if(burst)tag.putLong("zr_ashbringer_burst_until",now+80);
        double radius=burst?4.5:1.8;int limit=burst?12:6;
        var from=player.getEyePosition();var forward=player.getLookAngle();
        var candidates=level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(radius),e->e!=primary&&e!=player&&e.isAlive()&&!(e instanceof Player)&&(e instanceof net.minecraft.world.entity.monster.Enemy||MapActors.owns(e)));
        candidates.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        int hits=0;
        for(var enemy:candidates){
            if(hits>=limit)break;
            var delta=enemy.getBoundingBox().getCenter().subtract(from);
            if(delta.lengthSqr()>radius*radius||delta.normalize().dot(forward)<(burst?.15:.35)||!MeleeTrace.visible(player,enemy))continue;
            double vertical=enemy.getDeltaMovement().y;
            if(hit(player,stack,enemy,amount*(burst?.9f:.55f))){var velocity=enemy.getDeltaMovement();enemy.setDeltaMovement(velocity.x,vertical,velocity.z);MeleeCombat.pushHorizontal(player,enemy,burst?.65:.22);hits++;}
        }
        level.sendParticles(ParticleTypes.END_ROD,primary.getX(),primary.getY()+1,primary.getZ(),burst?24:4,burst?1.1:.15,burst?.5:.15,burst?1.1:.15,.03);
        if(burst)level.playSound(null,player.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,1,.6f);
    }
}

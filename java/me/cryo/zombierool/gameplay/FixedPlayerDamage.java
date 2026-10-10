package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Final health damage, after vanilla difficulty and armor. Canceled hits remain canceled. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class FixedPlayerDamage {
    public static final float SELF_EXPLOSION_FRACTION = 1.0f / 6.0f;
    private static final ThreadLocal<Float> FORCED = new ThreadLocal<>();
    public static boolean apply(Player player,float amount) { return apply(player,amount,player.damageSources().generic()); }
    private static boolean apply(Player player,float amount,net.minecraft.world.damagesource.DamageSource source) {
        if(player.isCreative() || player.isSpectator() || !player.isAlive())return false;
        Float previous=FORCED.get();
        try { FORCED.set(amount); player.invulnerableTime=0; return player.hurt(source,amount); }
        finally {if(previous==null)FORCED.remove();else FORCED.set(previous);}
    }
    public static void trapContact(Player player) {
        if(player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get()))return;
        long now=player.level().getGameTime();var data=player.getPersistentData();
        if(data.contains("zr_last_trap_hit") && now-data.getLong("zr_last_trap_hit")<20)return;
        // One pulse across overlapping emitters, including armor-absorbed hits.
        data.putLong("zr_last_trap_hit",now);apply(player,1);
    }
    /** A grenade whose fuse expires while held must defeat armor and absorption. */
    public static void heldGrenadeExplosion(Player player) {
        if(player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get()))return;
        apply(player,player.getMaxHealth()+player.getAbsorptionAmount(),player.damageSources().genericKill());
    }
    public static void selfExplosion(Player player) {
        if(player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get()))return;
        long now=player.level().getGameTime();var data=player.getPersistentData();
        if(data.contains("zr_last_self_exp") && now-data.getLong("zr_last_self_exp")<10)return;
        if(apply(player,player.getMaxHealth()*SELF_EXPLOSION_FRACTION,player.damageSources().explosion(player,player)))data.putLong("zr_last_self_exp",now);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if(!(event.getEntity() instanceof Player player) || player.level().isClientSide || event.getAmount()<=0)return;
        player.getPersistentData().putLong("zr_last_hurt_game_time",player.level().getGameTime());
        if(FORCED.get()!=null){event.setAmount(FORCED.get());return;}
        if(!WaveManager.isGameRunning())return;
        var source=event.getSource();var attacker=source.getEntity();
        if(attacker instanceof ZombieEntity zombie)event.setAmount(zombie.isCrawler()?1:2);
        else if(attacker instanceof CrawlerEntity || attacker instanceof HellhoundEntity)event.setAmount(1);
        else if(source.is(DamageTypeTags.IS_EXPLOSION))event.setAmount(player.getMaxHealth()*SELF_EXPLOSION_FRACTION);
        else {
            switch(source.getMsgId()) {
                case "inFire", "onFire", "hotFloor", "cactus", "sweetBerryBush", "drown", "fall", "freeze", "starve", "magic", "indirectMagic", "wither", "dragonBreath" -> event.setAmount(1);
                case "lava", "lightningBolt", "anvil", "fallingBlock", "fallingStalactite", "stalagmite" -> event.setAmount(2);
            }
        }
    }
}

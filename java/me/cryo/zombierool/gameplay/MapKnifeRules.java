package me.cryo.zombierool.gameplay;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;

/** Knife balance is independent of authored bullet health. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class MapKnifeRules {
    private record Hit(LivingEntity target,float amount){}
    private static final ThreadLocal<Hit> HIT=new ThreadLocal<>();
    public static boolean apply(LivingEntity target,net.minecraft.world.damagesource.DamageSource source,float amount){var previous=HIT.get();try{if(MapActors.owns(target)&&!profile(target).getString("knifeMode").equals("fixed"))HIT.set(new Hit(target,amount));return me.cryo.zombierool.core.manager.DamageManager.applyDamage(target,source,amount);}finally{if(previous==null)HIT.remove();else HIT.set(previous);}}
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void damage(net.minecraftforge.event.entity.living.LivingDamageEvent e){var hit=HIT.get();if(hit!=null&&hit.target()==e.getEntity())e.setAmount(hit.amount());}
    private MapKnifeRules(){}
    public static net.minecraft.nbt.CompoundTag profile(LivingEntity target){return target instanceof me.cryo.zombierool.entity.MapActorEntity a?a.profile():NativeMapActors.profile(target);}
    public static float damage(LivingEntity target,float ordinary){
        if(!MapActors.owns(target))return ordinary;
        var p=profile(target);String mode=p.getString("knifeMode");
        if(mode.isEmpty())mode=!EquipmentEffects.boss(target)?"wave":"fixed";
        if(mode.equals("hits"))return target.getMaxHealth()/Math.max(1,p.getInt("knifeHits"))*(ordinary/6f)+.0001f;
        if(mode.equals("wave")&&target.level() instanceof ServerLevel level){
            var config=me.cryo.zombierool.config.WorldConfig.get(level);int wave=Math.max(1,WaveManager.getCurrentWave());
            float health=config.getZombieBaseHealth();if(wave>=2)health=wave<=9?health+wave*4:(health+36)*(float)Math.pow(1.15,wave-9);
            health=Math.min(health,config.getZombieMaxHealth());return target.getMaxHealth()*ordinary/Math.max(.05f,health)+.0001f;
        }
        return ordinary;
    }
}

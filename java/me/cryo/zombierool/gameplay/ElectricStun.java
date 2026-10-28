package me.cryo.zombierool.gameplay;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Electric slowdowns must release navigation as well as their movement attribute. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class ElectricStun {
    public static final int DURATION=20;
    public static boolean active(LivingEntity entity){var slow=entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);var weak=entity.getEffect(MobEffects.WEAKNESS);return entity.isAlive()&&slow!=null&&slow.getAmplifier()==9&&weak!=null&&weak.getAmplifier()==9;}
    private static final String UNTIL="zr_electric_stun_until";
    private ElectricStun() {}
    public static void apply(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,DURATION,9,false,false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,DURATION,9,false,false));
        target.getPersistentData().putLong(UNTIL,target.level().getGameTime()+DURATION);
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || !mob.isAlive()) return;
        var data=mob.getPersistentData();
        if (!data.contains(UNTIL) || mob.level().getGameTime()<data.getLong(UNTIL)) return;
        data.remove(UNTIL);
        // An unloaded chunk does not tick potion durations, while world time keeps advancing.
        var slow=mob.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow!=null && slow.getAmplifier()==9 && slow.getDuration()<=100) mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        var weak=mob.getEffect(MobEffects.WEAKNESS);if(weak!=null&&weak.getAmplifier()==9&&weak.getDuration()<=100)mob.removeEffect(MobEffects.WEAKNESS);
        mob.getNavigation().stop();
        me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(mob);
    }
}

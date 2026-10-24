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
    private static final String UNTIL="zr_electric_stun_until";
    private ElectricStun() {}
    public static void apply(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,100,9,false,false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,100,9,false,false));
        target.getPersistentData().putLong(UNTIL,target.level().getGameTime()+100);
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || !mob.isAlive()) return;
        var data=mob.getPersistentData();
        if (!data.contains(UNTIL) || mob.level().getGameTime()<data.getLong(UNTIL)) return;
        data.remove(UNTIL);
        // An unloaded chunk does not tick potion durations, while world time keeps advancing.
        var slow=mob.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow!=null && slow.getAmplifier()==9 && slow.getDuration()<=100) mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        mob.getNavigation().stop();
        me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(mob);
    }
}

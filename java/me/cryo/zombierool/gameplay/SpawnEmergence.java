package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.*;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool")
public final class SpawnEmergence {
    public static final int DURATION=12;
    private static final String RESTORE="zr_emerge_no_ai";
    private SpawnEmergence(){}
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof AbstractZombieRoolEntity mob))return;
        mob.getPersistentData().remove("zr_reachable_next_check");
        if(mob.getPersistentData().contains(RESTORE)){mob.setNoAi(mob.getPersistentData().getBoolean(RESTORE));mob.getPersistentData().remove(RESTORE);}
        if(event.loadedFromDisk()||!(mob instanceof ZombieEntity||mob instanceof CrawlerEntity||mob instanceof SpecialEnemyEntity special&&!special.isPanzer()&&!special.isNapalm())||!mob.isAlive())return;
        mob.getPersistentData().putBoolean(RESTORE,mob.isNoAi());mob.setNoAi(true);mob.setEmergenceTicks(DURATION);
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof AbstractZombieRoolEntity mob)||mob.level().isClientSide||mob.getEmergenceTicks()<=0)return;
        if(!mob.isAlive()){mob.setEmergenceTicks(0);mob.getPersistentData().remove(RESTORE);return;}
        int remaining=mob.getEmergenceTicks()-1;mob.setEmergenceTicks(remaining);mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if(mob.level() instanceof ServerLevel level&&remaining%4==0){var p=mob.blockPosition().below();var state=level.getBlockState(p);if(!state.isAir())level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,state),mob.getX(),mob.getY()+.05,mob.getZ(),8,.3,.03,.3,.02);}
        if(remaining==0||!mob.isAlive()){mob.setEmergenceTicks(0);mob.setNoAi(mob.getPersistentData().getBoolean(RESTORE));mob.getPersistentData().remove(RESTORE);}
    }
}

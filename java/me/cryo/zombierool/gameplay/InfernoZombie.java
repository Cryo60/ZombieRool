package me.cryo.zombierool.gameplay;

import java.util.UUID;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool")
public final class InfernoZombie {
    private static final UUID SPEED=UUID.fromString("a9e16442-1df4-4dcd-897d-91ff9bdaf609");
    private InfernoZombie() {}
    public static boolean hot(net.minecraft.world.level.block.state.BlockState state){
        return state.is(net.minecraft.tags.BlockTags.FIRE)||state.is(net.minecraft.world.level.block.Blocks.LAVA)||state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
            ||state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
    }
    private static final String ORDINARY_BURN="zr_ordinary_burn_until";
    public static void ordinaryBurn(net.minecraft.world.entity.LivingEntity target,int seconds){
        if(target instanceof ZombieEntity zombie)zombie.getPersistentData().putLong(ORDINARY_BURN,zombie.level().getGameTime()+seconds*20L+20);
        target.setSecondsOnFire(seconds);
    }
    public static boolean ordinaryBurning(ZombieEntity zombie){
        var data=zombie.getPersistentData();if(!data.contains(ORDINARY_BURN))return false;
        if(data.getLong(ORDINARY_BURN)>zombie.level().getGameTime()||zombie.getRemainingFireTicks()>0)return true;
        data.remove(ORDINARY_BURN);return false;
    }
    public static void ignite(ZombieEntity zombie){
        zombie.setInferno(true);zombie.setSuperSprinter(true);
        var speed=zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null&&speed.getModifier(SPEED)==null)speed.addTransientModifier(new AttributeModifier(SPEED,"Inferno sprint",.65,AttributeModifier.Operation.MULTIPLY_TOTAL));
        zombie.setSecondsOnFire(3);
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof ZombieEntity zombie)||!(zombie.level() instanceof ServerLevel level)||!zombie.isAlive())return;
        if(!zombie.isInferno()&&!ordinaryBurning(zombie)&&zombie.tickCount%4==0){
            var box=zombie.getBoundingBox().inflate(.02);
            for(BlockPos pos:BlockPos.betweenClosed((int)Math.floor(box.minX),(int)Math.floor(box.minY-.12),(int)Math.floor(box.minZ),(int)Math.floor(box.maxX),(int)Math.floor(box.maxY),(int)Math.floor(box.maxZ)))
                if(hot(level.getBlockState(pos))){ignite(zombie);break;}
        }
        if(zombie.isInferno())ignite(zombie);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent event){
        if(!(event.getEntity() instanceof ZombieEntity zombie))return;
        if(ordinaryBurning(zombie)&&!zombie.isInferno())return;
        var source=event.getSource();
        if(source.is(DamageTypes.IN_FIRE)||source.is(DamageTypes.LAVA)||source.is(DamageTypes.HOT_FLOOR)){
            if(zombie.level() instanceof ServerLevel&&zombie.isAlive())ignite(zombie);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event){
        if(!(event.getEntity() instanceof ZombieEntity zombie)||!zombie.isInferno()||!(zombie.level() instanceof ServerLevel level)||zombie.getPersistentData().getBoolean("zr_inferno_exploded"))return;
        zombie.getPersistentData().putBoolean("zr_inferno_exploded",true);
        var origin=zombie.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,origin.x,origin.y,origin.z,1,0,0,0,0);
        level.sendParticles(ParticleTypes.FLAME,origin.x,origin.y,origin.z,35,.5,.7,.5,.08);
        level.playSound(null,zombie.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.HOSTILE,.85f,1.15f);
        for(var player:level.players())if(player.distanceToSqr(origin)<9&&level.clip(new ClipContext(origin,player.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,zombie)).getType()==HitResult.Type.MISS)
            FixedPlayerDamage.selfExplosion(player);
    }
}

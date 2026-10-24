package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.particles.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class EquipmentEffects {
    private static final String FLASH="zr_flash_until",PHEROMONE="zr_pheromone_until";
    private static final UUID SLOW=UUID.fromString("8c41657d-b845-4d12-9930-0437edb96ab5");
    private EquipmentEffects(){}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){if(e.getEntity() instanceof Mob m&&!m.level().isClientSide)expireSlow(m);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void attack(net.minecraftforge.event.entity.living.LivingAttackEvent e){if(e.getEntity() instanceof Player&&e.getSource().getEntity() instanceof Mob m&&active(m))e.setCanceled(true);}

    public static boolean enemy(LivingEntity e){var id=ForgeRegistries.ENTITY_TYPES.getKey(e.getType());return e.isAlive()&&e instanceof Monster&&id!=null&&id.getNamespace().equals("zombierool")&&!(e instanceof WhiteKnightEntity)&&!(e instanceof MrChiefEntity)&&!id.getPath().contains("dummy");}
    public static boolean instantKillImmune(LivingEntity e){return boss(e)||e instanceof me.cryo.zombierool.entity.SpecialEnemyEntity special&&special.isNapalm();}
    public static boolean boss(LivingEntity e){var id=ForgeRegistries.ENTITY_TYPES.getKey(e.getType());return e.getTags().contains("zr_boss")||e.getTags().contains("boss")||e.getPersistentData().getBoolean("zr_boss")||e.getPersistentData().getBoolean("isBoss")||e instanceof net.minecraft.world.entity.boss.wither.WitherBoss||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||id!=null&&id.getPath().contains("boss");}
    public static boolean active(Mob m){return m.getPersistentData().getLong(FLASH)>m.level().getGameTime()||m.getPersistentData().getLong(PHEROMONE)>m.level().getGameTime();}
    public static boolean flashed(Mob m){return m.getPersistentData().getLong(FLASH)>m.level().getGameTime();}
    public static void burst(ServerLevel level,Vec3 pos,EquipmentSystem.Kind kind,UUID owner){
        EquipmentSystem.sound(level,pos,kind==EquipmentSystem.Kind.FLASH?"flash_burst":"pheromone_burst",.8f);
        level.sendParticles(kind==EquipmentSystem.Kind.FLASH?ParticleTypes.FLASH:ParticleTypes.WITCH,pos.x,pos.y+.2,pos.z,kind==EquipmentSystem.Kind.FLASH?1:40,.7,.5,.7,.03);
        var player=owner==null?null:level.getServer().getPlayerList().getPlayer(owner);
        if(kind==EquipmentSystem.Kind.FLASH&&player!=null
                &&player.isAlive()&&player.level()==level&&player.distanceToSqr(pos)<=64
                &&player.getViewVector(1).dot(pos.add(0,.1,0).subtract(player.getEyePosition()))>0
                &&level.clip(new ClipContext(pos.add(0,.1,0),player.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS){
            player.getPersistentData().putLong("zr_player_flash_until",level.getGameTime()+80);
            me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.network.packet.S2CEquipmentFlashPacket(80));
        }
        for(var mob:level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(pos,pos).inflate(8),m->enemy(m)||boss(m))){
            if(mob.distanceToSqr(pos)>64||level.clip(new ClipContext(pos.add(0,.1,0),mob.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()!=HitResult.Type.MISS)continue;
            boolean already=mob.getPersistentData().getLong(kind==EquipmentSystem.Kind.FLASH?FLASH:PHEROMONE)>level.getGameTime();
            apply(mob,kind);
            if(!already&&!boss(mob)&&mob.getPersistentData().getLong(kind==EquipmentSystem.Kind.FLASH?FLASH:PHEROMONE)>level.getGameTime()){
                me.cryo.zombierool.career.CareerManager.progressGameplay(player,kind==EquipmentSystem.Kind.FLASH?me.cryo.zombierool.career.CareerManager.ChallengeType.FLASHED_ENEMIES:me.cryo.zombierool.career.CareerManager.ChallengeType.PHEROMONE_ENEMIES,1);
                me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnEnemyEquipmentEffect",mob.getUUID().toString(),kind.id,owner==null?"":owner.toString());
            }
        }
    }
    public static void apply(Mob mob,EquipmentSystem.Kind kind){
        if(kind==EquipmentSystem.Kind.PHEROMONE&&boss(mob))return;
        if(kind==EquipmentSystem.Kind.FLASH&&boss(mob)){
            var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null){speed.removeModifier(SLOW);speed.addTransientModifier(new AttributeModifier(SLOW,"Flash slow",-.6,AttributeModifier.Operation.MULTIPLY_TOTAL));mob.getPersistentData().putLong("zr_flash_slow_until",mob.level().getGameTime()+80);}return;
        }
        mob.getPersistentData().putLong(kind==EquipmentSystem.Kind.FLASH?FLASH:PHEROMONE,mob.level().getGameTime()+(kind==EquipmentSystem.Kind.FLASH?80:100));
        if(mob.getTarget() instanceof Player)mob.setTarget(null);mob.getNavigation().stop();
        if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof EffectGoal))mob.goalSelector.addGoal(0,new EffectGoal(mob));
    }
    public static void expireSlow(Mob m){var n=m.getPersistentData();if(n.contains("zr_flash_slow_until")&&n.getLong("zr_flash_slow_until")<=m.level().getGameTime()){var speed=m.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SLOW);n.remove("zr_flash_slow_until");}}
    /** Exclusive movement/look control prevents pursuit goals from fighting the effect. */
    private static final class EffectGoal extends Goal {
        final Mob mob;int nextPath,nextHit;float heading;
        EffectGoal(Mob mob){this.mob=mob;heading=mob.getYRot();setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP,Flag.TARGET));}
        @Override public boolean canUse(){return active(mob);}
        @Override public void start(){heading=mob.getYRot();nextPath=nextHit=0;}
        @Override public boolean canContinueToUse(){return active(mob);}
        @Override public void stop(){mob.setTarget(null);mob.getNavigation().stop();me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(mob);}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            if(!mob.isAlive())return;
            if(mob.level() instanceof ServerLevel sl&&mob.tickCount%5==0)sl.sendParticles(flashed(mob)?ParticleTypes.END_ROD:ParticleTypes.WITCH,mob.getX(),mob.getEyeY(),mob.getZ(),1,.15,.1,.15,0);
            if(flashed(mob)){
                mob.setTarget(null);
                if(mob.tickCount>=nextPath){nextPath=mob.tickCount+12;Vec3 next=mob.position().add(Vec3.directionFromRotation(0,heading).scale(4));var path=mob.getNavigation().createPath(next.x,next.y,next.z,0);if(path!=null&&path.getNodeCount()>1)mob.getNavigation().moveTo(path,1.2);else heading+=mob.getRandom().nextBoolean()?80:-80;}
            }else{
                var target=mob.getTarget();
                if(target==null||!enemy(target)||target==mob||boss(target)||mob.distanceToSqr(target)>144){target=mob.level().getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(12),m->m!=mob&&enemy(m)&&!boss(m)).stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);mob.setTarget(target);}
                if(target!=null){mob.getLookControl().setLookAt(target,30,30);if(mob.tickCount>=nextPath){nextPath=mob.tickCount+10;mob.getNavigation().moveTo(target,1.3);}if(mob.distanceToSqr(target)<Math.pow(mob.getBbWidth()+target.getBbWidth()+.8,2)&&mob.tickCount>=nextHit){nextHit=mob.tickCount+20;me.cryo.zombierool.core.manager.DamageManager.applyDamage(target,mob.damageSources().mobAttack(mob),Math.max(12,target.getMaxHealth()*.25f));}}
            }
        }
    }
}

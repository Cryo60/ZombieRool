package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.particles.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.UUID;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class WonderWeaponEffects {
    private static final UUID COLD=UUID.fromString("1d35679d-84bc-4a14-a34b-87426db6faf7");
    private static final String FROZEN="zr_winter_frozen_until",CHILL="zr_winter_chill_until";
    private WonderWeaponEffects(){}
    public static boolean frozen(Mob mob){return mob.isAlive()&&mob.getPersistentData().getLong(FROZEN)>mob.level().getGameTime();}
    public static boolean camouflage(Player player){return player.getPersistentData().getLong("zr_vril_camouflage_until")>player.level().getGameTime();}
    public static void fire(Player player,ItemStack stack,boolean winter,float damage,double range){
        var level=(ServerLevel)player.level();boolean pap=WeaponFacade.isPackAPunched(stack);
        Vec3 start=player.getEyePosition(),direction=player.getViewVector(1),end=start.add(direction.scale(range));
        var wall=CompatibilityFixes.clip(level,new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        double distance=start.distanceTo(end);LivingEntity first=null;double nearest=distance;
        var candidates=level.getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(winter?3:.6),e->e!=player&&e.isAlive()&&(EquipmentEffects.enemy(e)||EquipmentEffects.boss(e)||!winter&&(e instanceof Player||e instanceof VrilHumanEntity)));
        for(var target:candidates){
            if(winter){
                Vec3 center=target.getBoundingBox().getCenter(),delta=center.subtract(start);double along=delta.dot(direction);
                if(along<0||along>distance||delta.subtract(direction.scale(along)).length()> .55+along*.16)continue;
                if(CompatibilityFixes.clip(level,new ClipContext(start,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)continue;
                frost(target,player,pap,damage);
            }else{
                var hit=target.getBoundingBox().inflate(.18).clip(start,end);
                if(hit.isPresent()&&start.distanceTo(hit.get())<=nearest){nearest=start.distanceTo(hit.get());first=target;}
            }
        }
        if(first!=null){end=start.add(direction.scale(nearest));convert(first,player,pap);}
        var visual=new me.cryo.zombierool.network.packet.S2CWeaponVfxPacket(winter?"WINTERS_HOWL":"VR11",start.add(direction.scale(.5)).add(0,-.12,0),end,pap,false);
        me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->player),visual);
    }
    public static void frost(LivingEntity target,Player owner,boolean pap,float damage){
        if(!(target instanceof Mob mob)||!EquipmentEffects.enemy(target)&&!EquipmentEffects.boss(target))return;
        var data=target.getPersistentData();long now=target.level().getGameTime();
        data.putBoolean(DamageManager.GUN_DAMAGE_TAG,true);data.putBoolean(DamageManager.HEADSHOT_TAG,false);
        int hits=data.getLong(CHILL)>now?data.getInt("zr_winter_hits")+1:1;
        data.putInt("zr_winter_hits",hits);data.putLong(CHILL,now+(pap?160:100));
        boolean freeze=!EquipmentEffects.boss(target)&&hits>=(pap?2:2+Math.max(0,WaveManager.getCurrentWave()-10)/15);
        var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null){speed.removeModifier(COLD);speed.addTransientModifier(new AttributeModifier(COLD,"Winter chill",freeze?-1:-.65,AttributeModifier.Operation.MULTIPLY_TOTAL));}
        if(freeze){
            if(!frozen(mob)&&owner instanceof net.minecraft.server.level.ServerPlayer player)me.cryo.zombierool.career.CareerManager.progressGameplay(player,me.cryo.zombierool.career.CareerManager.ChallengeType.WINTER_FROZEN,1);
            data.putLong(FROZEN,now+(pap?150:100));TraversalMotion.cancel(mob);clearDecoy(mob);mob.getNavigation().stop();mob.setTarget(null);mob.setDeltaMovement(Vec3.ZERO);mob.setTicksFrozen(139);
            EquipmentSystem.sound((ServerLevel)mob.level(),mob.position(),"winters_howl_freeze",.65f);
        }
        ((ServerLevel)mob.level()).sendParticles(ParticleTypes.SNOWFLAKE,mob.getX(),mob.getY()+mob.getBbHeight()/2,mob.getZ(),16,.4,.6,.4,.04);
        DamageManager.applyDamage(target,target.damageSources().playerAttack(owner),damage);
    }
    public static void convert(LivingEntity target,Player owner,boolean pap){
        if(target instanceof Player ally){
            if(!ally.isAlive()||ally.isSpectator())return;
            ally.getPersistentData().putLong("zr_vril_camouflage_until",ally.level().getGameTime()+(pap?200:100));
            if(pap)ally.getPersistentData().putLong("zr_vril_damage_until",ally.level().getGameTime()+200);
            return;
        }
        if(target instanceof VrilHumanEntity human){if(pap&&++human.lazarusHits>=3)human.detonate(owner);return;}
        if(EquipmentEffects.boss(target)||!(target instanceof ZombieEntity||target instanceof CrawlerEntity))return;
        var level=(ServerLevel)target.level();var human=ZombieroolModEntities.VRIL_HUMAN.get().create(level);
        if(human==null)return;
        human.moveTo(target.getX(),target.getY(),target.getZ(),target.getYRot(),0);human.life=pap?300:200;human.owner=owner.getUUID();
        human.setHealth(human.getMaxHealth());
        if(!level.addFreshEntity(human))return;
        if(owner instanceof net.minecraft.server.level.ServerPlayer player)me.cryo.zombierool.career.CareerManager.progressGameplay(player,me.cryo.zombierool.career.CareerManager.ChallengeType.VRIL_CONVERSIONS,1);
        me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnVrilConversion",target.getUUID().toString(),human.getUUID().toString(),owner.getUUID().toString());
        TraversalMotion.cancel(target);target.getPersistentData().putBoolean("zombierool:no_gore",true);target.getPersistentData().putBoolean(DamageManager.GUN_DAMAGE_TAG,true);target.getPersistentData().putBoolean(DamageManager.HEADSHOT_TAG,false);
        target.setHealth(0);target.die(target.damageSources().playerAttack(owner));target.discard();
        EquipmentSystem.sound(level,human.position(),"vr11_convert",.75f);
        level.sendParticles(ParticleTypes.END_ROD,human.getX(),human.getY()+1,human.getZ(),25,.3,.7,.3,.03);
    }
    private record DecoyLock(java.util.UUID id,long until){}
    private static final java.util.Map<Mob,DecoyLock> DECOYS=new java.util.WeakHashMap<>();
    public static void clearDecoy(Mob mob){DECOYS.remove(mob);}
    public static VrilHumanEntity lockedDecoy(Mob mob){
        var lock=DECOYS.get(mob);if(lock==null)return null;
        if(mob.level() instanceof ServerLevel level&&level.getEntity(lock.id()) instanceof VrilHumanEntity human&&human.isAlive()&&mob.distanceToSqr(human)<1600)return human;
        DECOYS.remove(mob);return null;
    }
    public static VrilHumanEntity decoy(Mob mob,double playerDistance){
        if(!(mob instanceof AbstractZombieRoolEntity)||EquipmentEffects.boss(mob))return null;
        long now=mob.level().getGameTime();var current=lockedDecoy(mob);
        if(current!=null&&(DECOYS.get(mob).until()>now||playerDistance>=mob.distanceToSqr(current)*.45))return current;
        DECOYS.remove(mob);
        var selected=mob.level().getEntitiesOfClass(VrilHumanEntity.class,mob.getBoundingBox().inflate(32),h->h.isAlive()&&mob.distanceToSqr(h)<playerDistance*.75).stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
        if(selected!=null)DECOYS.put(mob,new DecoyLock(selected.getUUID(),now+60));return selected;
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){
        if(!(e.getEntity() instanceof Mob mob)||mob.level().isClientSide)return;
        var data=mob.getPersistentData();long now=mob.level().getGameTime();
        if(frozen(mob)){
            mob.getNavigation().stop();mob.setTarget(null);mob.setDeltaMovement(new Vec3(0,mob.getDeltaMovement().y,0));mob.xxa=mob.zza=0;mob.setTicksFrozen(139);
            if(mob.tickCount%8==0)((ServerLevel)mob.level()).sendParticles(ParticleTypes.SNOWFLAKE,mob.getX(),mob.getY()+1,mob.getZ(),4,.4,.7,.4,0);
        }else if(data.contains(FROZEN)){data.remove(FROZEN);mob.setTicksFrozen(0);me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(mob);}
        if(data.contains(CHILL)&&data.getLong(CHILL)<=now){var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(COLD);data.remove(CHILL);data.remove("zr_winter_hits");}
    }
    @SubscribeEvent public static void attack(net.minecraftforge.event.entity.living.LivingAttackEvent e){if(e.getSource().getEntity() instanceof Mob mob&&frozen(mob))e.setCanceled(true);}
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){
        if(e.getEntity().getTicksFrozen()<130||!(e.getEntity().level() instanceof ServerLevel level))return;
        var mob=e.getEntity();EquipmentSystem.sound(level,mob.position(),"winters_howl_shatter",.75f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.ICE.defaultBlockState()),mob.getX(),mob.getY()+1,mob.getZ(),35,.4,.7,.4,.1);
    }
    @SubscribeEvent public static void buff(net.minecraftforge.event.entity.living.LivingHurtEvent e){
        if(e.getSource().getEntity() instanceof Player p&&p.getPersistentData().getLong("zr_vril_damage_until")>p.level().getGameTime()&&EquipmentEffects.enemy(e.getEntity()))
            e.setAmount(EquipmentEffects.boss(e.getEntity())?e.getAmount()*4:Math.max(e.getAmount(),e.getEntity().getMaxHealth()*2));
    }
}

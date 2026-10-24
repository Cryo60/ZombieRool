package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Server-authoritative abilities. No ability inherits Brutus armor, locks or attacks. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class EncounterAbilities {
    private final SpecialEnemyEntity mob;
    private int cooldown=60, rage, action, flee, plague;
    private Vec3 aim=Vec3.ZERO, hook, previousHook;
    private UUID hooked,grabbed;
    private int escapeRepath,romeroBuffCooldown;
    private int teleportWindup,teleportCooldown;
    private String romeroAction="";
    private boolean wasInWater;
    private BlockPos escapePad;
    private double remainingPull;
    private boolean escaped, returned, leapHit, escaping, stoleWeapon, cleanKillRewarded;
    private final ListTag stolen=new ListTag();
    public EncounterAbilities(SpecialEnemyEntity mob){this.mob=mob;}
    public static String kind(SpecialEnemyEntity e){String template=e.encounterTemplate();return template.isBlank()?e.kind():template;}
    public static boolean handles(SpecialEnemyEntity e){return Set.of("pentagon_thief","george_romero","astronaut","abomination","geist").contains(kind(e));}
    private double number(String key){return me.cryo.zombierool.config.SpecialEnemyConfig.get((ServerLevel)mob.level()).getDouble(key);}
    private boolean valid(ServerPlayer p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())&&!me.cryo.zombierool.block.system.TeleporterTravel.bossProtected(p);}
    private void event(String name,Object... args){me.cryo.zombierool.scripting.LuaScriptManager.callEvent(name,args);}
    private void sound(SoundEvent sound,float pitch){mob.level().playSound(null,mob.blockPosition(),sound,SoundSource.HOSTILE,.8f,pitch);}
    public int rageTicks(){return rage;}
    public boolean isFleeing(){return kind(mob).equals("pentagon_thief")&&escaping;}
    public boolean mayTarget(LivingEntity entity){return !kind(mob).equals("pentagon_thief")||!escaping&&entity instanceof ServerPlayer p&&valid(p)&&!hasStolenFrom(p.getUUID())&&me.cryo.zombierool.core.system.WeaponFacade.isWeapon(p.getMainHandItem());}
    private boolean hasStolenFrom(UUID id){return stolen.stream().anyMatch(t->((CompoundTag)t).getUUID("owner").equals(id));}
    public boolean avoidsMelee(){return kind(mob).equals("george_romero")&&action>0||(kind(mob).equals("geist")||kind(mob).equals("astronaut"))&&action>0||kind(mob).equals("geist")&&mob.crawlerLeap.busy()||kind(mob).equals("abomination")&&(action>0||plague>0||hook!=null||hooked!=null)||kind(mob).equals("pentagon_thief")&&escaping;}
    public void provoke(){if(kind(mob).equals("george_romero")&&!mob.isInWater()&&rage==0){setRage((int)number("romeroRageTicks"));romeroAction="rage";action=95;cooldown=110;stop();mob.playBossAnimation("rage",95);EncounterAudio.play(mob,"romero_anger",.9f);EncounterAudio.play(mob,"romero_lamp_start",.65f);}}
    public void setRage(int ticks){boolean was=rage>0;rage=Math.max(0,ticks);if(was!=(rage>0)){var p=mob.bossProfile();p.putBoolean("enraged",rage>0);mob.setBossProfile(p);event("OnRomeroRageChanged",mob.getUUID().toString(),rage>0);}}
    private static void teleport(ServerPlayer p,Vec3 to){p.setPos(to.x,to.y,to.z);p.connection.teleport(to.x,to.y,to.z,p.getYRot(),p.getXRot());}
    private void stop(){mob.getNavigation().stop();mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);}
    public void tick(ServerLevel level){
        if(!WaveManager.isGameRunning()||WaveManager.isPausedByPlayer()||!mob.isAlive())return;
        if(EquipmentEffects.active(mob)||WonderWeaponEffects.frozen(mob)||FiestaFreeze.frozen(mob)||TraversalMotion.active(mob)){hook=null;hooked=null;action=0;mob.hookVisual(null);return;}
        if(cooldown>0)cooldown--;
        if(teleportCooldown>0)teleportCooldown--;
        var target=mob.getTarget() instanceof ServerPlayer p&&valid(p)?p:null;
        switch(kind(mob)){
            case "george_romero" -> romero(level,target);
            case "astronaut" -> astronaut(level,target);
            case "abomination" -> abomination(level,target);
            case "pentagon_thief" -> thief(level,target);
            case "geist" -> geist(level,target);
        }
        if(kind(mob).equals("abomination")){var p=hooked==null?null:level.getServer().getPlayerList().getPlayer(hooked);mob.hookVisual(p!=null?p.getBoundingBox().getCenter():hook);mob.plagueVisual(plague>0?aim:null);}
    }
    private void romero(ServerLevel level,ServerPlayer target){
        boolean water=mob.isInWater();
        if(water){
            if(!wasInWater&&rage>0){setRage(0);action=0;romeroAction="";cooldown=60;mob.playBossAnimation("calm",50);EncounterAudio.play(mob,"romero_laugh",.7f);}
            wasInWater=true;
        }else wasInWater=false;
        double base=mob.bossProfile().contains("speed")?mob.bossProfile().getDouble("speed"):.14;
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(rage>0?number("romeroRageSpeed"):base);
        if(romeroBuffCooldown>0)romeroBuffCooldown--;
        if(action>0){
            stop();action--;
            if((romeroAction.equals("rage")&&action==24)||(romeroAction.equals("slam")&&action==41))romeroSlam(level);
            if(romeroAction.equals("roar")&&action==16)romeroBuff(level,false);
            if(romeroAction.equals("melee")&&action==34)EncounterAudio.play(mob,"romero_swing",.7f);
            if(romeroAction.equals("melee")&&action==30){
                EncounterAudio.play(mob,"romero_melee",.8f);
                for(var p:level.players())if(valid(p)&&mob.distanceToSqr(p)<10&&mob.hasLineOfSight(p)&&p.position().subtract(mob.position()).normalize().dot(aim)>.15){
                    FixedPlayerDamage.panzerFlame(p,mob,(float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE));p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,2));shockPlayer(p,30);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,p.getX(),p.getY()+1,p.getZ(),10,.3,.5,.3,.04);
                }
            }
            return;
        }
        if(!water&&target!=null&&mob.distanceToSqr(target)<number("romeroProvokeRadius")*number("romeroProvokeRadius")){provoke();if(action>0)return;}
        // As in the source encounter, dry ground does not automatically calm an enraged Romero.
        if(rage<=0||target==null||cooldown>0)return;
        if(romeroBuffCooldown==0&&WaveManager.getCurrentWave()>=6&&!level.getEntitiesOfClass(me.cryo.zombierool.entity.ZombieEntity.class,mob.getBoundingBox().inflate(12),LivingEntity::isAlive).isEmpty()){
            romeroAction="roar";action=34;cooldown=60;romeroBuffCooldown=240;mob.playBossAnimation("roar",34);EncounterAudio.play(mob,"romero_speed",.8f);event("OnRomeroRoar",mob.getUUID().toString());
        }else if(mob.distanceToSqr(target)<100&&level.random.nextInt(3)==0){
            romeroAction="slam";action=66;cooldown=90;mob.playBossAnimation("slam",66);EncounterAudio.play(mob,"romero_roar",.8f);
        }
    }
    public static void shockPlayer(ServerPlayer player,int ticks){player.getPersistentData().putLong("zr_encounter_shock_until",player.level().getGameTime()+ticks);me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.network.packet.S2CEncounterShockPacket(ticks));}
    private void romeroSlam(ServerLevel level){
        EncounterAudio.play(mob,"romero_slam",1);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,mob.getX(),mob.getY()+.15,mob.getZ(),48,3.5,.15,3.5,.06);
        for(var p:level.players())if(valid(p)&&mob.distanceToSqr(p)<64&&mob.hasLineOfSight(p)){
            FixedPlayerDamage.panzerFlame(p,mob,2);shockPlayer(p,24);p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,1));
        }
        romeroBuff(level,true);event("OnRomeroGroundSlam",mob.getUUID().toString());
    }
    private void romeroBuff(ServerLevel level,boolean electric){
        int count=0;
        for(var zombie:level.getEntitiesOfClass(me.cryo.zombierool.entity.ZombieEntity.class,mob.getBoundingBox().inflate(12),LivingEntity::isAlive)){
            if(!mob.hasLineOfSight(zombie))continue;
            if(electric)zombie.getPersistentData().putLong("zr_electrified_until",level.getGameTime()+200);
            else zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,160,1));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,zombie.getX(),zombie.getY()+1,zombie.getZ(),6,.3,.6,.3,.03);count++;
        }
        if(count>0&&electric)EncounterAudio.play(mob,"romero_electric",.8f);
        event("OnRomeroZombieBuff",mob.getUUID().toString(),electric?"electric":"speed",count);
    }
    private void astronaut(ServerLevel level,ServerPlayer target){
        if(action>0){stop();target=grabbed==null?null:level.getServer().getPlayerList().getPlayer(grabbed);if(--action==8&&target!=null&&target.level()==level&&valid(target)&&mob.distanceToSqr(target)<9&&mob.hasLineOfSight(target)){
            FixedPlayerDamage.panzerFlame(target,mob,(float)Math.min(number("astronautDamage"),Math.max(0,target.getHealth()-1)));
            var effects=new ArrayList<net.minecraft.world.effect.MobEffect>();for(String id:PerksManager.ALL_PERKS.keySet()){var effect=PerksManager.getEffectInstance(id);if(effect!=null&&target.hasEffect(effect))effects.add(effect);}
            if(!effects.isEmpty()&&number("astronautStealPerk")>0)target.removeEffect(effects.get(level.random.nextInt(effects.size())));
            astronautBurst(level,false);
            Vec3 destination=safeDestination(target,24);
            if(destination!=null){teleport(target,destination);level.sendParticles(ParticleTypes.EXPLOSION,destination.x,destination.y+1,destination.z,1,0,0,0,0);event("OnAstronautTeleport",mob.getUUID().toString(),target.getUUID().toString(),destination.x,destination.y,destination.z);}
            final var victim=target;me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->victim),new me.cryo.zombierool.network.packet.S2CScreenShakePacket(8,1.5f,me.cryo.zombierool.client.ScreenShakeHandler.ShakeType.IMPACT));
            EncounterAudio.play(mob,"astronaut_attack",.8f);
        }return;}
        if(target!=null&&cooldown<=0&&mob.distanceToSqr(target)<3.2&&mob.hasLineOfSight(target)){grabbed=target.getUUID();action=48;cooldown=120;mob.playBossAnimation("headbutt",48);target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,6));event("OnAstronautGrab",mob.getUUID().toString(),target.getUUID().toString());}
    }
    /** Cosmetic pressure wave with physical knockback, never terrain damage. */
    public void astronautBurst(ServerLevel level,boolean death){
        level.sendParticles(ParticleTypes.EXPLOSION,mob.getX(),mob.getY()+1,mob.getZ(),1,0,0,0,0);
        level.sendParticles(ParticleTypes.POOF,mob.getX(),mob.getY()+1,mob.getZ(),28,.5,.7,.5,.15);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,mob.getX(),mob.getY()+1,mob.getZ(),18,.6,.6,.6,.12);
        if(death){level.playSound(null,mob.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.HOSTILE,1,.8f);for(var p:level.players())if(valid(p)&&mob.distanceToSqr(p)<36&&mob.hasLineOfSight(p)){
            Vec3 direction=p.position().subtract(mob.position()).multiply(1,0,1);if(direction.lengthSqr()<.001)direction=mob.getLookAngle().multiply(1,0,1);
            double force=1.4*(1-mob.distanceTo(p)/6);Vec3 push=direction.normalize().scale(force).add(0,.35+force*.25,0);p.setDeltaMovement(p.getDeltaMovement().add(push));p.hurtMarked=true;
        }}
    }
    /** A bounded flood through standing volumes: no destination beyond a closed door or unloaded chunk. */
    public static Vec3 safeDestination(ServerPlayer player,int radius){
        ServerLevel level=player.serverLevel();BlockPos origin=player.blockPosition();
        var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();var choices=new ArrayList<Vec3>();queue.add(origin);seen.add(origin);
        while(!queue.isEmpty()&&seen.size()<2048){BlockPos from=queue.remove();
            for(Direction direction:Direction.Plane.HORIZONTAL){BlockPos next=null;
                for(int dy:new int[]{0,1,-1}){BlockPos feet=from.relative(direction).offset(0,dy,0);if(seen.contains(feet)||Math.abs(feet.getX()-origin.getX())>radius||Math.abs(feet.getZ()-origin.getZ())>radius||Math.abs(feet.getY()-origin.getY())>6||!level.hasChunkAt(feet))continue;
                    Vec3 pos=Vec3.atBottomCenterOf(feet);AABB box=new AABB(pos.x-.31,pos.y+.01,pos.z-.31,pos.x+.31,pos.y+1.81,pos.z+.31);
                    AABB passage=box.minmax(new AABB(from.getX()+.19,from.getY()+.01,from.getZ()+.19,from.getX()+.81,from.getY()+1.81,from.getZ()+.81));
                    if(!level.getWorldBorder().isWithinBounds(box)||level.getBlockCollisions(player,box).iterator().hasNext()||level.getBlockCollisions(player,passage).iterator().hasNext()||!level.getFluidState(feet).isEmpty()||level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty())continue;
                    next=feet;break;
                }
                if(next!=null&&seen.add(next)){queue.add(next);if(next.distSqr(origin)>=64)choices.add(Vec3.atBottomCenterOf(next));}
            }
        }
        Collections.shuffle(choices,new Random(level.random.nextLong()));
        for(Vec3 p:choices)if(level.noCollision(player,player.getBoundingBox().move(p.subtract(player.position()))))return p;
        return null;
    }
    private void abomination(ServerLevel level,ServerPlayer target){
        if(plague>0){stop();--plague;if(plague>50){if(plague%5==0)level.sendParticles(ParticleTypes.SNEEZE,mob.getX(),mob.getEyeY()-.5,mob.getZ(),3,.15,.15,.15,.01);return;}if(plague==50)EncounterAudio.play(mob,"abomination_belch",.85f);if(plague%4==0){Vec3 origin=mob.getEyePosition().add(0,-.5,0);for(int i=1;i<=6;i++){Vec3 p=origin.add(aim.scale(i));level.sendParticles(ParticleTypes.SNEEZE,p.x,p.y,p.z,2,.15+i*.04,.12,.15+i*.04,.02);}
            for(var p:level.players())if(valid(p)&&mob.hasLineOfSight(p)){Vec3 delta=p.getBoundingBox().getCenter().subtract(origin);double along=delta.dot(aim);if(along>0&&along<6&&delta.subtract(aim.scale(along)).length()<.7+along*.12){p.getPersistentData().putLong("zr_plague_until",level.getGameTime()+40);p.getPersistentData().putUUID("zr_plague_source",mob.getUUID());}}
        }return;}
        if(hooked!=null){var p=level.getServer().getPlayerList().getPlayer(hooked);if(p==null||p.level()!=level||!valid(p)||remainingPull<=0||!mob.hasLineOfSight(p)||mob.distanceToSqr(p)<6.25){hooked=null;hook=null;return;}
            Vec3 delta=mob.position().subtract(p.position()).normalize().scale(Math.min(.4,remainingPull));if(!level.noCollision(p,p.getBoundingBox().move(delta))){hooked=null;hook=null;return;}Vec3 next=p.position().add(delta);teleport(p,next);p.setDeltaMovement(Vec3.ZERO);remainingPull-=delta.length();return;
        }
        if(hook!=null){previousHook=hook;hook=hook.add(aim.scale(1.9));var hit=level.clip(new ClipContext(previousHook,hook,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob));if(hit.getType()!=HitResult.Type.MISS||hook.distanceToSqr(mob.getEyePosition())>576){hook=null;return;}
            for(var p:level.players())if(valid(p)&&p.getBoundingBox().inflate(.7).clip(previousHook,hook).isPresent()){hooked=p.getUUID();remainingPull=number("abominationPullDistance");event("OnAbominationHook",mob.getUUID().toString(),p.getUUID().toString(),remainingPull);break;}
            level.sendParticles(ParticleTypes.CRIT,hook.x,hook.y,hook.z,1,0,0,0,0);return;
        }
        if(action>0){stop();if(action>3&&target!=null&&mob.hasLineOfSight(target))aim=hookAim(target);if(--action==0){hook=mob.getEyePosition();previousHook=hook;EncounterAudio.play(mob,"abomination_chain",.85f);}return;}
        if(target==null||cooldown>0||!mob.hasLineOfSight(target))return;
        aim=hookAim(target);cooldown=65;
        if(mob.distanceToSqr(target)>25){action=12;mob.playBossAnimation("hook",32);EncounterAudio.play(mob,"abomination_attack",.8f);}
        else {plague=70;cooldown=110;mob.playBossAnimation("vomit",70);EncounterAudio.play(mob,"abomination_idle",.8f);event("OnAbominationPlague",mob.getUUID().toString());}
    }
    private Vec3 hookAim(ServerPlayer target){return target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(Math.min(6,mob.distanceTo(target)/1.9))).subtract(mob.getEyePosition()).normalize();}
    private void geist(ServerLevel level,ServerPlayer target){mob.crawlerLeap.tick(level);}
    public boolean attack(Entity entity){
        if(entity instanceof LivingEntity target&&!(target instanceof net.minecraft.world.entity.player.Player)&&MapActorBehavior.enemies(mob,target)&&MapActorBehavior.faction(mob).equals("scourge")){
            if(kind(mob).equals("geist")&&mob.crawlerLeap.busy())return false;
            boolean hit=target.hurt(mob.damageSources().mobAttack(mob),(float)mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            if(hit){mob.playBossAnimation("melee",24);EncounterAudio.play(mob,kind(mob)+"_attack",.7f);}
            return hit;
        }
        if(!(entity instanceof ServerPlayer player)||!valid(player))return false;
        String kind=kind(mob);
        if(kind.equals("pentagon_thief")){if(cooldown>0||stolen.stream().anyMatch(t->((CompoundTag)t).getUUID("owner").equals(player.getUUID())))return false;
            ItemStack held=player.getMainHandItem();if(!me.cryo.zombierool.core.system.WeaponFacade.isWeapon(held))return false;
            var entry=new CompoundTag();entry.putUUID("owner",player.getUUID());entry.put("stack",held.save(new CompoundTag()));stolen.add(entry);stoleWeapon=true;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.getInventory().setChanged();player.containerMenu.broadcastChanges();
            player.getPersistentData().putUUID("zr_thief_look",mob.getUUID());player.getPersistentData().putLong("zr_thief_look_until",mob.level().getGameTime()+20);
            EncounterAudio.play(mob,"thief_steal",.9f);sound(me.cryo.zombierool.init.ZombieroolModSounds.encounter("thief_teleport_out"),1);cooldown=40;flee=(int)number("thiefEscapeTicks");escaping=true;escapeRepath=0;escapePad=null;mob.setTarget(null);stop();me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(mob);event("OnThiefWeaponStolen",mob.getUUID().toString(),player.getUUID().toString());
            Vec3 destination=safeDestination(player,20);if(destination!=null){for(Vec3 offset:List.of(new Vec3(2,0,0),new Vec3(-2,0,0),new Vec3(0,0,2),new Vec3(0,0,-2))){Vec3 next=destination.add(offset);if(mob.level().noCollision(mob,mob.getBoundingBox().move(next.subtract(mob.position())))){teleport(player,destination);mob.teleportTo(next.x,next.y,next.z);break;}}}
            return false;
        }
        if(kind.equals("astronaut")||kind.equals("geist")&&mob.crawlerLeap.busy())return false;
        if(kind.equals("george_romero")){if(rage<=0){provoke();return false;}if(action>0||cooldown>0)return false;romeroAction="melee";action=41;cooldown=55;aim=player.position().subtract(mob.position()).normalize();mob.playBossAnimation("melee",41);return false;}
        if(kind.equals("abomination"))EncounterAudio.play(mob,"abomination_attack",.8f);
        mob.playBossAnimation("melee",20);return FixedPlayerDamage.panzerFlame(player,mob,(float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }
    private void thief(ServerLevel level,ServerPlayer target){
        if(mob.tickCount%4==0)level.sendParticles(me.cryo.zombierool.init.ZombieroolModParticleTypes.THIEF_DIGITS.get(),mob.getX(),mob.getY()+1,mob.getZ(),3,.35,.8,.35,.01);
        if(stolen.isEmpty()){if(mob.tickCount>2400){escaped=true;EncounterAudio.play(mob,"thief_escape",1);event("OnThiefEscaped",mob.getUUID().toString());WaveManager.onMobDeath(mob,level);mob.discard();}return;}
        if(--flee<=0){escaped=true;EncounterAudio.play(mob,"thief_escape",1);event("OnThiefEscaped",mob.getUUID().toString());WaveManager.onMobDeath(mob,level);mob.discard();return;}
        // Leave the robbed victim immediately, then seek a different player only after the escape hop.
        if(!escaping){if(target!=null&&!mayTarget(target)){mob.setTarget(null);stop();}return;}
        mob.setTarget(null);
        var pads=me.cryo.zombierool.block.system.TeleporterRegistry.get(level).pads;
        if(escapePad!=null&&teleportCooldown==0&&mob.distanceToSqr(Vec3.atBottomCenterOf(escapePad))<6.25){
            stop();if(teleportWindup==0){teleportWindup=32;mob.playBossAnimation("teleport",32);}if(--teleportWindup>0)return;
            var settings=pads.get(escapePad);
            if(settings!=null)for(var exit:pads.entrySet()){
                if(exit.getKey().equals(escapePad)||!exit.getValue().getString("id").equals(settings.getString("destination"))||!exit.getValue().getBoolean("enabled")||!level.hasChunkAt(exit.getKey()))continue;
                Vec3 to=Vec3.atBottomCenterOf(exit.getKey().above());
                if(!level.noCollision(mob,mob.getBoundingBox().move(to.subtract(mob.position()))))continue;
                mob.teleportTo(to.x,to.y,to.z);stop();sound(me.cryo.zombierool.init.ZombieroolModSounds.ENCOUNTER_TELEPORT.get(),1);
                escapePad=null;escapeRepath=20;teleportCooldown=120;
                if(flee>60&&level.players().stream().anyMatch(p->valid(p)&&!hasStolenFrom(p.getUUID())&&me.cryo.zombierool.core.system.WeaponFacade.isWeapon(p.getMainHandItem()))){escaping=false;me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(mob);}
                return;
            }
            escapePad=null;
        }
        else teleportWindup=0;
        if(escapeRepath-->0&&!mob.getNavigation().isDone())return;
        escapeRepath=20;
        net.minecraft.world.level.pathfinder.Path best=null;double score=-Double.MAX_VALUE;escapePad=null;
        // A path must actually reach the pad. Never pick an inaccessible teleporter through a wall.
        if(teleportCooldown==0)for(var entry:pads.entrySet()){
            BlockPos pos=entry.getKey();if(!entry.getValue().getBoolean("enabled")||!level.hasChunkAt(pos)||mob.distanceToSqr(Vec3.atCenterOf(pos))>1024)continue;
            String destination=entry.getValue().getString("destination");if(destination.isBlank()||pads.entrySet().stream().noneMatch(e->!e.getKey().equals(pos)&&e.getValue().getString("id").equals(destination)&&e.getValue().getBoolean("enabled")))continue;
            var path=mob.getNavigation().createPath(pos,1);if(path==null||!path.canReach())continue;
            double value=1000-path.getNodeCount();if(value>score){best=path;score=value;escapePad=pos;}
        }
        if(best==null){
            // Score reachable routes by separation from players, rather than walking blindly into a wall.
            for(int i=0;i<12;i++){
                double angle=i*Math.PI/6;BlockPos pos=mob.blockPosition().offset((int)(Math.cos(angle)*10),0,(int)(Math.sin(angle)*10));
                if(!level.hasChunkAt(pos))continue;var path=mob.getNavigation().createPath(pos,2);if(path==null||!path.canReach()||path.getEndNode()==null)continue;
                Vec3 end=Vec3.atBottomCenterOf(path.getEndNode().asBlockPos());double separation=level.players().stream().filter(this::valid).mapToDouble(p->p.position().distanceToSqr(end)).min().orElse(0);
                double value=separation-path.getNodeCount()*.2;if(value>score){score=value;best=path;}
            }
        }
        if(best!=null)mob.getNavigation().moveTo(best,1.15);
    }

    /** A clean kill rewards the whole team once, even if the death callback repeats. */
    public void rewardCleanThiefKill(){
        if(!kind(mob).equals("pentagon_thief")||stoleWeapon||!stolen.isEmpty()||escaped||cleanKillRewarded||!WaveManager.isGameRunning()||!(mob.level() instanceof ServerLevel level))return;
        cleanKillRewarded=true;
        var players=level.getServer().getPlayerList().getPlayers();
        for(var player:players)IngotSaleDrops.grantTeamReward(player);
        event("OnThiefCleanKill",mob.getUUID().toString(),players.size());
    }
    public void returnWeapons(){if(returned||escaped||!(mob.level() instanceof ServerLevel level))return;returned=true;for(Tag tag:stolen){var e=(CompoundTag)tag;ItemStack stack=ItemStack.of(e.getCompound("stack"));if(stack.isEmpty())continue;UUID owner=e.getUUID("owner");var player=level.getServer().getPlayerList().getPlayer(owner);if(player!=null){player.getInventory().add(stack);player.containerMenu.broadcastChanges();}if(!stack.isEmpty()){var drop=new ItemEntity(level,mob.getX(),mob.getY()+.5,mob.getZ(),stack);drop.setTarget(owner);drop.setUnlimitedLifetime();level.addFreshEntity(drop);}event("OnThiefWeaponReturned",mob.getUUID().toString(),owner.toString());}stolen.clear();}
    public CompoundTag save(){var t=new CompoundTag();t.putInt("rage",rage);t.putInt("cooldown",cooldown);t.putInt("flee",flee);t.putInt("teleportCooldown",teleportCooldown);t.putBoolean("escaping",escaping);t.putBoolean("stoleWeapon",stoleWeapon);t.putBoolean("cleanKillRewarded",cleanKillRewarded);t.put("stolen",stolen.copy());return t;}
    public void load(CompoundTag t){rage=t.getInt("rage");cooldown=t.getInt("cooldown");flee=t.getInt("flee");teleportCooldown=Math.max(0,Math.min(120,t.getInt("teleportCooldown")));escaping=t.getBoolean("escaping");stolen.clear();stolen.addAll(t.getList("stolen",10));stoleWeapon=t.getBoolean("stoleWeapon")||!stolen.isEmpty();cleanKillRewarded=t.getBoolean("cleanKillRewarded");}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void playerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!WaveManager.isGameRunning()||WaveManager.isPausedByPlayer())return;long now=p.level().getGameTime();if(!p.isAlive()||p.isCreative()||p.isSpectator())return;
        var data=p.getPersistentData();if(data.getLong("zr_plague_until")>now&&now-data.getLong("zr_plague_last_damage")>=30){data.putLong("zr_plague_last_damage",now);FixedPlayerDamage.apply(p,.5f);}
        if(data.hasUUID("zr_thief_look")){var thief=p.serverLevel().getEntity(data.getUUID("zr_thief_look"));if(data.getLong("zr_thief_look_until")<=now||thief==null||!thief.isAlive()){data.remove("zr_thief_look");data.remove("zr_thief_look_until");}else{Vec3 delta=thief.getEyePosition().subtract(p.getEyePosition());float yaw=(float)(Math.atan2(delta.z,delta.x)*180/Math.PI)-90,pitch=-(float)(Math.atan2(delta.y,delta.horizontalDistance())*180/Math.PI);p.setYRot(yaw);p.setXRot(pitch);p.connection.teleport(p.getX(),p.getY(),p.getZ(),yaw,pitch);}}
    }
}

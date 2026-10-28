package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** Shared acquisition rules for players, Scourge and Risen; retaliation belongs to one mob. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class MapActorBehavior {
    private static final java.util.Map<ServerLevel,PatrolQueue> PATROLS=new java.util.WeakHashMap<>();
    private static final class PatrolProgress {Vec3 point;long tick;PatrolProgress(Vec3 point,long tick){this.point=point;this.tick=tick;}}
    private static final class PatrolQueue {final java.util.ArrayDeque<java.util.UUID> pending=new java.util.ArrayDeque<>();final java.util.Set<java.util.UUID> waiting=new java.util.HashSet<>();final java.util.Map<java.util.UUID,PatrolProgress> progress=new java.util.HashMap<>();}
    public static void requestPatrol(Mob mob,CompoundTag profile){
        if(!(mob.level() instanceof ServerLevel level)||profile.getDouble("wanderRadius")<=0||mob.getTarget()!=null||mob.isNoAi())return;
        var queue=PATROLS.computeIfAbsent(level,k->new PatrolQueue());long now=level.getGameTime();
        var progress=queue.progress.computeIfAbsent(mob.getUUID(),id->new PatrolProgress(mob.position(),now));
        if(now-progress.tick>=40){if(!mob.getNavigation().isDone()&&mob.position().distanceToSqr(progress.point)<.04)mob.getNavigation().stop();progress.point=mob.position();progress.tick=now;}
        if(mob.getNavigation().isDone()&&queue.waiting.add(mob.getUUID()))queue.pending.addLast(mob.getUUID());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void patrolTick(net.minecraftforge.event.TickEvent.LevelTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||WaveManager.isPausedByPlayer())return;
        var queue=PATROLS.get(level);if(queue==null)return;long started=System.nanoTime();
        for(int n=0;n<4&&!queue.pending.isEmpty();n++){
            if(n>0&&System.nanoTime()-started>=2_000_000)break;
            var id=queue.pending.removeFirst();queue.waiting.remove(id);
            if(!(level.getEntity(id) instanceof Mob mob)||!mob.isAlive()||mob.isNoAi())continue;
            var p=MapKnifeRules.profile(mob);Vec3 home=mob instanceof me.cryo.zombierool.entity.MapActorEntity actor?actor.home():new Vec3(mob.getPersistentData().getDouble("MapHomeX"),mob.getPersistentData().getDouble("MapHomeY"),mob.getPersistentData().getDouble("MapHomeZ"));
            patrol(mob,p,home);
            if(mob.getNavigation() instanceof RouteNavigation nav&&nav.planningDeferred()
                &&mob.getTarget()==null&&p.getDouble("wanderRadius")>0&&queue.waiting.add(id))queue.pending.addLast(id);
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void patrolUnload(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)PATROLS.remove(level);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void patrolLeft(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){if(event.getLevel() instanceof ServerLevel level){var queue=PATROLS.get(level);if(queue!=null){queue.progress.remove(event.getEntity().getUUID());queue.waiting.remove(event.getEntity().getUUID());}}}
    public static final java.util.List<String> BEHAVIOR_FIELDS=java.util.List.of("behavior","aggroRadius","wanderRadius","leashRadius","speed","speedMode","stayNearSpawn","healthMode","faction","knifeMode","knifeHits","wavePursuit");
    public static void settings(CompoundTag in,CompoundTag out){
        String mode=in.getString("speedMode");if(!java.util.List.of("custom","walk","trot","run").contains(mode))mode="walk";
        out.putString("speedMode",mode);out.putBoolean("stayNearSpawn",!in.contains("stayNearSpawn")||in.getBoolean("stayNearSpawn"));
        out.putString("healthMode",in.contains("healthMode")?in.getString("healthMode").equals("wave")?"wave":"fixed":in.getBoolean("boss")?"fixed":"wave");
        String faction=in.getString("faction");if(faction.isEmpty()){String id=out.getString("id"),base=in.getString("baseEntity");faction=id.startsWith("scarlet_zombie")||id.equals("balnazzar")?"risen":id.equals("baron_rivendare")||id.equals("crypt_fiend")||id.equals("raised_skeleton")||base.startsWith("scourge_")||(base.equals("abomination")||base.equals("geist"))?"scourge":"hostile";}if(out.getString("id").equals("baron_rivendare"))faction="scourge";if(out.getString("id").equals("balnazzar"))faction="risen";out.putString("faction",faction);
    }
    public static float waveHealth(ServerLevel level){var config=me.cryo.zombierool.config.WorldConfig.get(level);int wave=Math.max(1,WaveManager.getCurrentWave());float hp=config.getZombieBaseHealth();if(wave>=2)hp=wave<=9?hp+wave*4:(hp+36)*(float)Math.pow(1.15,wave-9);return Math.min(hp,config.getZombieMaxHealth());}
    public static double speed(CompoundTag p,ServerLevel level){return switch(p.getString("speedMode")){case "walk"->me.cryo.zombierool.entity.ZombieEntity.DEFAULT_MOVEMENT_SPEED;case "trot"->.23;case "run"->me.cryo.zombierool.config.WorldConfig.get(level).getZombieSprintSpeed();default->p.getDouble("speed");};}
    public static boolean eligible(ServerPlayer p){return p!=null&&p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID());}
    public static String faction(LivingEntity e){if(MapActors.owns(e)){var p=MapKnifeRules.profile(e);if(p.getString("id").equals("baron_rivendare")||p.getString("id").equals("scourge_necromancer"))return "scourge";if(p.getString("id").equals("balnazzar"))return "risen";if(p.getString("baseEntity").equals("geist"))return "scourge";return p.getString("faction");}if(e instanceof me.cryo.zombierool.entity.ZombieEntity zombie&&me.cryo.zombierool.config.EnemySkinPresets.blockyModel(zombie.getCustomSkin()).startsWith("scourge_"))return "scourge";if(e instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss&&(boss.getType()==me.cryo.zombierool.init.ZombieroolModEntities.GEIST.get()||boss.bossProfile().getString("template").equals("abomination")))return "scourge";return "";}
    private static boolean neutralRetaliation(LivingEntity a,LivingEntity b){return b!=null&&MapActors.owns(a)&&faction(a).equals("neutral")&&a.getPersistentData().hasUUID("MapRetaliation")&&a.getPersistentData().getUUID("MapRetaliation").equals(b.getUUID());}
    public static boolean enemies(LivingEntity a,LivingEntity b){if(neutralRetaliation(a,b))return true;if(MechanicalGreeter.ally(a))return MechanicalGreeter.undead(b);if(MechanicalGreeter.ally(b))return MechanicalGreeter.undead(a);String first=faction(a),second=faction(b);return first.equals("risen")&&second.equals("scourge")||first.equals("scourge")&&second.equals("risen");}
    public static double gapSquared(LivingEntity a,LivingEntity b){var x=a.getBoundingBox();var y=b.getBoundingBox();double dx=Math.max(0,Math.max(x.minX-y.maxX,y.minX-x.maxX)),dy=Math.max(0,Math.max(x.minY-y.maxY,y.minY-x.maxY)),dz=Math.max(0,Math.max(x.minZ-y.maxZ,y.minZ-x.maxZ));return dx*dx+dy*dy+dz*dz;}
    public static boolean visible(Mob mob,LivingEntity target){if(mob.hasLineOfSight(target))return true;var from=mob.position().add(0,Math.min(1.1,mob.getBbHeight()*.6),0);var to=target.position().add(0,Math.min(1.1,target.getBbHeight()*.6),0);return mob.level().clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mob)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;}
    public static boolean inMeleeRange(Mob mob,LivingEntity target,double reach){return gapSquared(mob,target)<=reach*reach&&visible(mob,target);}
    public static boolean eligible(Mob mob,LivingEntity target,double radius){return pursuable(mob,target)&&gapSquared(mob,target)<=radius*radius&&visible(mob,target);}
    /** Damage reveals the attacker; detection radius and visibility only apply to initial acquisition. */
    public static boolean pursuable(Mob mob,LivingEntity target){return target!=null&&target!=mob&&target.isAlive()&&target.level()==mob.level()&&(!MechanicalGreeter.ally(mob)||!(target instanceof ServerPlayer))&&(target instanceof ServerPlayer p?eligible(p)&&!me.cryo.zombierool.bonuses.BonusManager.isZombieBloodActive(p)&&NavigationArea.allowsPursuit(p):enemies(mob,target));}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void blood(net.minecraftforge.event.entity.living.LivingAttackEvent e){
        if(e.getEntity() instanceof ServerPlayer player&&me.cryo.zombierool.bonuses.BonusManager.isZombieBloodActive(player)&&e.getSource().getEntity() instanceof Mob mob&&MapActors.owns(mob))e.setCanceled(true);
    }
    /** The spawn tether bounds idle wandering only. Pursuit uses the independent leash. */
    public static double boundary(CompoundTag p){return p.getBoolean("wavePursuit")?0:p.getDouble("leashRadius");}
    public static LivingEntity target(Mob mob,CompoundTag p,Vec3 home){
        var level=(ServerLevel)mob.level();double radius=p.getDouble("aggroRadius");
        var state=mob.getPersistentData();
        if(p.getBoolean("wavePursuit")){
            // An authored wave-style arrival locks a valid target without radius or LOS
            // acquisition limits. Only already loaded entities are considered.
            if(pursuable(mob,mob.getTarget()))return mob.getTarget();
            long now=level.getGameTime();if(state.getLong("MapNextGlobalAcquire")>now)return null;
            state.putLong("MapNextGlobalAcquire",now+20);
            LivingEntity closest=null;double best=Double.MAX_VALUE;
            for(var player:level.players())if(pursuable(mob,player)){double distance=gapSquared(mob,player);if(distance<best){best=distance;closest=player;}}
            for(var entity:level.getAllEntities())if(entity instanceof LivingEntity candidate&&!(candidate instanceof ServerPlayer)&&pursuable(mob,candidate)){double distance=gapSquared(mob,candidate);if(distance<best){best=distance;closest=candidate;}}
            return closest;
        }
        if(MapActors.owns(mob)&&state.hasUUID("MapRetaliation")){var entity=level.getEntity(state.getUUID("MapRetaliation"));if(entity instanceof LivingEntity target&&pursuable(mob,target))return target;state.remove("MapRetaliation");}
        if(p.getString("behavior").equals("wander")||p.getString("faction").equals("neutral"))return null;
        if((MapActors.owns(mob)||faction(mob).equals("scourge"))&&pursuable(mob,mob.getTarget()))return mob.getTarget();
        return java.util.stream.Stream.concat(level.players().stream(),level.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(radius),target->!(target instanceof ServerPlayer)).stream()).filter(target->eligible(mob,target,radius)).min(java.util.Comparator.comparingDouble((LivingEntity target)->gapSquared(mob,target))).orElse(null);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void hit(net.minecraftforge.event.entity.living.LivingDamageEvent e){
        if(e.getAmount()>0&&e.getEntity() instanceof Mob mob&&MapActors.owns(mob)&&e.getSource().getEntity() instanceof LivingEntity attacker&&(pursuable(mob,attacker)||faction(mob).equals("neutral")&&attacker!=mob&&attacker.isAlive()&&(!(attacker instanceof ServerPlayer player)||eligible(player)))){mob.getPersistentData().putUUID("MapRetaliation",attacker.getUUID());mob.setTarget(attacker);}
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void factions(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e){
        if(!(e.getEntity() instanceof Mob mob)||!(mob.level() instanceof ServerLevel level)||MapActors.owns(mob)||mob.tickCount%2!=0||!faction(mob).equals("scourge")||WaveManager.isPausedByPlayer())return;
        var p=new CompoundTag();p.putDouble("aggroRadius",mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)==null?16:mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));var target=target(mob,p,mob.position());if(target!=null)mob.setTarget(target);else if(mob.getTarget()!=null&&enemies(mob,mob.getTarget()))mob.setTarget(null);
    }
    public static Vec3 wanderCenter(Mob mob,CompoundTag p,Vec3 home){return p.getBoolean("stayNearSpawn")?home:mob.position();}
    /** Patrol also applies to local guards. Zero radius explicitly keeps a sentry still. */
    public static boolean patrol(Mob mob,CompoundTag p,Vec3 home){
        double radius=p.getDouble("wanderRadius");
        if(radius<=0||mob.getTarget()!=null||!mob.getNavigation().isDone()||p.getString("behavior").equals("script"))return false;
        var level=mob.level();var center=wanderCenter(mob,p,home);
        double leash=boundary(p);if(p.getBoolean("stayNearSpawn")&&leash>0)radius=Math.min(radius,leash);
        for(int attempt=0;attempt<4;attempt++){
            double angle=mob.getRandom().nextDouble()*Math.PI*2,d=Math.sqrt(mob.getRandom().nextDouble())*radius;
            double x=center.x+Math.cos(angle)*d,z=center.z+Math.sin(angle)*d;
            // Sample supported feet positions, including stairs and raised streets.
            for(int y=net.minecraft.util.Mth.floor(mob.getY())+3;y>=net.minecraft.util.Mth.floor(mob.getY())-4;y--){
                var floor=net.minecraft.core.BlockPos.containing(x,y,z);
                if(!level.hasChunkAt(floor))continue;
                var shape=level.getBlockState(floor).getCollisionShape(level,floor);if(shape.isEmpty())continue;
                var at=new Vec3(x,y+shape.max(net.minecraft.core.Direction.Axis.Y),z);
                if(at.distanceToSqr(mob.position())<1||leash>0&&at.distanceToSqr(home)>leash*leash
                    ||!NavigationArea.allows(level,net.minecraft.core.BlockPos.containing(at))
                    ||!level.noCollision(mob,mob.getBoundingBox().move(at.subtract(mob.position())).deflate(.001)))continue;
                var route=mob.getNavigation() instanceof RouteNavigation nav?nav.createPatrolPath(at):mob.getNavigation().createPath(at.x,at.y,at.z,0);
                if(mob.getNavigation() instanceof RouteNavigation nav&&nav.planningDeferred())return false;
                if(route!=null&&route.canReach()&&mob.getNavigation().moveTo(route,1))return true;
            }
        }
        return false;
    }
    private MapActorBehavior(){}
}

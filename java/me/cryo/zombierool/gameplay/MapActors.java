package me.cryo.zombierool.gameplay;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import me.cryo.zombierool.entity.MapActorEntity;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.nio.file.*;
import java.util.*;

/** Map-owned definitions and encounter markers. No global entity catalogue entries. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class MapActors extends SavedData {
    private static final Map<ServerLevel,Set<net.minecraft.world.entity.projectile.Arrow>> BOLTS=new WeakHashMap<>();
    @SubscribeEvent public static void boltJoined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){if(event.getLevel() instanceof ServerLevel level&&event.getEntity() instanceof net.minecraft.world.entity.projectile.Arrow bolt&&bolt.getPersistentData().getBoolean("zr_actor_bolt"))BOLTS.computeIfAbsent(level,key->new HashSet<>()).add(bolt);}
    @SubscribeEvent public static void boltsUnloaded(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)BOLTS.remove(level);}
    private final CompoundTag profiles = new CompoundTag();
    private final CompoundTag markers = new CompoundTag();
    private final Map<String, String> triggered = new HashMap<>();
    private record SpawnBatch(String token,int remaining,int cursor){}
    private final Map<String,SpawnBatch> pendingBatches=new HashMap<>();
    // Match-local ledger: chunk unloading is not a death. Retain membership until match reset.
    private final Map<UUID,Boolean> questActors=new HashMap<>();
    private final Map<String,Set<UUID>> questMembers=new HashMap<>();
    public String trackQuestActor(String uuid){
        if(!uuid.isEmpty()&&running)questActors.putIfAbsent(UUID.fromString(uuid),true);
        return uuid;
    }
    private String remember(String uuid,String marker){
        if(!uuid.isEmpty()&&running&&!marker.isEmpty()&&markers.getCompound(marker).getString("trigger").equals("game_start")){
            trackQuestActor(uuid);questMembers.computeIfAbsent(marker,k->new HashSet<>()).add(UUID.fromString(uuid));
        }
        return uuid;
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void questDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event){
        if(event.getEntity().level() instanceof ServerLevel level&&owns(event.getEntity())){
            var data=get(level);if(data.questActors.containsKey(event.getEntity().getUUID()))data.questActors.put(event.getEntity().getUUID(),false);
        }
    }
    public String questActorState(String uuid){try{Boolean alive=questActors.get(UUID.fromString(uuid));return alive==null?"unknown":alive?"alive":"dead";}catch(IllegalArgumentException e){return "unknown";}}
    public org.luaj.vm2.LuaTable questStatus(BlockPos min,BlockPos max){
        var out=new org.luaj.vm2.LuaTable();int count=0,invalid=0,pending=0,alive=0,spawned=0,expected=0;
        for(String key:markers.getAllKeys()){
            BlockPos pos=BlockPos.of(Long.parseLong(key));
            if(pos.getX()<min.getX()||pos.getX()>max.getX()||pos.getY()<min.getY()||pos.getY()>max.getY()||pos.getZ()<min.getZ()||pos.getZ()>max.getZ())continue;
            var rule=markers.getCompound(key);if(!rule.getBoolean("enabled"))continue;count++;expected+=rule.getInt("batch");
            if(!rule.getString("trigger").equals("game_start"))invalid++;
            if(!Objects.equals(triggered.get(key),session+":start")||pendingBatches.containsKey(key))pending++;
            var members=questMembers.getOrDefault(key,Set.of());spawned+=members.size();
            for(UUID id:members)if(Boolean.TRUE.equals(questActors.get(id)))alive++;
        }
        out.set("expected",expected);out.set("unspawned",Math.max(0,expected-spawned));out.set("markers",count);out.set("invalid",invalid);out.set("pending",pending);out.set("alive",alive);out.set("spawned",spawned);
        out.set("complete",org.luaj.vm2.LuaValue.valueOf(running&&count>0&&invalid==0&&pending==0&&spawned>0&&alive==0));return out;
    }
    private record QuestArea(BlockPos min,BlockPos max){
        boolean core(BlockPos p){return Math.abs(p.getX()-(min.getX()+max.getX())*.5)<=3&&Math.abs(p.getZ()-(min.getZ()+max.getZ())*.5)<=3&&contains(p);}
        boolean contains(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    }
    private final Map<String,QuestArea> questAreas=new LinkedHashMap<>();
    private final Map<UUID,CompoundTag> parked=new LinkedHashMap<>();
    private double activationRadius,parkingRadius;
    private int questMarkerCursor;
    public void configureStreaming(double active,double sleep){
        if(!Double.isFinite(active)||!Double.isFinite(sleep)||active<32||active>96||sleep<active+16||sleep>192)throw new IllegalArgumentException("Streaming radii: active 32..96, parking active+16..192");
        activationRadius=active;parkingRadius=sleep;
    }
    public void questArea(String id,BlockPos min,BlockPos max){
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,48}")||questAreas.size()>=16&&!questAreas.containsKey(id)||min.getX()>max.getX()||min.getY()>max.getY()||min.getZ()>max.getZ()||max.getX()-min.getX()>64||max.getY()-min.getY()>32||max.getZ()-min.getZ()>64)throw new IllegalArgumentException("Invalid quest spawner area");
        questAreas.put(id,new QuestArea(min,max));
    }
    private QuestArea questArea(BlockPos pos){for(var area:questAreas.values())if(area.contains(pos))return area;return null;}
    private boolean protectedMarker(String key){try{return questArea(BlockPos.of(Long.parseLong(key)))!=null;}catch(NumberFormatException e){return false;}}
    public boolean spawnSpace(ServerLevel level,net.minecraft.world.entity.LivingEntity mob,String marker){
        return !marker.isEmpty()&&protectedMarker(marker)?!level.getBlockCollisions(mob,mob.getBoundingBox()).iterator().hasNext()&&level.getWorldBorder().isWithinBounds(mob.getBoundingBox()):level.noCollision(mob);
    }
    public boolean spawnOccupied(net.minecraft.world.entity.LivingEntity mob,String marker){
        var area=marker.isEmpty()?null:questArea(BlockPos.of(Long.parseLong(marker)));
        for(var other:mob.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,mob.getBoundingBox().inflate(.15,0,.15),e->e.isAlive())){
            // A small authored room can fill as a single group without its first guards
            // blocking the remaining guards. Players and unrelated groups still obstruct it.
            if(area!=null&&owns(other)&&!(other instanceof net.minecraft.world.entity.player.Player)){
                String key=other instanceof MapActorEntity actor?actor.marker():other.getPersistentData().getString("MapMarker");
                if(!key.isEmpty()&&area==questArea(BlockPos.of(Long.parseLong(key))))continue;
            }
            return true;
        }
        return false;
    }
    private void clearStreaming(){activationRadius=parkingRadius=0;questAreas.clear();parked.clear();questMarkerCursor=0;}
    private static boolean near(java.util.List<net.minecraft.server.level.ServerPlayer> players,Vec3 at,double radius){
        for(var player:players){double dx=player.getX()-at.x,dz=player.getZ()-at.z;if(dx*dx+dz*dz<=radius*radius&&Math.abs(player.getY()-at.y)<=24)return true;}return false;
    }
    private void questBehavior(CompoundTag profile){
        profile.putBoolean("stayNearSpawn",true);double leash=profile.getDouble("leashRadius");profile.putDouble("leashRadius",leash>0?Math.min(leash,10):10);profile.putDouble("wanderRadius",Math.min(profile.getDouble("wanderRadius"),3));
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void questActorJoined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)||!owns(mob))return;
        var data=get(level);
        if(mob.getPersistentData().getBoolean("matchActor")&&(!WaveManager.isGameRunning()||!data.running||!mob.getPersistentData().getString("MapMatchId").equals(data.matchIdentity))){mob.discard();event.setCanceled(true);return;}
        String key=mob instanceof MapActorEntity actor?actor.marker():mob.getPersistentData().getString("MapMarker");
        if(key.isEmpty()||!data.protectedMarker(key))return;
        var profile=MapKnifeRules.profile(mob).copy();data.questBehavior(profile);
        if(mob instanceof MapActorEntity actor)actor.applyBehaviorSettings(profile);else mob.getPersistentData().put("MapNativeProfile",profile);
    }
    /** Offscreen finite groups keep UUID, health and script state. Parking is never a kill or a fresh spawn. */
    public int stream(ServerLevel level,java.util.List<net.minecraft.server.level.ServerPlayer> players,long deadline){
        if(activationRadius<=0)return 0;int saved=0,restored=0;
        var candidates=new ArrayList<net.minecraft.world.entity.Mob>();
        for(var e:level.getAllEntities())if(e instanceof net.minecraft.world.entity.Mob mob&&owns(mob)&&mob.isAlive()&&!countsForWave(mob))candidates.add(mob);
        for(var mob:candidates){
            if(saved>=4||System.nanoTime()>deadline)break;
            String key=mob instanceof MapActorEntity actor?actor.marker():mob.getPersistentData().getString("MapMarker");
            if(key.isEmpty()||!markers.getCompound(key).getString("trigger").equals("game_start")||MapKnifeRules.profile(mob).getBoolean("boss")||near(players,mob.position(),parkingRadius)||(MapActorBehavior.pursuable(mob,mob.getTarget())&&(!(mob.getTarget() instanceof net.minecraft.world.entity.player.Player)||mob.distanceToSqr(mob.getTarget())<=parkingRadius*parkingRadius))||mob.hurtTime>0)continue;
            var tag=new CompoundTag();if(!mob.save(tag))continue;
            parked.put(mob.getUUID(),tag);mob.discard();saved++;
        }
        for(var it=parked.entrySet().iterator();it.hasNext();){
            if(restored>=4||System.nanoTime()>deadline||ActorPopulation.marked(level)>=ActorPopulation.MARKER_LIMIT||ActorPopulation.alive(level)>=ActorPopulation.MAX_ALIVE)break;
            var entry=it.next();var tag=entry.getValue();var pos=tag.getList("Pos",6);if(pos.size()!=3)continue;
            var at=new Vec3(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2));
            if(!near(players,at,activationRadius)||!level.hasChunkAt(BlockPos.containing(at)))continue;
            String key=tag.getString("MapMarker");if(key.isEmpty())key=tag.getCompound("ForgeData").getString("MapMarker");boolean objective=protectedMarker(key);
            if(!objective&&ActorPopulation.marked(level)>=ActorPopulation.MARKER_LIMIT-64)continue;
            if(level.getEntity(entry.getKey())!=null){it.remove();continue;}
            var entity=net.minecraft.world.entity.EntityType.loadEntityRecursive(tag.copy(),level,e->e);
            if(!(entity instanceof net.minecraft.world.entity.Mob mob)||!mob.isAlive())continue;
            if(!spawnSpace(level,mob,key)||spawnOccupied(mob,key))continue;
            if(!level.addFreshEntity(mob))continue;
            var profile=MapKnifeRules.profile(mob).copy();
            var original=tag.contains("MapProfile",10)?tag.getCompound("MapProfile"):tag.getCompound("ForgeData").getCompound("MapNativeProfile");
            for(String field:MapActorBehavior.BEHAVIOR_FIELDS)if(original.contains(field))profile.put(field,original.get(field).copy());
            if(objective)questBehavior(profile);
            if(mob instanceof MapActorEntity custom)custom.applyBehaviorSettings(profile);else{mob.getPersistentData().put("MapNativeProfile",profile);var speed=mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(speed!=null)speed.setBaseValue(MapActorBehavior.speed(profile,level));}
            ActorPopulation.spawned(mob);it.remove();restored++;
        }
        return restored;
    }
    private String matchIdentity="";
    public String matchIdentity(){return matchIdentity;}
    private boolean running;
    private int session;
    private boolean imported;
    private ServerLevel owner;
    private int markerCursor;
    private static final Map<Double,List<BlockPos>> SPAWN_OFFSETS=new LinkedHashMap<>(){
        @Override protected boolean removeEldestEntry(Map.Entry<Double,List<BlockPos>> entry){return size()>32;}
    };
    private static List<BlockPos> spawnOffsets(double radius){return SPAWN_OFFSETS.computeIfAbsent(radius,r->{var offsets=new ArrayList<BlockPos>();int extent=(int)Math.floor(r);for(int x=-extent;x<=extent;x++)for(int z=-extent;z<=extent;z++)if(x*x+z*z<=r*r)offsets.add(new BlockPos(x,0,z));offsets.sort(Comparator.comparingDouble((BlockPos p)->p.getX()*p.getX()+p.getZ()*p.getZ()).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));return List.copyOf(offsets);});}

    public static MapActors get(ServerLevel level) {
        var data=level.getDataStorage().computeIfAbsent(MapActors::load, MapActors::new, "zombierool_map_actors");
        data.owner=level;
        if(!data.imported){data.imported=true;try{data.importFiles(level);}catch(Exception e){me.cryo.zombierool.scripting.LuaScriptManager.log("Map entities: "+e.getMessage());}}
        return data;
    }
    private static MapActors load(CompoundTag n) {
        var data=new MapActors();data.profiles.merge(n.getCompound("profiles"));data.markers.merge(n.getCompound("markers"));return data;
    }
    @Override public CompoundTag save(CompoundTag n) {n.put("profiles",profiles.copy());n.put("markers",markers.copy());return n;}
    public CompoundTag profile(String id) {id=canonicalProfileId(id);return profiles.contains(id)?profiles.getCompound(id).copy():MapActorTemplates.builtin(id);}
    private String canonicalProfileId(String id){
        if(id.endsWith("_female")){
            String base=id.substring(0,id.length()-7);
            if(!profiles.getCompound(base).getList("appearances",10).isEmpty())return base;
        }
        return id;
    }
    private static final List<String> APPEARANCE_FIELDS=List.of("bones","boxes","animations","texture","textureWidth","textureHeight","scale","sourceMotion","sounds","dialogue","eyeR","eyeG","eyeB");
    /** Resolve once at spawn and keep only the chosen visual on the synchronized entity. */
    public static CompoundTag appearance(CompoundTag profile,int choice){
        var result=profile.copy();var alternatives=profile.getList("appearances",10);
        choice=Math.max(0,Math.min(choice,alternatives.size()));
        if(choice>0){var visual=alternatives.getCompound(choice-1);for(String field:APPEARANCE_FIELDS)if(visual.contains(field))result.put(field,visual.get(field).copy());}
        result.remove("appearances");result.putInt("appearance",choice);return result;
    }
    public Set<String> ids() {return new TreeSet<>(profiles.getAllKeys());}
    public Set<String> catalogueIds(){var result=new TreeSet<>(ids());result.addAll(MapActorTemplates.BUILTINS);return result;}
    public static boolean owns(net.minecraft.world.entity.Entity entity){return entity instanceof MapActorEntity||NativeMapActors.owns(entity);}
    public static boolean countsForWave(net.minecraft.world.entity.Entity entity){return owns(entity)&&MapKnifeRules.profile((net.minecraft.world.entity.LivingEntity)entity).getBoolean("countsForWave");}
    public static net.minecraft.network.chat.Component displayName(CompoundTag p){return p.getString("nameKey").isEmpty()?net.minecraft.network.chat.Component.literal(p.getString("name")):net.minecraft.network.chat.Component.translatable(p.getString("nameKey"));}
    public CompoundTag catalogue(){var out=new CompoundTag();out.putString("action","actor_catalogue");var names=new CompoundTag();var keys=new CompoundTag();for(String id:catalogueIds()){var p=profile(id);names.putString(id,p.getString("name"));keys.putString(id,p.getString("nameKey"));}out.put("names",names);out.put("nameKeys",keys);if(owner!=null)out.put("eggVariants",me.cryo.zombierool.config.SpecialEnemyConfig.get(owner));return out;}
    @SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.scripting.LuaWorkbenchPackets.Response(get(player.serverLevel()).catalogue()));}
    public static String json(CompoundTag n) {return new GsonBuilder().setPrettyPrinting().create().toJson(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE,n));}
    public static CompoundTag parse(String json) throws Exception {
        if(json.length()>1000000)throw new IllegalArgumentException("Definition too large");
        return TagParser.parseTag(json);
    }
    public static void id(String id) {if(!id.matches("[a-z][a-z0-9_]{0,47}"))throw new IllegalArgumentException("Use an identifier such as scarlet_guard");}
    private static double number(CompoundTag in,String key,double fallback,double min,double max) {
        double v=in.contains(key,99)?in.getDouble(key):fallback;
        if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid number: "+key);
        return Math.max(min,Math.min(max,v));
    }
    public static CompoundTag sanitize(String id,CompoundTag in) {
        id(id);var out=new CompoundTag();out.putString("id",id);
        String name=in.getString("name");out.putString("name",name.isBlank()?id:name.substring(0,Math.min(80,name.length())));
        out.putBoolean("boss",in.getBoolean("boss"));out.putInt("meleeCooldown",(int)number(in,"meleeCooldown",20,20,200));out.putBoolean("countsForWave",in.getBoolean("countsForWave"));
        out.putBoolean("undead",!in.contains("undead")||in.getBoolean("undead"));
        out.putBoolean("sourceMotion",in.getBoolean("sourceMotion"));
        out.putBoolean("harmless",in.getBoolean("harmless"));
        out.putBoolean("deployable",in.getBoolean("deployable"));
        if(in.contains("explosiveProp",10))out.put("explosiveProp",MapExplosiveProps.settings(in.getCompound("explosiveProp")));
        out.putBoolean("invulnerable",in.getBoolean("invulnerable"));
        out.putDouble("healthMultiplier",number(in,"healthMultiplier",1,.1,10));
        out.putBoolean("wavePursuit",in.getBoolean("wavePursuit"));
        out.putBoolean("despawnAfterDeath",in.getBoolean("despawnAfterDeath"));
        out.putBoolean("fullBright",in.getBoolean("fullBright"));
        out.putDouble("renderAlpha",number(in,"renderAlpha",1,.1,1));
        String base=in.getString("baseEntity");if(!base.isEmpty()&&!List.of("scourge_ghoul","scourge_zombie","scourge_skeleton","abomination","geist","zombie","crawler","hellhound","white_knight","panzer","brutus","pentagon_thief","napalm_minion","skeleton_minion","george_romero","astronaut").contains(base))throw new IllegalArgumentException("Invalid base entity");out.putString("baseEntity",base);
        out.putString("nameKey",in.getString("nameKey"));
        String knife=in.getString("knifeMode");if(!List.of("wave","hits","fixed").contains(knife))knife=!in.getBoolean("boss")&&List.of("zombie","scourge_ghoul","scourge_zombie","scourge_skeleton","crawler","hellhound","skeleton_minion").contains(base)?"wave":"fixed";out.putString("knifeMode",knife);out.putInt("knifeHits",(int)number(in,"knifeHits",1,1,1000));
        var armor=new ListTag();for(var t:in.getList("armorBoxes",10)){if(armor.size()>=32)throw new IllegalArgumentException("Too many armor cubes");var b=(CompoundTag)t;String bone=b.getString("bone");if(!List.of("torso","head","left_arm","right_arm","left_forearm","right_forearm","left_hand","right_hand","left_shin","right_shin").contains(bone))throw new IllegalArgumentException("Invalid armor bone");var clean=new CompoundTag();clean.putString("bone",bone);clean.putInt("material",(int)number(b,"material",5,0,23));var shape=new ListTag();for(var n:b.getList("box",6)){double v=((NumericTag)n).getAsDouble();if(!Double.isFinite(v)||Math.abs(v)>64)throw new IllegalArgumentException("Invalid armor cube");shape.add(DoubleTag.valueOf(v));}if(shape.size()!=6)throw new IllegalArgumentException("Armor cube requires six dimensions");clean.put("box",shape);for(String angle:List.of("rx","ry","rz"))clean.putDouble(angle,number(b,angle,0,-180,180));armor.add(clean);}out.put("armorBoxes",armor);
        out.putDouble("knockbackResistance",1);
        MapActorBehavior.settings(in,out);
        out.putDouble("meleeReach",number(in,"meleeReach",.8,.1,3));
        String role=in.getString("combatRole");out.putString("combatRole",List.of("archer","mage").contains(role)?role:"melee");out.putDouble("preferredRange",number(in,"preferredRange",8,2,32));out.putBoolean("smokyEyes",in.getBoolean("smokyEyes"));
        var sounds=new CompoundTag();for(String action:List.of("aggro","attack","hurt","death","slay","cast","idle","move","wing","spawn")){var rows=new ListTag();for(Tag t:in.getCompound("sounds").getList(action,8)){String sound=t.getAsString();if(rows.size()<16&&sound.matches("[a-z0-9_./:-]{1,160}"))rows.add(StringTag.valueOf(sound));}if(!rows.isEmpty())sounds.put(action,rows);}out.put("sounds",sounds);
        var dialogue=new CompoundTag();for(String sound:in.getCompound("dialogue").getAllKeys()){
            if(dialogue.size()>=64)throw new IllegalArgumentException("Maximum 64 voice subtitles");
            var idSound=net.minecraft.resources.ResourceLocation.tryParse(sound);String text=in.getCompound("dialogue").getString(sound).trim();
            if(idSound==null||text.length()>512)throw new IllegalArgumentException("Invalid voice subtitle");
            if(!text.isBlank())dialogue.putString(idSound.toString(),text);
        }out.put("dialogue",dialogue);
        String behavior=in.getString("behavior");if(!List.of("melee","guard","wander","script").contains(behavior))throw new IllegalArgumentException("behavior: melee, guard, wander or script");
        out.putString("behavior",behavior);
        for(var key:List.of("health","damage","speed","aggroRadius","wanderRadius","leashRadius","scale","width","height")) {
            double fallback=switch(key){case "health"->40;case "damage"->3;case "speed"->.23;case "aggroRadius"->12;case "wanderRadius"->6;case "leashRadius"->24;case "width"->.6;case "height"->1.8;default->1;};
            double max=switch(key){case "health"->1000000;case "damage"->1000;case "speed"->1;case "aggroRadius","wanderRadius","leashRadius"->128;case "scale"->8;default->8;};
            out.putDouble(key,number(in,key,fallback,key.equals("damage")||key.endsWith("Radius")?0:.05,max));
        }
        String callback=in.getString("callback");if(!callback.isEmpty())id(callback);out.putString("callback",callback);
        var bones=MapActorAnimations.bones(in.getCompound("bones"));out.put("bones",bones);
        out.put("animations",MapActorAnimations.sanitize(in.getCompound("animations"),bones));
        String texture=in.getString("texture");if(!texture.isEmpty()&&!texture.matches("[a-z0-9_./:-]{1,128}"))throw new IllegalArgumentException("Invalid map texture name");out.putString("texture",texture);
        out.putInt("textureWidth",(int)number(in,"textureWidth",1,1,1024));out.putInt("textureHeight",(int)number(in,"textureHeight",1,1,1024));
        for(String color:List.of("eyeR","eyeG","eyeB"))out.putDouble(color,number(in,color,color.equals("eyeR")?.3:color.equals("eyeG")?.75:1,0,1));
        var boxes=new ListTag();for(var t:in.getList("boxes",10)) {
            if(boxes.size()>=128)throw new IllegalArgumentException("Maximum 128 model boxes");
            var b=(CompoundTag)t;var clean=new CompoundTag();String bone=b.getString("bone");
            if(!bone.isEmpty()&&!bones.contains(bone))throw new IllegalArgumentException("Unknown model bone: "+bone);
            clean.putString("bone",bone.isEmpty()?"body":bone);
            clean.putInt("u",(int)number(b,"u",0,0,1024));clean.putInt("v",(int)number(b,"v",0,0,1024));
            clean.putBoolean("emissive",b.getBoolean("emissive"));
            for(String k:List.of("x","y","z","sx","sy","sz","r","g","b"))clean.putDouble(k,number(b,k,k.startsWith("s")?4:k.length()==1&&"rgb".contains(k)?.7:0,k.startsWith("s")?.1:k.length()==1&&"rgb".contains(k)?0:-64,k.length()==1&&"rgb".contains(k)?1:64));
            boxes.add(clean);
        }
        out.put("boxes",boxes);
        var appearances=new ListTag();
        for(var tag:in.getList("appearances",10)){
            if(appearances.size()>=4)throw new IllegalArgumentException("Maximum four alternative appearances");
            var alternative=(CompoundTag)tag;
            if(alternative.contains("appearances"))throw new IllegalArgumentException("Nested appearances are not supported");
            var input=out.copy();input.remove("appearances");
            for(String field:APPEARANCE_FIELDS)if(alternative.contains(field))input.put(field,alternative.get(field).copy());
            var validated=sanitize(id,input);var visual=new CompoundTag();
            for(String field:APPEARANCE_FIELDS)if(validated.contains(field))visual.put(field,validated.get(field).copy());
            appearances.add(visual);
        }
        if(!appearances.isEmpty())out.put("appearances",appearances);
        return out;
    }
    public void put(ServerLevel level,String id,CompoundTag input) throws Exception {
        if(!profiles.contains(id)&&profiles.size()>=256)throw new IllegalArgumentException("Maximum 256 profiles");
        var clean=sanitize(id,input);
        if(json(clean).length()>1000000)throw new IllegalArgumentException("Model and animations exceed 1000000 characters");
        Path directory=me.cryo.zombierool.util.WorldZombieroolPaths.path(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),"entities");
        Files.createDirectories(directory);Path file=directory.resolve(id+".json");
        if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(file))throw new IllegalArgumentException("Entity files cannot be symbolic links");
        var temp=Files.createTempFile(directory,"entity-",".tmp");
        try {Files.writeString(temp,json(clean));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);} finally {Files.deleteIfExists(temp);}
        profiles.put(id,clean);setDirty();
    }
    public int importFiles(ServerLevel level) throws Exception {
        Path dir=me.cryo.zombierool.util.WorldZombieroolPaths.path(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),"entities");
        if(!Files.isDirectory(dir))return 0;
        if(Files.isSymbolicLink(dir))throw new IllegalArgumentException("Linked entity directory");
        var imported=new CompoundTag();
        try(var files=Files.list(dir)) {for(Path file:files.filter(p->p.toString().endsWith(".json")).sorted().limit(257).toList()) {
            if(imported.size()>=256||Files.isSymbolicLink(file)||Files.size(file)>1000000)throw new IllegalArgumentException("Too many or invalid entity files");
            String id=file.getFileName().toString().replaceFirst("\\.json$","");imported.put(id,sanitize(id,parse(Files.readString(file))));
        }}
        // A file defines the complete profile. Recursive NBT merge keeps removed
        // bones and animation channels from older versions of the same model.
        // Replace matching profiles atomically after all files have validated;
        // profiles without a corresponding file and authored markers stay intact.
        for(String id:imported.getAllKeys())profiles.put(id,imported.getCompound(id));
        // Older Risen maps had separate gender entries. Preserve their spawners as aliases.
        for(String old:new ArrayList<>(profiles.getAllKeys()))if(!canonicalProfileId(old).equals(old))profiles.remove(old);
        for(String key:markers.getAllKeys()){
            var rule=markers.getCompound(key);String old=rule.getString("profile"),canonical=canonicalProfileId(old);
            if(!old.equals(canonical))rule.putString("profile",canonical);
        }
        setDirty();return imported.size();
    }
    public CompoundTag marker(BlockPos pos) {return markers.getCompound(Long.toString(pos.asLong())).copy();}
    public static void end(ServerLevel level){var data=get(level);data.running=false;data.matchIdentity="";data.triggered.clear();data.pendingBatches.clear();data.questActors.clear();data.questMembers.clear();data.clearStreaming();for(var dimension:level.getServer().getAllLevels()){var cleanup=new java.util.ArrayList<net.minecraft.world.entity.Entity>();for(var entity:dimension.getAllEntities())if(owns(entity)&&!MapExplosiveProps.authored(entity))cleanup.add(entity);for(var entity:cleanup)if(!entity.isRemoved())entity.discard();}}
    public static void begin(ServerLevel level){end(level);var data=get(level);data.running=true;data.session++;data.matchIdentity=UUID.randomUUID().toString();for(var dimension:level.getServer().getAllLevels())MapExplosiveProps.begin(dimension);}
    /** A saved batch must fit within its simultaneous population limit. Never lower a larger limit. */
    public static void normalizeSpawnBatch(CompoundTag settings) {
        int batch=(int)number(settings,"batch",1,1,16);
        int limit=(int)number(settings,"maxAlive",1,1,64);
        settings.putInt("batch",batch);
        settings.putInt("maxAlive",Math.max(batch,limit));
    }
    public void marker(BlockPos pos,CompoundTag in) {
        String key=Long.toString(pos.asLong());if(!markers.contains(key)&&markers.size()>=1024)throw new IllegalArgumentException("Maximum 1024 markers");
        String profile=canonicalProfileId(in.getString("profile"));id(profile);
        var out=new CompoundTag();out.putString("profile",profile);
        for(String k:List.of("health","damage","speed","aggroRadius","wanderRadius","leashRadius","knockbackResistance"))if(in.contains(k,99))out.putDouble(k,number(in,k,1,k.equals("health")?1:0,k.endsWith("Radius")?128:k.equals("speed")||k.equals("knockbackResistance")?1:1000000));
        if(in.contains("countsForWave"))out.putBoolean("countsForWave",in.getBoolean("countsForWave"));
        if(in.contains("knifeMode")){String mode=in.getString("knifeMode");if(!List.of("wave","hits","fixed").contains(mode))throw new IllegalArgumentException("Invalid knife mode");out.putString("knifeMode",mode);out.putInt("knifeHits",(int)number(in,"knifeHits",1,1,1000));}
        if(in.contains("behavior")){String behavior=in.getString("behavior");if(!List.of("melee","guard","wander","script").contains(behavior))throw new IllegalArgumentException("Invalid behavior");out.putString("behavior",behavior);}
        out.putDouble("knockbackResistance",1);MapActorBehavior.settings(in,out);
        out.putDouble("spawnRadius",number(in,"spawnRadius",32,0,32));
        String trigger=in.getString("trigger");if(!List.of("game_start","wave_start","interval").contains(trigger))throw new IllegalArgumentException("trigger: game_start, wave_start or interval");
        out.putString("trigger",trigger);
        for(String k:List.of("firstWave","waveInterval","cooldownTicks","maxAlive","batch"))out.putInt(k,(int)number(in,k,k.equals("cooldownTicks")?600:1,1,k.equals("maxAlive")?64:k.equals("batch")?16:72000));
        normalizeSpawnBatch(out);
        out.putBoolean("requireRedstone",in.getBoolean("requireRedstone"));
        out.putBoolean("enabled",!in.contains("enabled")||in.getBoolean("enabled"));
        markers.put(key,out);triggered.remove(key);pendingBatches.remove(key);setDirty();if(owner!=null&&owner.getBlockEntity(pos) instanceof me.cryo.zombierool.block.MapActorMarkerBlock.MarkerData be)be.configure(out);
        if(owner!=null)for(var entity:owner.getAllEntities())if(entity instanceof net.minecraft.world.entity.Mob mob&&owns(mob)&&mob.isAlive()&&(mob instanceof MapActorEntity actor?actor.marker():mob.getPersistentData().getString("MapMarker")).equals(key)){
            if(mob instanceof MapActorEntity actor)actor.applyBehaviorSettings(out);
            else{var p=NativeMapActors.profile(mob);for(String field:MapActorBehavior.BEHAVIOR_FIELDS)if(out.contains(field))p.put(field,out.get(field).copy());var movement=mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(movement!=null)movement.setBaseValue(MapActorBehavior.speed(p,owner));if(mob instanceof me.cryo.zombierool.entity.ZombieEntity zombie)zombie.mapOutfit(p);mob.getNavigation().stop();}
        }
    }
    public void removeMarker(BlockPos pos) {String key=Long.toString(pos.asLong());markers.remove(key);triggered.remove(key);pendingBatches.remove(key);setDirty();}
    public String spawn(ServerLevel level,String id,Vec3 at,String marker) {
        var p=profile(id);if(p.isEmpty()||!Double.isFinite(at.x)||!Double.isFinite(at.y)||!Double.isFinite(at.z)||!level.hasChunkAt(BlockPos.containing(at))||level.isOutsideBuildHeight(BlockPos.containing(at)))return "";
        p=appearance(p,level.random.nextInt(p.getList("appearances",10).size()+1));
        if(!marker.isEmpty()){var rule=markers.getCompound(marker);for(String key:List.of("health","damage","speed","aggroRadius","wanderRadius","leashRadius","countsForWave","behavior","knockbackResistance","knifeMode","knifeHits","speedMode","stayNearSpawn","healthMode"))if(rule.contains(key))p.put(key,rule.get(key).copy());}
        if(!marker.isEmpty()&&protectedMarker(marker))questBehavior(p);
        p.putDouble("knockbackResistance",1);double baseHealth=p.getString("healthMode").equals("wave")?MapActorBehavior.waveHealth(level):p.getDouble("health");double healthMultiplier=p.contains("healthMultiplier",99)?p.getDouble("healthMultiplier"):1;p.putDouble("health",Math.min(1000000,baseHealth*healthMultiplier));
        if(!p.getString("baseEntity").isEmpty())return remember(NativeMapActors.spawn(level,p,at,marker),marker);
        if(ActorPopulation.alive(level)>=ActorPopulation.MAX_ALIVE)return "";
        var actor=ZombieroolModEntities.MAP_ACTOR.get().create(level);if(actor==null)return "";
        actor.configure(p,at,marker);actor.moveTo(at.x,at.y,at.z,level.random.nextFloat()*360,0);
        actor.getPersistentData().putBoolean("matchActor",WaveManager.isGameRunning());
        actor.getPersistentData().putString("MapMatchId",matchIdentity);
        if(!spawnSpace(level,actor,marker)||spawnOccupied(actor,marker)||!level.addFreshEntity(actor))return "";
        if(p.getBoolean("countsForWave")&&WaveManager.isCombatWave())WaveManager.registerMapActor(actor);
        ActorPopulation.spawned(actor);MapActorAudio.play(actor,"spawn");actor.callback("spawn");return remember(actor.getUUID().toString(),marker);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)return;
        var activeBolts=BOLTS.getOrDefault(level,Collections.emptySet());
        for(var iterator=activeBolts.iterator();iterator.hasNext();){var bolt=iterator.next();if(bolt.isRemoved()){iterator.remove();continue;}
            if(bolt.tickCount>100||bolt.getOwner()==null||!bolt.getOwner().isAlive()){bolt.discard();continue;}
            var tag=bolt.getPersistentData();String style=tag.getString("style");
            if(tag.hasUUID("factionTarget")&&bolt.getOwner() instanceof net.minecraft.world.entity.Mob owner&&level.getEntity(tag.getUUID("factionTarget")) instanceof net.minecraft.world.entity.LivingEntity target&&!(target instanceof net.minecraft.world.entity.player.Player)&&MapActorBehavior.enemies(owner,target)&&MapActorBehavior.pursuable(owner,target)){
                var aim=target.position().add(0,target.getBbHeight()*.55,0).subtract(bolt.position());double speed=Math.max(.2,Math.min(3,tag.getDouble("factionSpeed")));
                if(aim.lengthSqr()>.001){var velocity=bolt.getDeltaMovement().lerp(aim.normalize().scale(speed),.18);bolt.setDeltaMovement(velocity.normalize().scale(speed));bolt.hasImpulse=true;}
            }
            if(style.equals("arrow")||level.getGameTime()%5!=0)continue;level.sendParticles(style.equals("fire")?net.minecraft.core.particles.ParticleTypes.FLAME:style.equals("web")?net.minecraft.core.particles.ParticleTypes.CLOUD:net.minecraft.core.particles.ParticleTypes.WITCH,bolt.getX(),bolt.getY(),bolt.getZ(),3,.05,.05,.05,0);
        }
        var data=get(level);boolean playing=WaveManager.isGameRunning();
        if(!playing) {if(data.running){for(var e:level.getAllEntities())if(owns(e)&&!MapExplosiveProps.authored(e))e.discard();data.running=false;data.triggered.clear();data.pendingBatches.clear();data.questActors.clear();data.questMembers.clear();data.clearStreaming();}return;}
        if(!data.running){data.running=true;data.session++;data.matchIdentity=UUID.randomUUID().toString();data.triggered.clear();data.pendingBatches.clear();data.questActors.clear();data.questMembers.clear();data.clearStreaming();}
        if(WaveManager.isPausedByPlayer()||level.players().stream().noneMatch(MapActorBehavior::eligible))return;
        int wave=WaveManager.getCurrentWave();
        var players=level.players().stream().filter(MapActorBehavior::eligible).toList();
        long streamStarted=System.nanoTime();
        int attemptsLeft=128,spawnsLeft=8-data.stream(level,players,streamStarted+1_000_000);
        var priority=new ArrayList<String>();var ordinary=new ArrayList<String>();
        for(String key:new TreeSet<>(data.markers.getAllKeys()))(data.protectedMarker(key)?priority:ordinary).add(key);
        long spawnStarted=System.nanoTime();
        for(var keys:java.util.List.of(priority,ordinary)){
            boolean protectedPass=keys==priority;int visited=0;
            while(visited<keys.size()&&attemptsLeft>0&&spawnsLeft>0){
                if(System.nanoTime()-spawnStarted>=2_000_000)break;
                int cursor=protectedPass?data.questMarkerCursor++:data.markerCursor++;
                String key=keys.get(Math.floorMod(cursor,keys.size()));visited++;
            BlockPos pos=BlockPos.of(Long.parseLong(key));if(!level.hasChunkAt(pos))continue;
            if(data.activationRadius>0&&!near(players,Vec3.atCenterOf(pos),data.activationRadius))continue;
            var quest=data.questArea(pos);
            if(!level.getBlockState(pos).is(me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BLOCK.get())){data.markers.remove(key);data.setDirty();continue;}
            var n=data.markers.getCompound(key);if(!n.getBoolean("enabled")||n.getBoolean("requireRedstone")&&!level.hasNeighborSignal(pos))continue;
            String trigger=n.getString("trigger");if(!trigger.equals("game_start")&&(wave<n.getInt("firstWave")||(wave-n.getInt("firstWave"))%n.getInt("waveInterval")!=0))continue;
            String token=data.session+":"+(trigger.equals("game_start")?"start":trigger.equals("wave_start")?wave:level.getGameTime()/n.getInt("cooldownTicks"));
            if(token.equals(data.triggered.get(key)))continue;
            long alive=ActorPopulation.alive(level,key);
            var pending=data.pendingBatches.get(key);
            if(pending==null||!pending.token.equals(token))pending=new SpawnBatch(token,(int)Math.min(n.getInt("batch"),Math.max(0,n.getInt("maxAlive")-alive)),0);
            int count=(int)Math.min(pending.remaining,Math.max(0,n.getInt("maxAlive")-alive));
            if(data.profile(n.getString("profile")).isEmpty())continue;
            if(pending.remaining<=0){data.triggered.put(key,token);data.pendingBatches.remove(key);continue;}
            if(count<=0){data.pendingBatches.put(key,pending);continue;}
            if(ActorPopulation.marked(level)>=(data.activationRadius>0&&quest==null?ActorPopulation.MARKER_LIMIT-64:ActorPopulation.MARKER_LIMIT)||ActorPopulation.alive(level)>=ActorPopulation.MAX_ALIVE){data.pendingBatches.put(key,pending);break;}
            int spawned=0;
            // Fill the interior nearest the marker first, rather than only its centre and eight distant points.
            var offsets=spawnOffsets(quest!=null?Math.min(8,n.getDouble("spawnRadius")):n.getDouble("spawnRadius"));
            int checked=0;
            while(checked<Math.min(32,offsets.size())&&spawned<count&&attemptsLeft>0&&spawnsLeft>0){
                if(checked>0&&System.nanoTime()-spawnStarted>=2_000_000)break;
                var offset=offsets.get((pending.cursor+checked++)%offsets.size());
                for(int dy:quest==null?new int[]{0}:new int[]{0,-1,-2}){
                    if(attemptsLeft<=0||System.nanoTime()-spawnStarted>=2_000_000)break;
                    attemptsLeft--;
                    Vec3 at=Vec3.atBottomCenterOf(pos).add(offset.getX(),dy,offset.getZ());
                    if(quest!=null&&(!quest.contains(BlockPos.containing(at))||!quest.core(BlockPos.containing(at))&&!MeleeTrace.clear(level,Vec3.atCenterOf(pos),at.add(0,1.35,0))))continue;
                    var floor=BlockPos.containing(at).below();if(!level.hasChunkAt(floor)||level.getBlockState(floor).getCollisionShape(level,floor).isEmpty())continue;
                    if(ActorPopulation.marked(level)>=(data.activationRadius>0&&quest==null?ActorPopulation.MARKER_LIMIT-64:ActorPopulation.MARKER_LIMIT)||ActorPopulation.alive(level)>=ActorPopulation.MAX_ALIVE)break;
                    if(!data.spawn(level,n.getString("profile"),at,key).isEmpty()){spawned++;spawnsLeft--;break;}
                }
            }
            int remaining=pending.remaining-spawned;
            if(remaining<=0){data.triggered.put(key,token);data.pendingBatches.remove(key);}
            else data.pendingBatches.put(key,new SpawnBatch(token,remaining,(pending.cursor+checked)%offsets.size()));
            }
        }
    }
    @SubscribeEvent public static void impact(net.minecraftforge.event.entity.ProjectileImpactEvent event){
        if(!(event.getEntity() instanceof net.minecraft.world.entity.projectile.Arrow bolt)||!bolt.getPersistentData().getBoolean("zr_actor_bolt")||bolt.level().isClientSide)return;
        if(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult collision&&bolt.getOwner() instanceof net.minecraft.world.entity.Mob shooter&&collision.getEntity() instanceof net.minecraft.world.entity.LivingEntity body&&!MapActorBehavior.pursuable(shooter,body)){
            // Corpses and allies must not consume an enemy's ranged attack.
            event.setCanceled(true);return;
        }
        if(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim&&bolt.getOwner() instanceof net.minecraft.world.entity.Mob owner&&owns(owner)&&MapActorBehavior.pursuable(owner,victim)){
            if(bolt.getPersistentData().getFloat("damage")>0){var source=owner.damageSources().arrow(bolt,owner);float damage=bolt.getPersistentData().getFloat("damage");if(victim instanceof net.minecraft.world.entity.player.Player player)FixedPlayerDamage.projectileHit(player,source,damage);else victim.hurt(source,damage);}
            if(bolt.getPersistentData().getString("style").equals("web"))victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,40,1));
        }
        bolt.discard();event.setCanceled(true);
    }
}

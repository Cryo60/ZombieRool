package me.cryo.zombierool.scripting;

import me.cryo.zombierool.gameplay.PointManager;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CPlayGlobalSoundPacket;
import me.cryo.zombierool.network.packet.S2CSetFogPresetPacket;
import me.cryo.zombierool.network.packet.S2CTriggerScopeScreamerPacket;
import me.cryo.zombierool.block.system.ObstacleDoorSystem;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlockEntity;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.player.PlayerDownManager;
import me.cryo.zombierool.player.PlayerCrawlManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.chat.Component;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PacketDistributor;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ZombieroolAPI {
    private final ServerLevel level;
    private static final Map<UUID, ServerBossEvent> playerObjectives = new ConcurrentHashMap<>();

    public ZombieroolAPI(ServerLevel level) {
        this.level = level;
    }
    public boolean isGameRunning(){return WaveManager.isGameRunning();}
    public boolean isGamePaused(){return WaveManager.isPausedByPlayer();}
    public LuaTable getMapActorProfiles(){var out=new LuaTable();int i=1;for(String id:me.cryo.zombierool.gameplay.MapActors.get(level).ids())out.set(i++,id);return out;}
    public boolean defineMapActor(String id,LuaTable definition){try{var input=me.cryo.zombierool.gameplay.MapActorTemplates.profile(false);input.merge(MapActorLuaData.compound(definition));me.cryo.zombierool.gameplay.MapActors.get(level).put(level,id,input);return true;}catch(Exception e){throw new org.luaj.vm2.LuaError(e.getMessage());}}
    public String spawnMapActor(String id,double x,double y,double z){if(!LuaScriptManager.gameplayCallAllowed(level))return "";return me.cryo.zombierool.gameplay.MapActors.get(level).spawn(level,id,new Vec3(x,y,z),"");}
    public LuaTable getMusicVolumes(){return deviceTable(me.cryo.zombierool.block.system.MusicVolume.Data.get(level).list());}
    public LuaTable getMusicVolume(String id){return deviceTable(me.cryo.zombierool.block.system.MusicVolume.Data.get(level).list().getCompound(id));}
    public boolean toggleMusicVolume(String uuid,String id){var p=scriptPlayer(uuid);return p!=null&&me.cryo.zombierool.block.system.MusicVolume.toggle(p,id);}
    public boolean isMusicVolumeActive(String uuid,String id){var p=scriptPlayer(uuid);return p!=null&&me.cryo.zombierool.block.system.MusicVolume.Data.get(level).active(p.getUUID(),id);}
    public boolean setMusicVolumeEnabled(String id,boolean enabled){return me.cryo.zombierool.block.system.MusicVolume.enabled(level,id,enabled);}
    public boolean resetPlayerMusic(String uuid){var p=scriptPlayer(uuid);if(p==null)return false;me.cryo.zombierool.block.system.MusicVolume.reset(p);return true;}
    public String spawnMapActorNearPlayer(String profile,String uuid,double minRadius,double maxRadius){var p=scriptPlayer(uuid);return p==null?"":me.cryo.zombierool.gameplay.HiddenActorSpawn.spawn(p,profile,"",minRadius,maxRadius);}
    public String spawnEnemySpawnerNearPlayer(int x,int y,int z,String uuid,double minRadius,double maxRadius){var p=scriptPlayer(uuid);var pos=new BlockPos(x,y,z);if(p==null||!(level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.MapActorMarkerBlock.MarkerData))return "";var rule=me.cryo.zombierool.gameplay.MapActors.get(level).marker(pos);if(rule.isEmpty())return "";return me.cryo.zombierool.gameplay.HiddenActorSpawn.spawn(p,rule.getString("profile"),Long.toString(pos.asLong()),minRadius,maxRadius);}
    private net.minecraft.world.entity.Mob mapActor(String uuid){try{var e=level.getEntity(UUID.fromString(uuid));return e instanceof net.minecraft.world.entity.Mob actor&&me.cryo.zombierool.gameplay.MapActors.owns(actor)?actor:null;}catch(IllegalArgumentException e){return null;}}
    public LuaTable getMapActor(String uuid){var a=mapActor(uuid);var out=new LuaTable();if(a==null)return out;out.set("uuid",uuid);out.set("profile",(a instanceof me.cryo.zombierool.entity.MapActorEntity custom?custom.profile():me.cryo.zombierool.gameplay.NativeMapActors.profile(a)).getString("id"));out.set("health",a.getHealth());out.set("maxHealth",a.getMaxHealth());out.set("x",a.getX());out.set("y",a.getY());out.set("z",a.getZ());out.set("homeX",a instanceof me.cryo.zombierool.entity.MapActorEntity custom?custom.home().x:a.getPersistentData().getDouble("MapHomeX"));out.set("homeY",a instanceof me.cryo.zombierool.entity.MapActorEntity custom?custom.home().y:a.getPersistentData().getDouble("MapHomeY"));out.set("homeZ",a instanceof me.cryo.zombierool.entity.MapActorEntity custom?custom.home().z:a.getPersistentData().getDouble("MapHomeZ"));out.set("inMeleeRange",org.luaj.vm2.LuaValue.valueOf(a.getTarget()!=null&&(a instanceof me.cryo.zombierool.entity.MapActorEntity custom?custom.inMeleeRange(a.getTarget()):a.getBoundingBox().inflate(.9).intersects(a.getTarget().getBoundingBox()))));out.set("canSeeTarget",org.luaj.vm2.LuaValue.valueOf(a.getTarget()!=null&&me.cryo.zombierool.gameplay.MapActorBehavior.pursuable(a,a.getTarget())&&me.cryo.zombierool.gameplay.MapActorBehavior.visible(a,a.getTarget())));out.set("target",a.getTarget()==null?"":a.getTarget().getUUID().toString());return out;}
    public boolean moveMapActor(String uuid,double x,double y,double z,double speed){var a=mapActor(uuid);if(a==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(speed)||!level.hasChunkAt(BlockPos.containing(x,y,z)))return false;return a.getNavigation().moveTo(x,y,z,Math.max(.1,Math.min(3,speed)));}
    public boolean despawnMapActor(String uuid){var a=mapActor(uuid);if(a==null)return false;WaveManager.onMobDeath(a,level);a.discard();return true;}
    public boolean setMapActorTarget(String uuid,String playerUUID){var a=mapActor(uuid);if(a==null)return false;var p=scriptPlayer(playerUUID);if(p!=null&&!me.cryo.zombierool.gameplay.MapActorBehavior.pursuable(a,p))return false;a.setTarget(p);return p!=null||playerUUID.isEmpty();}
    public int mapActorAttack(String uuid,double radius,double damage,String effect,int effectTicks){
        var a=mapActor(uuid);if(a==null||!a.isAlive()||!Double.isFinite(radius)||!Double.isFinite(damage))return 0;radius=Math.max(0,Math.min(32,radius));damage=Math.max(0,Math.min(1000,damage));int hit=0;
        var effectId=ResourceLocation.tryParse(effect);var potion=effectId==null?null:ForgeRegistries.MOB_EFFECTS.getValue(effectId);
        for(var p:level.players())if(me.cryo.zombierool.gameplay.MapActorBehavior.pursuable(a,p)&&a.distanceToSqr(p)<=radius*radius&&a.hasLineOfSight(p)){
            if(damage>0)p.hurt(a.damageSources().mobAttack(a),(float)damage);if(potion!=null)p.addEffect(new MobEffectInstance(potion,Math.max(1,Math.min(1200,effectTicks)),0));hit++;
        }return hit;
    }
    public LuaTable getMapActorData(String uuid){var a=mapActor(uuid);return a==null?new LuaTable():deviceTable(a.getPersistentData().getCompound("script"));}
    public boolean setMapActorData(String uuid,LuaTable values){var a=mapActor(uuid);if(a==null)return false;var n=a.getPersistentData().getCompound("script").copy();n.merge(MapActorLuaData.compound(values));if(n.toString().length()>16000)return false;a.getPersistentData().put("script",n);return true;}
    public long getMapActorTime(){return level.getGameTime();}
    public boolean playMapActorAnimation(String uuid,String clip){var actor=mapActor(uuid);return actor instanceof me.cryo.zombierool.entity.MapActorEntity custom&&custom.playAnimation(clip);}
    public int mapActorEffect(String uuid,double radius,String effect,int duration,int amplifier){
        var a=mapActor(uuid);var id=ResourceLocation.tryParse(effect);var type=id==null?null:ForgeRegistries.MOB_EFFECTS.getValue(id);if(a==null||type==null||!Double.isFinite(radius))return 0;
        radius=Math.max(0,Math.min(32,radius));int count=0;for(var p:level.players())if(me.cryo.zombierool.gameplay.MapActorBehavior.pursuable(a,p)&&a.distanceToSqr(p)<=radius*radius&&a.hasLineOfSight(p)){p.addEffect(new MobEffectInstance(type,Math.max(1,Math.min(1200,duration)),Math.max(0,Math.min(4,amplifier))));count++;}return count;
    }
    public boolean setMapActorCasting(String uuid,int ticks){var a=mapActor(uuid);if(a==null)return false;if(a instanceof me.cryo.zombierool.entity.MapActorEntity custom)custom.setCastingTicks(Math.max(0,Math.min(400,ticks)));else a.getPersistentData().putLong("MapCastUntil",level.getGameTime()+Math.max(0,Math.min(400,ticks)));a.getNavigation().stop();return true;}
    public boolean healMapActor(String uuid,double health){var a=mapActor(uuid);if(a==null||!Double.isFinite(health))return false;a.heal((float)Math.max(0,Math.min(10000,health)));return true;}
    public boolean shootMapActor(String uuid,String targetUUID,String style,double damage,double speed){
        var a=mapActor(uuid);net.minecraft.world.entity.LivingEntity target=null;try{var e=level.getEntity(UUID.fromString(targetUUID));if(e instanceof net.minecraft.world.entity.LivingEntity living)target=living;}catch(IllegalArgumentException ignored){}
        if(a==null||!a.isAlive()||target==null||!target.isAlive()||!me.cryo.zombierool.gameplay.MapActorBehavior.pursuable(a,target)||a.distanceToSqr(target)>64*64||!me.cryo.zombierool.gameplay.MapActorBehavior.visible(a,target)||!Double.isFinite(damage)||!Double.isFinite(speed))return false;
        long now=level.getGameTime();if(a.getPersistentData().contains("lastBolt")&&now-a.getPersistentData().getLong("lastBolt")<10)return false;
        a.getPersistentData().putLong("lastBolt",now);
        var bolt=new net.minecraft.world.entity.projectile.Arrow(level,a);bolt.setPos(a.getX(),a.getEyeY()-.2,a.getZ());bolt.setBaseDamage(0);bolt.setNoGravity(!style.equals("arrow"));bolt.setSilent(true);bolt.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        double velocity=Math.max(.2,Math.min(3,speed));
        Vec3 direction=target.position().add(0,target.getBbHeight()*.55,0).subtract(bolt.position());
        if(!(target instanceof net.minecraft.world.entity.player.Player)){
            // Faction enemies move while fighting. Aim a short distance ahead of their
            // torso rather than at the position they left when the cast finished.
            Vec3 motion=new Vec3(target.getX()-target.xo,0,target.getZ()-target.zo);
            if(motion.lengthSqr()>.16)motion=motion.normalize().scale(.4);
            double travel=Math.min(12,direction.length()/velocity);
            direction=direction.add(motion.scale(travel));
        }
        if(style.equals("arrow")){
            double horizontal=direction.horizontalDistance(),gravityDrop=0;
            for(int iteration=0;iteration<5;iteration++){
                double length=Math.sqrt(horizontal*horizontal+(direction.y+gravityDrop)*(direction.y+gravityDrop));
                double vx=velocity*horizontal/Math.max(.001,length),travel=0,vy=0,drop=0;
                for(int tick=0;tick<200&&travel<horizontal;tick++){
                    double fraction=Math.min(1,(horizontal-travel)/Math.max(.0001,vx));
                    drop+=vy*fraction;travel+=vx;vx*=.99;vy=vy*.99+.05;
                }
                gravityDrop=drop;
            }
            direction=direction.add(0,gravityDrop,0);
        }
        bolt.shoot(direction.x,direction.y,direction.z,(float)velocity,0);
        var n=bolt.getPersistentData();if((style.equals("fire")||style.equals("shadow"))&&!(target instanceof net.minecraft.world.entity.player.Player)){n.putUUID("factionTarget",target.getUUID());n.putDouble("factionSpeed",velocity);}n.putBoolean("zr_actor_bolt",true);n.putBoolean(me.cryo.zombierool.ZombieroolKeys.INVISIBLE,!style.equals("arrow"));n.putFloat("damage",(float)Math.max(0,Math.min(100,damage)));n.putString("style",java.util.Set.of("shadow","fire","web","arrow").contains(style)?style:"shadow");boolean created=level.addFreshEntity(bolt);if(created&&a instanceof me.cryo.zombierool.entity.MapActorEntity custom)me.cryo.zombierool.gameplay.MapActorAudio.play(custom,"attack");return created;
    }
    public String buyMapArmor(String uuid,String set){var p=scriptPlayer(uuid);return p==null?"unavailable":me.cryo.zombierool.gameplay.MapArmor.purchase(p,set);}
    public boolean equipMapArmor(String uuid,String set){var p=scriptPlayer(uuid);return p!=null&&me.cryo.zombierool.gameplay.MapArmor.equip(p,set);}
    public boolean hasWardenKey(){return me.cryo.zombierool.gameplay.WardenLocks.hasKey(level);}
    public void disableTurbines(double x,double y,double z,double radius){me.cryo.zombierool.gameplay.PortableEquipment.electricalPulse(level,new Vec3(x,y,z),radius);}
    public boolean givePortableEquipment(String uuid,String itemId){var player=getPlayer(uuid);var id=ResourceLocation.tryParse(itemId.contains(":")?itemId:"zombierool:"+itemId);if(player==null||id==null)return false;var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);return item!=null&&me.cryo.zombierool.gameplay.PortableEquipment.acquire(player,new ItemStack(item));}
    public LuaTable getPortableEquipment(String uuid){var player=getPlayer(uuid);return player==null?new LuaTable():deviceTable(me.cryo.zombierool.gameplay.PortableEquipment.data(player).copy());}

    public boolean playVoice(String uuid,String sound){var p=level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(uuid));return p!=null&&me.cryo.zombierool.util.PlayerVoiceManager.playVoice(p,sound,"lua");}
    public boolean playVoiceEvent(String uuid,String event){var p=level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(uuid));if(p==null)return false;me.cryo.zombierool.util.PlayerVoiceManager.playSoundType(p,level,event);return true;}
    public boolean setPlayerVoicePreset(String uuid,String preset){var p=level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(uuid));if(p==null||preset.length()>128)return false;p.getPersistentData().putString("zr_voice_preset",preset);return true;}
    /** Built-in presets and variants; variant numbering in Lua starts at one. */
    public LuaTable getEnemySkinPresets(){
        var result=new LuaTable();var catalog=me.cryo.zombierool.config.EnemySkinPresets.catalog();
        for(String group:List.of("zombies","hounds")){var presets=new LuaTable();for(var entry:catalog.getAsJsonObject(group).entrySet()){
            var variants=new LuaTable();int i=1;for(var row:entry.getValue().getAsJsonObject().getAsJsonArray("variants")){
                var v=new LuaTable();v.set("id","@preset/"+group+"/"+entry.getKey()+"/"+(i-1));v.set("texture",row.getAsJsonObject().get("texture").getAsString());variants.set(i++,v);
            }presets.set(entry.getKey(),variants);
        }result.set(group,presets);}return result;
    }
    private me.cryo.zombierool.entity.AbstractZombieRoolEntity appearanceEntity(String id){
        try{var entity=level.getEntity(UUID.fromString(id));return entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity enemy?enemy:null;}catch(IllegalArgumentException e){return null;}
    }
    public String getEntitySkin(String uuid){var entity=appearanceEntity(uuid);return entity==null?"":entity.getCustomSkin();}
    public String getEntityEyeSkin(String uuid){var entity=appearanceEntity(uuid);return entity==null?"":entity.getCustomEyeSkin();}
    public LuaTable getEnemyAppearances(){var result=new LuaTable();int i=1;for(var entity:level.getAllEntities())if(entity instanceof me.cryo.zombierool.entity.ZombieEntity||entity instanceof me.cryo.zombierool.entity.HellhoundEntity){var enemy=(me.cryo.zombierool.entity.AbstractZombieRoolEntity)entity;var row=new LuaTable();row.set("uuid",entity.getUUID().toString());row.set("kind",entity instanceof me.cryo.zombierool.entity.HellhoundEntity?"hellhound":"zombie");row.set("skin",enemy.getCustomSkin());row.set("eyes",enemy.getCustomEyeSkin());row.set("x",entity.getX());row.set("y",entity.getY());row.set("z",entity.getZ());result.set(i++,row);}return result;}
    public LuaTable getCustomEnemySkins(String kind){var result=new LuaTable();var skins=me.cryo.zombierool.core.manager.DynamicResourceManager.getAllServerSkins().get(kind);int i=1;if(skins!=null)for(String id:new java.util.TreeSet<>(skins.keySet()))result.set(i++,id);return result;}
    public boolean setEntitySkin(String uuid,String skin){
        var entity=appearanceEntity(uuid);if(entity==null||skin==null)return false;
        String kind=entity instanceof me.cryo.zombierool.entity.HellhoundEntity?"hellhound":"zombie";
        if(skin.startsWith("@preset/")){if(!skin.startsWith("@preset/"+("hellhound".equals(kind)?"hounds/":"zombies/"))||me.cryo.zombierool.config.EnemySkinPresets.texture(skin)==null)return false;}
        else if(!skin.isEmpty()){var skins=me.cryo.zombierool.core.manager.DynamicResourceManager.getAllServerSkins().get(kind);if(skins==null||!skins.containsKey(skin))return false;}
        entity.setCustomSkin(skin);entity.assignEyeSkin(kind);return true;
    }
    public boolean setEntityEyeSkin(String uuid,String skin){var entity=appearanceEntity(uuid);if(entity==null||skin==null)return false;
        if(!skin.isEmpty()){String kind=entity instanceof me.cryo.zombierool.entity.HellhoundEntity?"hellhound_eyes":"zombie_eyes";var skins=me.cryo.zombierool.core.manager.DynamicResourceManager.getAllServerSkins().get(kind);if(skins==null||!skins.containsKey(skin))return false;}
        entity.setCustomEyeSkin(skin);return true;
    }
    public boolean setEnemyAppearance(String uuid,String preset,int variant){var entity=appearanceEntity(uuid);if(entity==null)return false;
        String kind=entity instanceof me.cryo.zombierool.entity.HellhoundEntity?"hellhound":"zombie";
        var available="zombie".equals(kind)?me.cryo.zombierool.config.EnemySkinPresets.zombies():me.cryo.zombierool.config.EnemySkinPresets.hounds();
        if("zombie".equals(kind)&&preset!=null&&preset.startsWith("scourge_"))preset="scourge";if(!available.contains(preset))return false;String skin=me.cryo.zombierool.config.EnemySkinPresets.appearance(kind,preset,variant,level.random);return skin!=null&&setEntitySkin(uuid,skin);
    }
    public int setEnemySkinPreset(String kind,String preset,boolean updateExisting){
        boolean zombie="zombie".equals(kind);if(!zombie&&!"hellhound".equals(kind))return -1;
        if(zombie&&preset!=null&&preset.startsWith("scourge_"))preset="scourge";var available=zombie?me.cryo.zombierool.config.EnemySkinPresets.zombies():me.cryo.zombierool.config.EnemySkinPresets.hounds();if(!available.contains(preset))return -1;
        var config=WorldConfig.get(level);var tag=new net.minecraft.nbt.CompoundTag();tag.putString(zombie?"zombieSkinPreset":"hellhoundSkinPreset",preset);config.loadEditable(tag);config.setDirty();int changed=0;
        if(updateExisting)for(var entity:level.getAllEntities())if(zombie?entity instanceof me.cryo.zombierool.entity.ZombieEntity:entity instanceof me.cryo.zombierool.entity.HellhoundEntity)if(setEnemyAppearance(entity.getUUID().toString(),preset,0))changed++;
        return changed;
    }
    public void configureLunarEnvironment(double gravity,boolean vacuum,int oxygenSeconds){
        var config=WorldConfig.get(level);var addons=config.getAddonConfig();var settings=addons.getCompound("zombierool:lunar");settings.putDouble("gravity",me.cryo.zombierool.gameplay.LunarEnvironment.gravity(gravity));settings.putBoolean("vacuum",vacuum);settings.putInt("oxygenSeconds",Math.max(1,Math.min(300,oxygenSeconds)));addons.put("zombierool:lunar",settings);
        var patch=new net.minecraft.nbt.CompoundTag();patch.put("addonConfig",addons);config.loadEditable(patch);config.setDirty();
    }
    public LuaTable getMapAtmosphere(){return deviceTable(me.cryo.zombierool.gameplay.MapAtmosphere.get(level).settings());}
    public void configureMapAtmosphere(LuaTable changes){var d=me.cryo.zombierool.gameplay.MapAtmosphere.get(level);d.configure(level,mergeDevice(d.settings(),changes));}
    public void setMapAtmosphereEnabled(boolean enabled){var d=me.cryo.zombierool.gameplay.MapAtmosphere.get(level);var n=d.settings();n.putBoolean("enabled",enabled);d.configure(level,n);}
    public void setMapAtmospherePreset(String id){try{me.cryo.zombierool.gameplay.MapAtmosphere.get(level).configure(level,me.cryo.zombierool.gameplay.MapAtmosphere.get(level).resolvePreset(id));}catch(IllegalArgumentException e){throw new org.luaj.vm2.LuaError(e.getMessage());}}
    public String saveMapAtmospherePreset(String name){var d=me.cryo.zombierool.gameplay.MapAtmosphere.get(level);try{String id=d.createPreset(name,d.settings());d.configure(level,d.resolvePreset(id));return id;}catch(IllegalArgumentException e){throw new org.luaj.vm2.LuaError(e.getMessage());}}
    public LuaTable getMapAtmospherePresets(){var rows=new LuaTable();for(String id:me.cryo.zombierool.gameplay.MapAtmosphere.get(level).availablePresets()){var n=new LuaTable();n.set("id",id);n.set("name",me.cryo.zombierool.gameplay.MapAtmosphere.get(level).displayPresetName(id));rows.insert(0,n);}return rows;}

    public LuaTable getSkyObjects(){return getMapAtmosphere().get("skyObjects").checktable();}
    public boolean configureSkyObject(String id,LuaTable changes){
        if(id.isBlank()||id.length()>64)return false;var d=me.cryo.zombierool.gameplay.MapAtmosphere.get(level);var settings=d.settings();var rows=settings.getList("skyObjects",10);var base=me.cryo.zombierool.gameplay.SkyObjects.object(id,"custom",0,30,16);int index=-1;
        for(int j=0;j<rows.size();j++)if(rows.getCompound(j).getString("id").equals(id)){base=rows.getCompound(j);index=j;break;}if(index<0&&rows.size()>=me.cryo.zombierool.gameplay.SkyObjects.MAX_OBJECTS)return false;
        var row=mergeDevice(base,changes);row.putString("id",id);row=me.cryo.zombierool.gameplay.SkyObjects.sanitize(row);if(index<0)rows.add(row);else rows.set(index,row);settings.put("skyObjects",rows);settings.putBoolean("enabled",true);d.configure(level,settings);return true;
    }
    public boolean removeSkyObject(String id){var d=me.cryo.zombierool.gameplay.MapAtmosphere.get(level);var settings=d.settings();var rows=settings.getList("skyObjects",10);boolean removed=rows.removeIf(t->((net.minecraft.nbt.CompoundTag)t).getString("id").equals(id));if(removed){settings.put("skyObjects",rows);d.configure(level,settings);}return removed;}
    public boolean importSkyObject(String id,String json){try{var row=me.cryo.zombierool.gameplay.SkyObjects.importModel(json,id);return configureSkyObject(id,deviceTable(row));}catch(IllegalArgumentException e){throw new org.luaj.vm2.LuaError(e.getMessage());}}

    public LuaTable getAmbientSoundLibrary(){var rows=new LuaTable();var ids=new java.util.TreeSet<String>(me.cryo.zombierool.gameplay.MapAtmosphere.soundIds());ids.addAll(me.cryo.zombierool.core.manager.DynamicResourceManager.customAudioIds());for(String id:ids)rows.insert(0,LuaValue.valueOf(id));return rows;}
    public LuaTable getMapSkyboxes(){var rows=new LuaTable();var ids=new java.util.TreeSet<String>(me.cryo.zombierool.gameplay.MapAtmosphereAssets.skyIds());for(String id:me.cryo.zombierool.gameplay.MapAtmosphere.presetIds())if(!id.equals("none"))ids.add(me.cryo.zombierool.gameplay.MapAtmosphere.preset(id).getString("skyTexture"));for(String id:ids)rows.insert(0,LuaValue.valueOf(id));return rows;}
    public void setSkybox(String texture,String layout,double yaw,double brightness){var d=me.cryo.zombierool.gameplay.MapAtmosphere.get(level);var n=d.settings();n.putBoolean("enabled",true);n.putBoolean("skyEnabled",!texture.isBlank());n.putString("skyTexture",texture);n.putString("skyLayout",layout);n.putDouble("skyYaw",yaw);n.putDouble("skyBrightness",brightness);d.configure(level,n);}
    public boolean configureAmbientSource(String id,LuaTable changes){var d=me.cryo.zombierool.gameplay.MapAtmosphere.get(level);var base=me.cryo.zombierool.gameplay.MapAtmosphere.source(id,"zombierool:ambient_ash_wind",false,false,0,80,0,1,128,400,1200);for(var t:d.settings().getList("sources",10)){var row=(net.minecraft.nbt.CompoundTag)t;if(row.getString("id").equals(id)){base=row;break;}}var n=mergeDevice(base,changes);n.putString("id",id);return d.putSource(level,n);}
    public boolean removeAmbientSource(String id){return me.cryo.zombierool.gameplay.MapAtmosphere.get(level).removeSource(level,id);}
    public boolean triggerAmbientSound(String id){return me.cryo.zombierool.gameplay.MapAtmosphere.get(level).trigger(level,id);}
    public LuaTable getAmbientSources(){return getMapAtmosphere().get("sources").checktable();}
    public void setAmbientVolume(double volume){var n=new LuaTable();n.set("ambientVolume",volume);configureMapAtmosphere(n);}
    public LuaTable getAmbientSource(String id){for(var row:getAmbientSources().keys()){var source=getAmbientSources().get(row);if(source.get("id").optjstring("").equals(id))return source.checktable();}return new LuaTable();}
    public boolean setAmbientSourceEnabled(String id,boolean enabled){var n=new LuaTable();n.set("enabled",LuaValue.valueOf(enabled));return configureAmbientSource(id,n);}
    public void setColorGrading(double exposure,double contrast,double saturation,double gamma){var n=new LuaTable();n.set("exposure",exposure);n.set("contrast",contrast);n.set("saturation",saturation);n.set("gamma",gamma);configureMapAtmosphere(n);}
    public void setScreenEffects(double vignette,double dirt,double grain){var n=new LuaTable();n.set("vignette",vignette);n.set("dirt",dirt);n.set("grain",grain);configureMapAtmosphere(n);}
    public boolean configurePlayerAtmosphere(String playerUUID,LuaTable changes){var p=getPlayer(playerUUID);if(p==null)return false;var n=mergeDevice(me.cryo.zombierool.gameplay.MapAtmosphere.effective(p),changes);me.cryo.zombierool.gameplay.MapAtmosphere.configurePlayer(p,n);return true;}
    public boolean setPlayerAtmospherePreset(String playerUUID,String id){var p=getPlayer(playerUUID);return p!=null&&me.cryo.zombierool.gameplay.MapAtmosphere.applyPlayerPreset(p,id);}
    public boolean resetPlayerAtmosphere(String playerUUID){var p=getPlayer(playerUUID);if(p==null)return false;me.cryo.zombierool.gameplay.MapAtmosphere.restorePlayer(p,new net.minecraft.nbt.CompoundTag());return true;}
    public LuaTable getLunarEnvironment(){return deviceTable(WorldConfig.get(level).getAddonConfig().getCompound("zombierool:lunar"));}
    public void setZombieSwimming(boolean enabled){me.cryo.zombierool.gameplay.EnemyWaterMovement.configure(level,enabled);}
    public void configureIntroPhase(boolean enabled,int rampSeconds,int houndsAfterSeconds){me.cryo.zombierool.gameplay.IntroPhase.configure(level,enabled,rampSeconds,houndsAfterSeconds);}
    public void configureIntroPhaseSettings(LuaTable settings){var config=WorldConfig.get(level);var addons=config.getAddonConfig();addons.put(me.cryo.zombierool.gameplay.IntroPhase.ID,mergeDevice(addons.getCompound(me.cryo.zombierool.gameplay.IntroPhase.ID),settings));var patch=new net.minecraft.nbt.CompoundTag();patch.put("addonConfig",addons);config.loadEditable(patch);config.setDirty();}
    public LuaTable getIntroPhaseState(){var n=me.cryo.zombierool.gameplay.IntroPhase.settings(level).copy();n.putBoolean("active",isIntroPhase());n.putDouble("elapsedSeconds",getIntroElapsedSeconds());return deviceTable(n);}
    public boolean startIntroPhase(){return me.cryo.zombierool.gameplay.WaveManager.startIntroPhase(level);}
    public boolean finishIntroPhase(){return me.cryo.zombierool.gameplay.WaveManager.finishIntroPhase(level);}
    public boolean isIntroPhase(){return me.cryo.zombierool.gameplay.WaveManager.isIntroPhase();}
    public double getIntroElapsedSeconds(){return me.cryo.zombierool.gameplay.WaveManager.getIntroElapsedTicks()/20.0;}
    public boolean getZombieSwimming(){return me.cryo.zombierool.gameplay.EnemyWaterMovement.enabled(level);}
    public void setAtmosphereZone(String id,double x1,double y1,double z1,double x2,double y2,double z2,double gravity,boolean vacuum){me.cryo.zombierool.gameplay.LunarEnvironment.get(level).put(id,new AABB(x1,y1,z1,x2,y2,z2),gravity,vacuum);}
    private me.cryo.zombierool.block.system.AtmosphereZoneBlock.Zone atmosphereBlock(String id){
        var pos=me.cryo.zombierool.gameplay.LunarEnvironment.get(level).blockPosition(id);if(pos==null)return null;level.getChunkAt(pos);return level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.AtmosphereZoneBlock.Zone zone?zone:null;
    }
    public LuaTable getAtmosphereZone(String id){var zone=atmosphereBlock(id);if(zone!=null)return deviceTable(zone.config());for(var value:me.cryo.zombierool.gameplay.LunarEnvironment.get(level).describe().getList("zones",10)){var n=(net.minecraft.nbt.CompoundTag)value;if(n.getString("id").equals(id))return deviceTable(n);}return new LuaTable();}
    public boolean configureAtmosphereZone(String id,LuaTable patch){
        var zone=atmosphereBlock(id);
        if(zone!=null){var n=mergeDevice(zone.config(),patch);String name=n.getString("id").trim();if(name.length()>128||name.startsWith("@block/")||!me.cryo.zombierool.gameplay.LunarEnvironment.get(level).canBind(name,zone.getBlockPos()))return false;me.cryo.zombierool.gameplay.MatchWorldJournal.note(level,zone.getBlockPos());zone.configure(n);return true;}
        for(var value:me.cryo.zombierool.gameplay.LunarEnvironment.get(level).describe().getList("zones",10)){var n=(net.minecraft.nbt.CompoundTag)value;if(n.getString("id").equals(id)){n=mergeDevice(n,patch);setAtmosphereZone(id,n.getDouble("x1"),n.getDouble("y1"),n.getDouble("z1"),n.getDouble("x2"),n.getDouble("y2"),n.getDouble("z2"),n.getDouble("gravity"),n.getBoolean("vacuum"));return true;}}return false;
    }
    public boolean removeAtmosphereZone(String id){return me.cryo.zombierool.gameplay.LunarEnvironment.get(level).remove(id);}
    public LuaTable getAtmosphereZones(){return deviceTable(me.cryo.zombierool.gameplay.LunarEnvironment.get(level).describe());}
    public boolean giveLunarHelmet(String uuid){var player=getPlayer(uuid);if(player==null)return false;me.cryo.zombierool.gameplay.LunarEquipment.acquire(player);return true;}
    public boolean giveEnvironmentalSuit(String uuid,String style){var player=getPlayer(uuid);if(player==null||!java.util.Set.of("lunar","diving","hazmat").contains(style))return false;me.cryo.zombierool.gameplay.LunarEquipment.acquire(player,me.cryo.zombierool.gameplay.LunarEquipment.SuitStyle.parse(style));return true;}
    public boolean removeLunarHelmet(String uuid){var player=getPlayer(uuid);if(player==null)return false;me.cryo.zombierool.gameplay.LunarEquipment.revoke(player);return true;}
    public boolean setLunarHelmetEquipped(String uuid,boolean equipped){var player=getPlayer(uuid);return player!=null&&me.cryo.zombierool.gameplay.LunarEquipment.setEquipped(player,equipped);}
    public LuaTable getLunarHelmetState(String uuid){var player=getPlayer(uuid);return player==null?new LuaTable():deviceTable(me.cryo.zombierool.gameplay.LunarEquipment.data(player).copy());}

    public void registerInteractable(String id, double x, double y, double z, double radius, String langKey) {
        me.cryo.zombierool.core.manager.InteractableManager.register(id, new Vec3(x, y, z), radius, langKey);
    }

    public void removeInteractable(String id) {
        me.cryo.zombierool.core.manager.InteractableManager.remove(id);
    }

    public void registerTimer(String id, int delayTicks, LuaValue callback) {
        LuaScriptManager.registerTimer(id, delayTicks, callback);
    }

    public void cancelTimer(String id) {
        LuaScriptManager.cancelTimer(id);
    }

    public void setData(String key, String value) {
        LuaScriptManager.setData(key, value);
    }

    public String getData(String key) {
        return LuaScriptManager.getData(key);
    }

    public LuaTable getNearbyPlayers(double x, double y, double z, double radius) {
        LuaTable table = new LuaTable();
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box, p ->
            p.distanceToSqr(x, y, z) <= radius * radius &&
            (p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
        );
        int i = 1;
        for (ServerPlayer p : players) {
            table.set(i++, LuaValue.valueOf(p.getUUID().toString()));
        }
        return table;
    }

    public int getEntityCount(String entityTypeStr) {
        ResourceLocation rl = new ResourceLocation(entityTypeStr.contains(":") ? entityTypeStr : "zombierool:" + entityTypeStr);
        return (int) StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(e -> ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).equals(rl))
                .count();
    }

    public float getPlayerHealth(String uuidStr) {
        ServerPlayer player = getPlayer(uuidStr);
        return player != null ? player.getHealth() : 0f;
    }
    public boolean playKnuckleAnimation(String uuidStr) {
        ServerPlayer player=getPlayer(uuidStr);if(player==null)return false;
        me.cryo.zombierool.gameplay.EquipmentSystem.animate(player,"knuckle_crack",40);return true;
    }
    public String getBlockPowerMode(int x,int y,int z) {
        return me.cryo.zombierool.gameplay.BlockPowerSettings.mode(level,new BlockPos(x,y,z)).name().toLowerCase(java.util.Locale.ROOT);
    }
    public boolean setBlockPowerMode(int x,int y,int z,String source) {
        BlockPos pos=new BlockPos(x,y,z);
        if(!level.hasChunkAt(pos)||!me.cryo.zombierool.gameplay.BlockPowerSettings.configurable(level,pos)||level.getBlockState(pos).getBlock() instanceof me.cryo.zombierool.block.SignalTechnicalBlock)return false;
        try { me.cryo.zombierool.gameplay.BlockPowerSettings.setMode(level,pos,me.cryo.zombierool.gameplay.BlockPowerSettings.Mode.valueOf(source.toUpperCase(java.util.Locale.ROOT)));return true; }
        catch(IllegalArgumentException|NullPointerException ignored){return false;}
    }

    public float getPlayerMaxHealth(String uuidStr) {
        ServerPlayer player = getPlayer(uuidStr);
        return player != null ? player.getMaxHealth() : 0f;
    }

    public boolean isPlayerDown(String uuidStr) {
        try {
            return PlayerDownManager.isPlayerDown(UUID.fromString(uuidStr));
        } catch (Exception e) { return false; }
    }

    public boolean isPlayerCrawling(String uuidStr) {
        try {
            return PlayerCrawlManager.isCrawling(UUID.fromString(uuidStr));
        } catch (Exception e) { return false; }
    }

    public float getPlayerYaw(String uuidStr) {
        ServerPlayer player = getPlayer(uuidStr);
        return player != null ? player.getYRot() : 0f;
    }

    public float getPlayerPitch(String uuidStr) {
        ServerPlayer player = getPlayer(uuidStr);
        return player != null ? player.getXRot() : 0f;
    }

    public String getBlockState(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            return state.toString();
        }
        return "minecraft:air";
    }

    public void setPlayerGameMode(String uuidStr, String modeStr) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null) {
            try {
                GameType mode = GameType.valueOf(modeStr.toUpperCase());
                player.setGameMode(mode);
            } catch (Exception ignored) {}
        }
    }

    public void setWave(int wave) {
        WaveManager.forceSetWave(level, wave);
    }

    public void pauseWave() {
        WaveManager.setGamePaused(true);
    }

    public void resumeWave() {
        WaveManager.setGamePaused(false);
    }

    public void setMaxZombiesAlive(int count) {
        WorldConfig.get(level).setMaxActiveMobsPerPlayer(count);
        WorldConfig.get(level).setDirty();
    }

    public void showTitle(String uuidStr, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            if (title != null && !title.isEmpty()) {
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
            }
            if (subtitle != null && !subtitle.isEmpty()) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            }
        }
    }

    public void showActionBar(String uuidStr, String message) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null && message != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    public void playSound(double x, double y, double z, String soundId, float volume, float pitch) {
        ResourceLocation rl = new ResourceLocation(soundId.contains(":") ? soundId : "minecraft:" + soundId);
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (sound != null) {
            level.playSound(null, x, y, z, sound, SoundSource.MASTER, volume, pitch);
        }
    }

    public void setGlowing(String uuidStr, boolean glowing) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null) {
            player.setGlowingTag(glowing);
        }
    }

    public void setObjective(String uuidStr, String text) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player == null) return;
        UUID pId = player.getUUID();

        if (text == null || text.isEmpty()) {
            if (playerObjectives.containsKey(pId)) {
                playerObjectives.get(pId).removeAllPlayers();
                playerObjectives.remove(pId);
            }
        } else {
            ServerBossEvent be = playerObjectives.computeIfAbsent(pId, k -> {
                ServerBossEvent boss = new ServerBossEvent(Component.literal(text), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
                boss.addPlayer(player);
                return boss;
            });
            be.setName(Component.literal(text));
        }
    }

    public void giveRandomWeapon(String uuidStr, String tier) {
        ServerPlayer player = getPlayer(uuidStr);
        if (player != null) {
            boolean pap = false;
            String searchTier = tier != null ? tier.toUpperCase(Locale.ROOT) : "";

            if (searchTier.contains("PAP")) {
                pap = true;
                searchTier = searchTier.replace("PAP", "").trim();
            }

            List<String> candidates = new ArrayList<>();
            for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
                boolean match = false;
                if (searchTier.isEmpty() || searchTier.equals("ALL") || searchTier.equals("ANY")) {
                    match = true;
                } else if (searchTier.equals("WONDER")) {
                    match = def.is_wonder_weapon || "WONDER".equalsIgnoreCase(def.type);
                } else if (def.type != null && def.type.equalsIgnoreCase(searchTier)) {
                    match = true;
                } else if (def.tags != null && def.tags.contains(searchTier.toLowerCase(Locale.ROOT))) {
                    match = true;
                }

                if (match) {
                    candidates.add(def.id);
                }
            }

            if (!candidates.isEmpty()) {
                String chosen = candidates.get(level.random.nextInt(candidates.size()));
                ItemStack weapon = WeaponFacade.createWeaponStack(chosen, pap, player);
                if (!weapon.isEmpty()) {
                    WeaponFacade.grantWeaponToPlayer(player, weapon);
                }
            }
        }
    }

    public void addPoints(String playerUUIDStr, int amount) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            PointManager.modifyScore(player, amount);
        }
    }

    public void removePoints(String playerUUIDStr, int amount) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            PointManager.modifyScore(player, -amount);
        }
    }

    public int getScore(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? PointManager.getScore(player) : 0;
    }

    public void spawnBoss(double x, double y, double z, String mobType) {
        if(!LuaScriptManager.gameplayCallAllowed(level))return;
        ResourceLocation id=new ResourceLocation(mobType.contains(":")?mobType:"zombierool:"+mobType);
        var type=ForgeRegistries.ENTITY_TYPES.getValue(id);if(type==null)return;
        var entity=type.create(level);if(entity==null)return;entity.addTag("zr_boss");entity.setPos(x,y,z);
        if(entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zr)zr.assignRolledSkins(id.getPath());
        level.addFreshEntity(entity);
    }

    public void spawnMob(double x, double y, double z, String mobType) {
        if(!LuaScriptManager.gameplayCallAllowed(level))return;
        ResourceLocation rl = new ResourceLocation(mobType.contains(":") ? mobType : "zombierool:" + mobType);
        net.minecraft.world.entity.EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type != null) {
            Entity entity = type.create(level);
            if (entity != null) {
                entity.setPos(x, y, z);
                if (entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zrEntity) {
                    zrEntity.assignRolledSkins(rl.getPath());
                }
                level.addFreshEntity(entity);
            }
        }
    }

    public void spawnCustomBoss(double x, double y, double z, String mobType, float hp, float speed, float scale) {
        if(!LuaScriptManager.gameplayCallAllowed(level))return;
        ResourceLocation rl = new ResourceLocation(mobType.contains(":") ? mobType : "zombierool:" + mobType);
        net.minecraft.world.entity.EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type != null) {
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity living) {
                living.addTag("zr_boss");
                living.setPos(x, y, z);
                living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
                living.setHealth(hp);
                if (living.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                    living.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
                }

                if (living instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zrEntity) {
                    zrEntity.setScale(scale);
                    String skin = me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin(rl.getPath());
                    if (skin == null || skin.isEmpty()) {
                        java.util.Map<String, byte[]> skins = me.cryo.zombierool.core.manager.DynamicResourceManager.getAllServerSkins().get(rl.getPath());
                        if (skins != null && !skins.isEmpty()) {
                            skin = skins.keySet().iterator().next();
                        }
                    }
                    if (skin != null && !skin.isEmpty()) {
                        zrEntity.setCustomSkin(skin);
                    }
                    zrEntity.assignEyeSkin(rl.getPath());
                }

                level.addFreshEntity(living);
            }
        }
    }

    public void giveWeapon(String playerUUIDStr, String weaponId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ItemStack weapon = WeaponFacade.createWeaponStack(weaponId, false, player);
            if (!weapon.isEmpty()) {
                WeaponFacade.grantWeaponToPlayer(player, weapon);
            }
        }
    }

    public void packAPunchWeapon(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ItemStack held = player.getMainHandItem();
            if (WeaponFacade.isWeapon(held) && WeaponFacade.canBePackAPunched(held)) {
                WeaponFacade.applyPackAPunch(held);
                WeaponFacade.setAmmo(held, WeaponFacade.getMaxAmmo(held));
                WeaponFacade.setReserve(held, WeaponFacade.getMaxReserve(held));
                player.inventoryMenu.broadcastChanges();
            }
        }
    }

    public void givePerk(String playerUUIDStr, String perkId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            me.cryo.zombierool.gameplay.PerksManager.Perk perk = null;
            if (perkId.equalsIgnoreCase("random")) {
                List<me.cryo.zombierool.gameplay.PerksManager.Perk> unowned = me.cryo.zombierool.gameplay.PerksManager.ALL_PERKS.values().stream()
                        .filter(p -> p.getAssociatedEffect() != null && !player.hasEffect(p.getAssociatedEffect()))
                        .collect(Collectors.toList());
                if (!unowned.isEmpty()) {
                    perk = unowned.get(level.random.nextInt(unowned.size()));
                }
            } else {
                perk = me.cryo.zombierool.gameplay.PerksManager.ALL_PERKS.get(perkId);
            }

            if (perk != null) {
                perk.applyEffect(player);
                me.cryo.zombierool.gameplay.PerksManager.incrementPerkPurchases(perk.getId(), player);
                player.sendSystemMessage(Component.literal("Â§aAtout " + perk.getName() + " obtenu !"));
            }
        }
    }

    public void unlockChannel(int channel) {
        WaveManager.unlockChannel(channel);
    }

    public void lockChannel(int channel) {
        try {
            WaveManager.lockChannel(channel);
        } catch (Exception ignored) {}
    }

    public void clearUnlockedChannels() {
        WaveManager.UNLOCKED_CHANNELS.clear();
    }

    public void clearUnlockedZones() {
        WaveManager.UNLOCKED_ZONES.clear();
    }

    public void setSpawnIntensity(String intensity) {
        try {
            WorldConfig config = WorldConfig.get(level);
            config.setSpawnIntensity(intensity);
            config.setDirty();
            WaveManager.recalculateSpawnInterval(level);
        } catch (Exception ignored) {}
    }

    public void setSpecialWavesEnabled(boolean enabled) {
        try {
            WorldConfig config = WorldConfig.get(level);
            config.setSpecialWavesEnabled(enabled);
            config.setDirty();
        } catch (Exception ignored) {}
    }

    public void openObstacle(String channel) {
        try {
            int c = Integer.parseInt(channel);
            WaveManager.unlockChannel(c);

            List<BlockPos> toBreak = new ArrayList<>();
            for (ObstacleDoorBlockEntity be : ObstacleDoorSystem.OBSTACLES) {
                if (be.getLevel() == level && be.getChannels().contains(c)) {
                    toBreak.add(be.getBlockPos());
                }
            }

            for (BlockPos p : toBreak) {
                level.setBlock(p, ZombieroolModBlocks.PATH.get().defaultBlockState(), 3);
            }
        } catch (NumberFormatException ignored) {}
    }

    public void playGlobalSound(String soundId) { playGlobalSound(soundId, 1.0f, 1.0f); }
    public void playGlobalSound(String soundId, float volume) { playGlobalSound(soundId, volume, 1.0f); }
    public void playSoundForPlayer(String playerUUIDStr, String soundId) { playSoundForPlayer(playerUUIDStr, soundId, 1.0f, 1.0f); }
    public void playSoundForPlayer(String playerUUIDStr, String soundId, float volume) { playSoundForPlayer(playerUUIDStr, soundId, volume, 1.0f); }
    public void playDynamicSoundForPlayer(String playerUUIDStr, String soundId) { playDynamicSoundForPlayer(playerUUIDStr, soundId, 1.0f, 1.0f); }
    public void playDynamicSoundForPlayer(String playerUUIDStr, String soundId, float volume) { playDynamicSoundForPlayer(playerUUIDStr, soundId, volume, 1.0f); }
    public void playSound(double x, double y, double z, String soundId) { playSound(x, y, z, soundId, 1.0f, 1.0f); }
    public void playSound(double x, double y, double z, String soundId, float volume) { playSound(x, y, z, soundId, volume, 1.0f); }

    public void playGlobalSound(String soundId, float volume, float pitch) {
        ResourceLocation rl = new ResourceLocation(soundId.contains(":") ? soundId : "zombierool:" + soundId);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayGlobalSoundPacket(rl, volume, pitch));
    }

    public void playSoundForPlayer(String playerUUIDStr, String soundId, float volume, float pitch) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(soundId.contains(":") ? soundId : "zombierool:" + soundId);
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
            if (sound != null) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
            }
        }
    }

    public void playDynamicSoundForPlayer(String playerUUIDStr, String soundId, float volume, float pitch) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(soundId.contains(":") ? soundId : "zombierool:" + soundId);
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CPlayGlobalSoundPacket(rl, volume, pitch));
        }
    }

    public void setMusicPreset(String preset) {
        WaveManager.currentSessionMusic = preset;
        level.getServer().getPlayerList().getPlayers().forEach(p -> {
            p.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:" + preset));
        });
    }

    public void setFog(String preset) {
        WorldConfig config = WorldConfig.get(level);
        config.setFogPreset(preset);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSetFogPresetPacket(
            preset, config.getCustomFogR(), config.getCustomFogG(), config.getCustomFogB(),
            config.getCustomFogNear(), config.getCustomFogFar(), config.isFogOutdoorsOnly()
        ));
    }

    public void setCustomFog(double r, double g, double b, double near, double far) {
        WorldConfig config = WorldConfig.get(level);
        config.setFogPreset("custom");
        config.setCustomFogR((float)r);
        config.setCustomFogG((float)g);
        config.setCustomFogB((float)b);
        config.setCustomFogNear((float)near);
        config.setCustomFogFar((float)far);
        config.setDirty();

        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSetFogPresetPacket(
            "custom", (float)r, (float)g, (float)b, (float)near, (float)far, config.isFogOutdoorsOnly()
        ));
    }

    public void setConfigFlag(String key, boolean value) {
        WorldConfig config = WorldConfig.get(level);
        switch (key) {
            case "superSprintersEnabled" -> config.setSuperSprintersEnabled(value);
            case "zombiesCanSprint" -> config.setZombiesCanSprint(value);
            case "spookyAmbience" -> config.setSpookyAmbience(value);
            case "forceHalloween" -> config.setForceHalloween(value);
            case "hellhoundFireVariant" -> config.setHellhoundFireVariant(value);
            case "crawlerGasExplosion" -> config.setCrawlerGasExplosion(value);
        }
        config.setDirty();
    }

    public void setConfigString(String key, String value) {
        WorldConfig config = WorldConfig.get(level);
        switch (key) {
            case "deathPenalty" -> config.setDeathPenalty(value);
            case "dayNightMode" -> config.setDayNightMode(value);
        }
        config.setDirty();
    }

    public void forceWunderfizz(int x, int y, int z) {
        WorldConfig config = WorldConfig.get(level);
        BlockPos pos = new BlockPos(x, y, z);
        config.setActiveWunderfizzPosition(pos, level);
    }

    public void forceMysteryBox(int x, int y, int z, boolean isLocked) {
        BlockPos pos = new BlockPos(x, y, z);
        me.cryo.zombierool.gameplay.MysteryBoxManager.get(level).forceLocation(level, pos, isLocked);
    }

    public void excludeMysteryBox(int x, int y, int z, boolean exclude) {
        WorldConfig.get(level).setMysteryBoxExcluded(new BlockPos(x, y, z), exclude);
    }

    public void excludeWunderfizz(int x, int y, int z, boolean exclude) {
        WorldConfig.get(level).setWunderfizzExcluded(new BlockPos(x, y, z), exclude);
    }

    public void setAmmoCratePrice(int price) {
        me.cryo.zombierool.gameplay.AmmoCrateManager.get(level).setBaseCost(price);
    }

    public void resetPlayerPerkPurchases(String playerUUIDStr, String perkId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                cap.setPerkPurchases(perkId, 0);
            });
        }
    }

    public void teleportPlayer(String playerUUIDStr, double x, double y, double z) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.teleportTo(x, y, z);
        }
    }

    public void spawnParticle(String particleId, double x, double y, double z) {
        spawnParticleAdv(particleId, x, y, z, 1, 0, 0, 0, 0);
    }

    public void spawnParticleAdv(String particleId, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        ResourceLocation rl = new ResourceLocation(particleId.contains(":") ? particleId : "minecraft:" + particleId);
        net.minecraft.core.particles.ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
        if (type instanceof net.minecraft.core.particles.SimpleParticleType simple) {
            level.sendParticles(simple, x, y, z, count, dx, dy, dz, speed);
        } else if (type instanceof net.minecraft.core.particles.ParticleOptions opts) {
            level.sendParticles(opts, x, y, z, count, dx, dy, dz, speed);
        }
    }

    public void broadcastMessage(String message) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    public void printConsole(String message) {
        System.out.println("[ZombieRool Lua] " + message);
    }

    public void setBlock(int x, int y, int z, String blockId) {
        ResourceLocation rl = new ResourceLocation(blockId.contains(":") ? blockId : "minecraft:" + blockId);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.isLoaded(pos)) {
                level.setBlock(pos, block.defaultBlockState(), 3);
            }
        }
    }

    public String getBlock(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            return rl != null ? rl.toString() : "minecraft:air";
        }
        return "minecraft:air";
    }

    public void destroyBlock(int x, int y, int z, boolean dropItems) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.isLoaded(pos)) {
            level.destroyBlock(pos, dropItems);
        }
    }

    public void fillBlocks(int x1, int y1, int z1, int x2, int y2, int z2, String blockId) {
        ResourceLocation rl = new ResourceLocation(blockId.contains(":") ? blockId : "minecraft:" + blockId);
        Block block = ForgeRegistries.BLOCKS.getValue(rl);
        if (block != null) {
            BlockState state = block.defaultBlockState();
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);

            for (int i = minX; i <= maxX; i++) {
                for (int j = minY; j <= maxY; j++) {
                    for (int k = minZ; k <= maxZ; k++) {
                        BlockPos pos = new BlockPos(i, j, k);
                        if (level.isLoaded(pos)) {
                            level.setBlock(pos, state, 3);
                        }
                    }
                }
            }
        }
    }

    public void registerLookTrigger(String id, double x, double y, double z, double radius, double seconds, boolean requireScope) {
        me.cryo.zombierool.core.manager.LookTriggerManager.register(id, x, y, z, radius, seconds, requireScope);
    }

    /** A named, stationary marker for scripted look triggers; no map-specific coordinates. */
    /** Map-owned entity templates can be recreated at every match start without duplicates. */
    public String restoreEntityTemplate(String snbt) {
        if(snbt==null||snbt.length()>65536)return "";
        try {
            var tag=net.minecraft.nbt.TagParser.parseTag(snbt);tag.remove("UUID");
            var position=tag.getList("Pos",net.minecraft.nbt.Tag.TAG_DOUBLE);if(position.size()!=3)return "";
            var id=net.minecraft.resources.ResourceLocation.tryParse(tag.getString("id"));if(id==null)return "";
            var type=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);if(type==null)return "";
            double x=position.getDouble(0),y=position.getDouble(1),z=position.getDouble(2);
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return "";
            level.getChunkAt(net.minecraft.core.BlockPos.containing(x,y,z));
            for(var old:level.getEntities(type,new net.minecraft.world.phys.AABB(x-.1,y-.1,z-.1,x+.1,y+2,z+.1),e->e.isAlive()))
                if(old.position().distanceToSqr(new net.minecraft.world.phys.Vec3(x,y,z))<.04)return old.getUUID().toString();
            WorldConfig.get(level).getAllowedMobs().add(id.toString());
            var entity=net.minecraft.world.entity.EntityType.loadEntityRecursive(tag,level,e->e);
            return entity!=null&&level.addFreshEntity(entity)?entity.getUUID().toString():"";
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){return "";}
    }

    public String spawnLookTarget(double x,double y,double z,String name) {
        String label=name==null?"Look target":name.substring(0,Math.min(128,name.length()));
        var bounds=new net.minecraft.world.phys.AABB(x-.5,y-.5,z-.5,x+.5,y+2.5,z+.5);
        for(var old:level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,bounds))
            if(old.getTags().contains("zr_script_look_target")&&old.getName().getString().equals(label))old.discard();
        // This permission belongs to the map's match snapshot and is restored on match end.
        WorldConfig.get(level).getAllowedMobs().add("minecraft:armor_stand");
        var stand=net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
        if(stand==null)return "";
        stand.setPos(x,y,z);stand.setNoGravity(true);stand.setInvulnerable(true);
        stand.setCustomName(Component.literal(label));stand.setCustomNameVisible(true);stand.addTag("zr_script_look_target");
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
        return level.addFreshEntity(stand)?stand.getUUID().toString():"";
    }

    public void removeLookTrigger(String id) {
        me.cryo.zombierool.core.manager.LookTriggerManager.remove(id);
    }

    public String getPlayerName(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? player.getName().getString() : "Unknown";
    }

    public void giveItem(String playerUUIDStr, String itemId, int count) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = new ItemStack(item, count);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }

    public void removeItem(String playerUUIDStr, String itemId, int count) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                int remaining = count;
                for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == item) {
                        int toRemove = Math.min(stack.getCount(), remaining);
                        stack.shrink(toRemove);
                        remaining -= toRemove;
                    }
                }
            }
        }
    }

    public boolean hasItem(String playerUUIDStr, String itemId, int count) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(itemId.contains(":") ? itemId : "minecraft:" + itemId);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                int total = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() == item) {
                        total += stack.getCount();
                    }
                }
                return total >= count;
            }
        }
        return false;
    }

    public LuaTable getInventory(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        LuaTable table = new LuaTable();
        if (player != null) {
            int index = 1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (rl != null) {
                        table.set(index++, LuaValue.valueOf(rl.toString()));
                    }
                }
            }
        }
        return table;
    }

    public void teleportAllPlayers(double x, double y, double z) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                player.teleportTo(x, y, z);
            }
        }
    }

    public void endGame(String message) {
        WaveManager.endGame(level, Component.literal(message));
    }

    public void spawnBonus(String bonusId, double x, double y, double z) {
        if(!LuaScriptManager.gameplayCallAllowed(level))return;
        BonusManager.Bonus bonus = BonusManager.getBonus(bonusId);
        if (bonus != null) {
            BonusManager.spawnBonus(bonus, level, new net.minecraft.world.phys.Vec3(x, y, z));
        }
    }

    public void spawnBonusAtPlayer(String playerUUIDStr, String bonusId) {
        if(!LuaScriptManager.gameplayCallAllowed(level))return;
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            BonusManager.Bonus bonus = BonusManager.getBonus(bonusId);
            if (bonus != null) {
                BonusManager.spawnBonus(bonus, level, player.position());
            }
        }
    }

    public int getRandomInt(int min, int max) {
        if (min > max) return min;
        return level.random.nextInt(max - min + 1) + min;
    }

    public void applyEffect(String playerUUIDStr, String effectId, int durationTicks, int amplifier) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            ResourceLocation rl = new ResourceLocation(effectId.contains(":") ? effectId : "minecraft:" + effectId);
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
            if (effect != null) {
                player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, true));
            }
        }
    }

    public LuaTable getActivePlayers() {
        List<String> activePlayers = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .map(p -> p.getUUID().toString())
                .collect(Collectors.toList());
        LuaTable table = new LuaTable();
        int i = 1;
        for (String uuid : activePlayers) {
            table.set(i++, LuaValue.valueOf(uuid));
        }
        return table;
    }

    private ServerPlayer getPlayer(String uuidStr) {
        return me.cryo.zombierool.util.ServerPlayerLookup.byUuid(level, uuidStr);
    }

    public void triggerElderGuardianOverlay(String playerUUIDStr) {
        triggerScopeScreamer(playerUUIDStr);
    }

    public void triggerScopeScreamer(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CTriggerScopeScreamerPacket());
        }
    }

    public void killNearbyEntities(double x, double y, double z, double radius, String entityType) {
        level.getEntitiesOfClass(Entity.class,
                new net.minecraft.world.phys.AABB(x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius))
                .stream()
                .filter(e -> ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString().equals(entityType))
                .forEach(Entity::discard);
    }

    public double getPlayerX(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? player.getX() : 0.0;
    }

    public double getPlayerY(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? player.getY() : 0.0;
    }

    public double getPlayerZ(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        return player != null ? player.getZ() : 0.0;
    }

    public boolean isPlayerInRadius(String playerUUIDStr, double x, double y, double z, double radius) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            return player.distanceToSqr(x, y, z) <= (radius * radius);
        }
        return false;
    }

    public int getZombiesInRadius(double x, double y, double z, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius),
                e -> e instanceof me.cryo.zombierool.entity.ZombieEntity ||
                     e instanceof me.cryo.zombierool.entity.HellhoundEntity ||
                     e instanceof me.cryo.zombierool.entity.CrawlerEntity)
                .size();
    }

    public void sendMessageToPlayer(String playerUUIDStr, String message) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    public void damagePlayer(String playerUUIDStr, float amount) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.hurt(level.damageSources().magic(), amount);
        }
    }

    public void healPlayer(String playerUUIDStr, float amount) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.heal(amount);
        }
    }

    public boolean hasPerk(String playerUUIDStr, String perkId) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            return me.cryo.zombierool.gameplay.PerksManager.hasPerk(player, perkId);
        }
        return false;
    }

    public void refillAmmo(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof me.cryo.zombierool.api.IReloadable reloadableWeapon) {
                    reloadableWeapon.setAmmo(stack, reloadableWeapon.getMaxAmmo(stack));
                    reloadableWeapon.setReserve(stack, reloadableWeapon.getMaxReserve(stack));
                }
            }
            me.cryo.zombierool.core.system.WeaponFacade.refillAllTaczAmmo(player);
            player.inventoryMenu.broadcastChanges();
        }
    }

    public int getRemainingEnemies(){return WaveManager.remainingEnemies();}

    public int getCurrentWave() {
        return WaveManager.getCurrentWave();
    }

    public boolean isPowerActivated() {
        return me.cryo.zombierool.config.GlobalSwitchState.isActivated(level);
    }

    public void killAllZombies() {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof me.cryo.zombierool.entity.ZombieEntity ||
                entity instanceof me.cryo.zombierool.entity.CrawlerEntity ||
                entity instanceof me.cryo.zombierool.entity.HellhoundEntity) {
                ((LivingEntity) entity).setHealth(0);
                ((LivingEntity) entity).die(level.damageSources().generic());
            }
        }
    }

    public void strikeLightning(double x, double y, double z) {
        net.minecraft.world.entity.LightningBolt lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(x, y, z);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
            level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 5.0f, 1.0f);
            level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0f, 1.0f);
        }
    }

    public void createExplosion(double x, double y, double z, float radius, float damage) {
        me.cryo.zombierool.gameplay.ExplosionControl.doCustomExplosion(
            level, null, new net.minecraft.world.phys.Vec3(x, y, z),
            damage, radius, 1.0f, 0.0f, 0.0f, 1.0f, "EXPLOSION", "zombierool:explosion_old", false
        );
    }

    public void setWeather(String type) {
        if (type.equalsIgnoreCase("clear")) {
            level.setWeatherParameters(6000, 0, false, false);
        } else if (type.equalsIgnoreCase("rain")) {
            level.setWeatherParameters(0, 6000, true, false);
        } else if (type.equalsIgnoreCase("thunder")) {
            level.setWeatherParameters(0, 6000, true, true);
        }
    }

    public void spawnCrawler(double x, double y, double z) {
        if(!LuaScriptManager.gameplayCallAllowed(level))return;
        me.cryo.zombierool.entity.CrawlerEntity crawler = me.cryo.zombierool.init.ZombieroolModEntities.CRAWLER.get().create(level);
        if (crawler != null) {
            crawler.setPos(x, y, z);
            crawler.assignRolledSkins("crawler");
            level.addFreshEntity(crawler);
        }
    }

    public void makeZombieCrawler(String uuidStr) {
        try {
            Entity e = level.getEntity(UUID.fromString(uuidStr));
            if (e instanceof me.cryo.zombierool.entity.ZombieEntity zombie) {
                zombie.makeCrawler();
            }
        } catch(Exception ignored) {}
    }

    public void spawnThrowable(String ownerUuidStr, String throwableType, double x, double y, double z, double vx, double vy, double vz, int fuseTicks) {
        if(!LuaScriptManager.gameplayCallAllowed(level))return;
        try {
            ServerPlayer player = getPlayer(ownerUuidStr);
            me.cryo.zombierool.item.throwable.ThrowableCore.BaseThrowableEntity proj = null;

            if (throwableType.equalsIgnoreCase("molotov")) {
                proj = new me.cryo.zombierool.item.throwable.Molotov.MolotovEntity(level, player);
            } else if (throwableType.equalsIgnoreCase("stielhandgranate")) {
                proj = new me.cryo.zombierool.item.throwable.Stielhandgranate.StielhandgranateEntity(level, player, 100 - fuseTicks);
            } else if (throwableType.equalsIgnoreCase("monkey_bomb")) {
                proj = new me.cryo.zombierool.item.throwable.MonkeyBomb.MonkeyBombEntity(level, player, 100 - fuseTicks);
            } else {
                proj = new me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity(level, player, 100 - fuseTicks);
            }

            if (proj != null) {
                proj.setPos(x, y, z);
                proj.setDeltaMovement(vx, vy, vz);
                level.addFreshEntity(proj);
            }
        } catch(Exception ignored) {}
    }

    public void giveBowieKnife(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            player.getPersistentData().putBoolean("zr_has_bowie_knife", true);
        }
    }

    public boolean hasBowieKnife(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            return player.getPersistentData().getBoolean("zr_has_bowie_knife");
        }
        return false;
    }

    public void performMeleeAttack(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            me.cryo.zombierool.procedures.MeleeAttackHandler.performMeleeAttack(player);
        }
    }

    public void triggerAmmoCrate(String playerUUIDStr) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player != null) {
            me.cryo.zombierool.gameplay.AmmoCrateManager.get(level).tryPurchaseAmmo(player, level, me.cryo.zombierool.gameplay.WaveManager.getCurrentWave());
        }
    }

    public void placePerkMachine(int x, int y, int z, String facingStr, String perkId) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;

        level.setBlock(pos.below(), me.cryo.zombierool.block.system.PerksSystem.DUMMY_BLOCK.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.PART, me.cryo.zombierool.block.system.PerksSystem.DummyPart.LOWER)
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.FACING, facing), 3);
        level.setBlock(pos.above(), me.cryo.zombierool.block.system.PerksSystem.DUMMY_BLOCK.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.PART, me.cryo.zombierool.block.system.PerksSystem.DummyPart.UPPER)
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.FACING, facing), 3);
        level.setBlock(pos, me.cryo.zombierool.block.system.PerksSystem.BLOCK.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlock.FACING, facing)
                .setValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlock.PERK_TYPE, me.cryo.zombierool.block.system.PerksSystem.PerkType.fromString(perkId)), 3);

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlockEntity perkBE) {
            perkBE.setSavedPerkId(perkId);
            if (me.cryo.zombierool.gameplay.PerksManager.ALL_PERKS.containsKey(perkId)) {
                perkBE.setSavedPrice(2000);
            }
        }
    }

    public void placeMysteryBox(int x, int y, int z, String facingStr, boolean active) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;

        BlockPos otherPos = me.cryo.zombierool.gameplay.MysteryBoxManager.getOtherPartPos(pos, facing);

        level.setBlock(pos, me.cryo.zombierool.block.system.MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.FACING, facing)
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.PART, false)
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.ACTIVE, active), 3);
        level.setBlock(otherPos, me.cryo.zombierool.block.system.MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.FACING, facing)
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.PART, true)
                .setValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.ACTIVE, active), 3);
    }

    public void placeWunderfizz(int x, int y, int z, String facingStr) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;

        level.setBlock(pos, me.cryo.zombierool.init.ZombieroolModBlocks.DER_WUNDERFIZZ.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.DerWunderfizzBlock.FACING, facing), 3);
    }

    public void placePowerSwitch(int x, int y, int z, String facingStr, boolean powered) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;

        level.setBlock(pos, me.cryo.zombierool.init.ZombieroolModBlocks.POWER_SWITCH.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.PowerSwitchBlock.FACING, facing)
                .setValue(me.cryo.zombierool.block.PowerSwitchBlock.POWERED, powered), 3);
    }

    /** Recipe IDs and part IDs are unique map-local identifiers; reward SNBT preserves custom item data. */
    public void defineBuildable(String id, String name, String partIds, String rewardSnbt, String mode, int ticks, int limit) {
        try {
            var stack = net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(rewardSnbt));
            me.cryo.zombierool.gameplay.BuildableSystem.get(level).define(id, name, partIds, stack, mode, ticks, limit);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalArgumentException("Invalid reward SNBT", e); }
    }
    public void bindBuildablePart(String recipe, String part, int x, int y, int z) {
        me.cryo.zombierool.gameplay.BuildableSystem.get(level).anchor(level, new BlockPos(x,y,z), recipe, part);
    }
    public void bindBuildableBench(String recipe, int x, int y, int z) {
        me.cryo.zombierool.gameplay.BuildableSystem.get(level).anchor(level, new BlockPos(x,y,z), recipe, "");
    }
    public void bindBuildableItemPart(String recipe, String part, int x, int y, int z, String itemSnbt) {
        try {
            var stack = net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(itemSnbt));
            var pos=new BlockPos(x,y,z);var buildables=me.cryo.zombierool.gameplay.BuildableSystem.get(level);
            if(level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.BuildableBlocks.PartSupport)buildables.configurePart(level,pos,recipe,part,stack);
            else buildables.itemPart(level,pos,recipe,part,stack);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalArgumentException("Invalid part SNBT", e); }
    }
    public void bindBuildableDestination(String recipe,int x,int y,int z) {
        var pos=new BlockPos(x,y,z);var data=me.cryo.zombierool.gameplay.BuildableSystem.get(level);
        if(!level.getBlockState(pos).is(me.cryo.zombierool.block.system.BuildableBlocks.DESTINATION.get()))throw new IllegalArgumentException("Expected a buildable deployment marker");
        data.destination(level,pos,recipe);
        var support=(me.cryo.zombierool.block.system.BuildableBlocks.PartSupport)level.getBlockEntity(pos);
        support.configure(recipe,"destination",net.minecraft.world.item.ItemStack.of(data.definitions().getCompound("recipes").getCompound(recipe).getCompound("reward")));
    }
    public String getCarriedBuildableResult(String uuid) {return me.cryo.zombierool.gameplay.BuildableSystem.get(level).delivery(java.util.UUID.fromString(uuid));}
    public void removeBuildable(String id) { me.cryo.zombierool.gameplay.BuildableSystem.get(level).remove(id); }
    public void unbindBuildable(int x,int y,int z) { me.cryo.zombierool.gameplay.BuildableSystem.get(level).unbind(new BlockPos(x,y,z)); }
    public int getBuildableInstalledCount(int x,int y,int z) { return me.cryo.zombierool.gameplay.BuildableSystem.get(level).installed(new BlockPos(x,y,z)); }
    public boolean isBuildableComplete(int x,int y,int z) { return me.cryo.zombierool.gameplay.BuildableSystem.get(level).completed(new BlockPos(x,y,z)); }
    public String getCarriedBuildablePart(String uuid) { return me.cryo.zombierool.gameplay.BuildableSystem.get(level).carried(java.util.UUID.fromString(uuid)); }
    public void dropBuildablePart(String uuid) {
        var p=level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
        if(p!=null)me.cryo.zombierool.gameplay.BuildableSystem.get(p.serverLevel()).drop(p);
    }

    public void placeMeteorite(int x, int y, int z, boolean active) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, me.cryo.zombierool.core.registry.ZRBlocks.METEORITE.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.system.MeteoriteEasterEgg.MeteoriteBlock.ACTIVE, active), 3);
    }

    public void placeAmmoCrate(int x, int y, int z, String facingStr) {
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.byName(facingStr.toLowerCase(Locale.ROOT));
        if (facing == null) facing = net.minecraft.core.Direction.NORTH;

        level.setBlock(pos, me.cryo.zombierool.init.ZombieroolModBlocks.AMMO_CRATE.get().defaultBlockState()
                .setValue(me.cryo.zombierool.block.AmmoCrateBlock.FACING, facing), 3);
    }

    public int getPackAPunchState(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlockEntity be) {
            return be.getState();
        }
        return -1;
    }

    public boolean insertPackAPunchWeapon(String playerUUIDStr, int x, int y, int z, boolean free) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlockEntity be) {
            if (be.getState() == 0 && level.getBlockState(pos).getValue(me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlock.POWERED)) {
                ItemStack held = player.getMainHandItem();
                if (!held.isEmpty() && me.cryo.zombierool.core.system.WeaponFacade.isWeapon(held) && me.cryo.zombierool.core.system.WeaponFacade.canBePackAPunched(held)) {
                    if (!free) {
                        int price = be.getPrice();
                        if (me.cryo.zombierool.gameplay.PointManager.getScore(player) < price) return false;
                        me.cryo.zombierool.gameplay.PointManager.modifyScore(player, -price);
                    }

                    be.startUpgrading(held.copy(), player.getUUID());
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    level.playSound(null, pos, net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation("zombierool", "buy")), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                    level.playSound(null, pos, net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation("zombierool", "pap_upgrade")), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean collectPackAPunchWeapon(String playerUUIDStr, int x, int y, int z) {
        ServerPlayer player = getPlayer(playerUUIDStr);
        if (player == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.PackAPunchSystem.PackAPunchBlockEntity be) {
            if (be.getState() == 2) {
                if (be.getOwner() == null || be.getOwner().equals(player.getUUID())) {
                    ItemStack upgraded = be.getCurrentWeapon();
                    boolean added = player.getInventory().add(upgraded);
                    if (!added) {
                        player.drop(upgraded, false);
                    }
                    be.reset();
                    player.inventoryMenu.broadcastChanges();
                    me.cryo.zombierool.util.PlayerVoiceManager.playWeaponUpgraded(player, level);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
                    return true;
                }
            }
        }
        return false;
    }

    // All positions and registries are resolved in this map's dimension.
    private me.cryo.zombierool.block.system.MapDeviceSystem.Device mapDevice(int x,int y,int z) {
        BlockPos pos = new BlockPos(x,y,z);
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.MapDeviceSystem.Device d ? d : null;
    }
    public boolean placeGlassDefenseDoor(int x,int y,int z,String facing) {
        var direction=net.minecraft.core.Direction.byName(facing.toLowerCase(Locale.ROOT));
        BlockPos pos=new BlockPos(x,y,z);
        if(direction==null||direction.getAxis().isVertical()||!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos.above()))return false;
        var block=me.cryo.zombierool.core.registry.ZRBlocks.GLASS_DEFENSE_DOOR.get();
        var state=block.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING,direction);
        level.setBlock(pos,state,3);level.setBlock(pos.above(),state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
        me.cryo.zombierool.block.system.GlassDoorEvents.track(level,pos);me.cryo.zombierool.block.system.GlassDoorEvents.track(level,pos.above());return true;
    }
    public LuaTable getGlassDefenseDoor(int x,int y,int z) {
        var out=new LuaTable();var state=level.getBlockState(new BlockPos(x,y,z));
        if(state.getBlock() instanceof me.cryo.zombierool.block.system.GlassDefenseDoorBlock){out.set("stage",state.getValue(me.cryo.zombierool.block.system.GlassDefenseDoorBlock.STAGE));out.set("breached",LuaValue.valueOf(state.getValue(me.cryo.zombierool.block.system.GlassDefenseDoorBlock.BREACHED)));}return out;
    }
    public boolean damageGlassDefenseDoor(int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);if(!(level.getBlockState(pos).getBlock() instanceof me.cryo.zombierool.block.system.GlassDefenseDoorBlock))return false;
        me.cryo.zombierool.block.system.GlassDefenseDoorBlock.damage(level,pos);return true;
    }
    public boolean repairGlassDefenseDoor(int x,int y,int z,int planks) {
        BlockPos pos=new BlockPos(x,y,z);var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof me.cryo.zombierool.block.system.GlassDefenseDoorBlock door)||!state.getValue(me.cryo.zombierool.block.system.GlassDefenseDoorBlock.BREACHED))return false;
        door.updateStage(level,pos,Math.max(0,Math.min(5,planks)));return true;
    }
    public boolean placeMapDevice(int x,int y,int z,String kind,String facing) {
        try {
            var type=me.cryo.zombierool.block.system.MapDeviceSystem.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
            var direction=net.minecraft.core.Direction.byName(facing.toLowerCase(Locale.ROOT));
            BlockPos pos=new BlockPos(x,y,z);
            if(direction==null || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos))return false;
            return level.setBlock(pos,me.cryo.zombierool.block.system.MapDeviceSystem.TYPES.get(type).get().defaultBlockState().setValue(me.cryo.zombierool.block.system.MapDeviceSystem.DeviceBlock.FACING,direction),3);
        }catch(IllegalArgumentException e){return false;}
    }
    public LuaTable getSpecialEnemiesConfig(){return deviceTable(me.cryo.zombierool.config.SpecialEnemyConfig.get(level));}
    public boolean setMiniBossLootItem(boolean first,String itemId,int count,String nbt){
        var id=net.minecraft.resources.ResourceLocation.tryParse(itemId);var item=id==null?null:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);if(item==null)return false;
        var stack=new net.minecraft.world.item.ItemStack(item,Math.max(1,Math.min(count,item.getMaxStackSize())));try{if(!nbt.isBlank())stack.setTag(net.minecraft.nbt.TagParser.parseTag(nbt));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){return false;}
        var update=new net.minecraft.nbt.CompoundTag();String key=first?"firstLoot":"recurringLoot";update.put(key+"Stack",stack.save(new net.minecraft.nbt.CompoundTag()));update.putString(key,"");me.cryo.zombierool.config.SpecialEnemyConfig.update(level,update);return true;
    }
    public LuaTable getAddonMapConfig(){return deviceTable(me.cryo.zombierool.config.WorldConfig.get(level).getAddonConfig());}
    public void configureSpecialEnemies(LuaTable settings){me.cryo.zombierool.config.SpecialEnemyConfig.update(level,mergeDevice(me.cryo.zombierool.config.SpecialEnemyConfig.get(level),settings));}
    public String getMiniBossType(){return me.cryo.zombierool.config.SpecialEnemyConfig.get(level).getString("type");}
    public boolean despawnEncounter(String id){var e=specialEnemy(id);if(e==null||!e.isEncounter())return false;e.encounter.returnWeapons();me.cryo.zombierool.gameplay.WaveManager.onMobDeath(e,level);e.discard();return true;}
    public boolean setRomeroEnraged(String id,boolean enraged){var e=specialEnemy(id);if(e==null||!me.cryo.zombierool.gameplay.EncounterRules.persistent(e))return false;e.encounter.setRage(enraged?me.cryo.zombierool.config.SpecialEnemyConfig.get(level).getInt("romeroRageTicks"):0);return true;}
    public LuaTable getEncounterState(String id){var e=specialEnemy(id);if(e==null||!e.isEncounter())return new LuaTable();var t=e.encounter.save();t.putString("kind",me.cryo.zombierool.gameplay.EncounterAbilities.kind(e));t.remove("stolen");return deviceTable(t);}
    public LuaTable findSafeEnemyTeleport(String playerUUID){var p=scriptPlayer(playerUUID);var t=new LuaTable();if(p==null)return t;var at=me.cryo.zombierool.gameplay.EncounterAbilities.safeDestination(p,24);if(at!=null){t.set("x",LuaValue.valueOf(at.x));t.set("y",LuaValue.valueOf(at.y));t.set("z",LuaValue.valueOf(at.z));}return t;}
    public String spawnEncounter(String kind,double x,double y,double z){if(!LuaScriptManager.gameplayCallAllowed(level))return "";
        if(!me.cryo.zombierool.gameplay.WaveManager.isGameRunning()||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return "";
        if(kind.equals("pentagon_thief")&&!me.cryo.zombierool.gameplay.EncounterRules.thief(level))return "";
        var profile=me.cryo.zombierool.config.MiniBossDefinitions.get(kind.contains(":")?kind:"zombierool:"+kind);
        var type=kind.equals("pentagon_thief")?me.cryo.zombierool.init.ZombieroolModEntities.PENTAGON_THIEF.get():kind.equals("geist")?me.cryo.zombierool.init.ZombieroolModEntities.GEIST.get():profile.isEmpty()?null:me.cryo.zombierool.init.ZombieroolModEntities.CUSTOM_MINI_BOSS.get();if(type==null)return "";var e=type.create(level);if(e==null)return "";
        if(!profile.isEmpty()){e.setBossProfile(profile);for(var pair:java.util.List.of(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)){String key=pair==net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH?"health":pair==net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED?"speed":"damage";e.getAttribute(pair).setBaseValue(profile.getDouble(key));}e.setHealth((float)profile.getDouble("health"));}
        e.getPersistentData().putBoolean("zr_special_spawner",true);e.moveTo(x,y,z,0,0);return level.hasChunkAt(e.blockPosition())&&level.noCollision(e)&&level.addFreshEntity(e)?e.getUUID().toString():"";
    }
    public LuaTable listMiniBossTypes(){var out=new LuaTable();int i=1;for(String id:me.cryo.zombierool.config.MiniBossDefinitions.ids())out.set(i++,id);return out;}
    public boolean isMachineLocked(int x,int y,int z){return me.cryo.zombierool.gameplay.BrutusMachineLocks.locked(level,new BlockPos(x,y,z));}
    public boolean lockMachine(int x,int y,int z){return me.cryo.zombierool.gameplay.BrutusMachineLocks.lock(level,new BlockPos(x,y,z));}
    public boolean setMiniBossType(String type){if(!me.cryo.zombierool.config.MiniBossDefinitions.contains(type))return false;var changes=new net.minecraft.nbt.CompoundTag();changes.putString("type",type);me.cryo.zombierool.config.SpecialEnemyConfig.update(level,changes);return true;}
    public LuaTable listBossLootTables(){var out=new LuaTable();int i=1;for(String id:me.cryo.zombierool.gameplay.MiniBossLoot.tables(level))out.set(i++,id);return out;}
    public int getMiniBossKillCount(){return me.cryo.zombierool.gameplay.MiniBossLoot.kills(level);}
    public void resetMiniBossLoot(){me.cryo.zombierool.gameplay.MiniBossLoot.reset(level);}
    public LuaTable getMapConfig(){var tag=new net.minecraft.nbt.CompoundTag();me.cryo.zombierool.config.WorldConfig.get(level).saveEditable(tag);return deviceTable(tag);}
    public void configureMap(LuaTable settings){var config=me.cryo.zombierool.config.WorldConfig.get(level);var tag=new net.minecraft.nbt.CompoundTag();config.saveEditable(tag);config.loadEditable(mergeDevice(tag,settings));config.setDirty();me.cryo.zombierool.config.SpecialEnemyConfig.syncSpawners(level);}
    private me.cryo.zombierool.entity.SpecialEnemyEntity specialEnemy(String id){try{return level.getEntity(java.util.UUID.fromString(id)) instanceof me.cryo.zombierool.entity.SpecialEnemyEntity enemy?enemy:null;}catch(IllegalArgumentException invalid){return null;}}
    public LuaTable listSpecialEnemies(){var out=new LuaTable();int i=1;for(var e:level.getAllEntities())if(e instanceof me.cryo.zombierool.entity.SpecialEnemyEntity enemy&&enemy.isAlive())out.set(i++,getSpecialEnemy(enemy.getUUID().toString()));return out;}
    public LuaTable getSpecialEnemy(String id){var enemy=specialEnemy(id);var out=new LuaTable();if(enemy==null)return out;out.set("uuid",id);out.set("type",enemy.kind());out.set("health",LuaValue.valueOf(enemy.getHealth()));out.set("maxHealth",LuaValue.valueOf(enemy.getMaxHealth()));out.set("x",LuaValue.valueOf(enemy.getX()));out.set("y",LuaValue.valueOf(enemy.getY()));out.set("z",LuaValue.valueOf(enemy.getZ()));out.set("boss",LuaValue.valueOf(enemy.isBoss()));out.set("helmet",LuaValue.valueOf(enemy.legacyBoss()&&enemy.hasBossHelmet()));out.set("reactorCover",LuaValue.valueOf(enemy.isPanzer()&&enemy.hasCoreCover()));out.set("reactorCore",LuaValue.valueOf(enemy.isPanzer()&&enemy.hasPowerCore()));out.set("capturedPlayer",LuaValue.valueOf(enemy.hasCapturedPlayer()));out.set("descending",LuaValue.valueOf(enemy.isDescending()));out.set("enraged",LuaValue.valueOf(enemy.isNapalmEnraged()||enemy.bossProfile().getBoolean("enraged")));return out;}
    public boolean releasePanzerGrab(String id){var enemy=specialEnemy(id);if(enemy==null||!enemy.isPanzer())return false;enemy.releaseCapture();return true;}
    public boolean shockMiniBoss(String id){var enemy=specialEnemy(id);return enemy!=null&&enemy.isBoss()&&enemy.teleporterShock();}
    public boolean damageSpecialEnemy(String id,double damage,double x,double y,double z,String playerUUID){var enemy=specialEnemy(id);var player=scriptPlayer(playerUUID);if(enemy==null||player==null||!Double.isFinite(damage)||damage<=0||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;enemy.recordShotImpact(new net.minecraft.world.phys.Vec3(x,y,z),player);return enemy.hurt(player.damageSources().playerAttack(player),(float)Math.min(1000000,damage));}
    public LuaTable listSpecialSpawners(){var out=new LuaTable();int i=1;for(var be:me.cryo.zombierool.spawner.SpawnerRegistry.getSpawners(level))if(be.getMobType().special())out.set(i++,getSpecialSpawner(be.getBlockPos().getX(),be.getBlockPos().getY(),be.getBlockPos().getZ()));return out;}
    public LuaTable getSpecialSpawner(int x,int y,int z){if(!(level.getBlockEntity(new BlockPos(x,y,z)) instanceof me.cryo.zombierool.block.system.UniversalSpawnerSystem.UniversalSpawnerBlockEntity be)||!be.getMobType().special())return new LuaTable();var tag=be.specialConfig();tag.putString("type",be.getMobType().boss()?getMiniBossType():be.getMobType().getSerializedName());tag.putString("zone",be.getZone());tag.putString("startChannels",be.getStartChannels());tag.putString("stopChannels",be.getStopChannels());tag.putBoolean("requirePower",be.doesRequirePower());tag.putInt("weight",be.getSpawnWeight());tag.putInt("x",x);tag.putInt("y",y);tag.putInt("z",z);return deviceTable(tag);}
    public boolean configureSpecialSpawner(int x,int y,int z,LuaTable settings){if(!(level.getBlockEntity(new BlockPos(x,y,z)) instanceof me.cryo.zombierool.block.system.UniversalSpawnerSystem.UniversalSpawnerBlockEntity be)||!be.getMobType().special())return false;var tag=mergeDevice(be.specialConfig(),settings);var current=getSpecialSpawner(x,y,z);var requested=settings.get("type");if(be.getMobType().boss()&&!requested.isnil()&&!setMiniBossType(requested.tojstring()))return false;var type=be.getMobType().boss()?me.cryo.zombierool.config.SpecialEnemyConfig.type(level):me.cryo.zombierool.block.system.UniversalSpawnerSystem.SpawnerMobType.fromString(requested.optjstring(current.get("type").tojstring()));if(!type.special()||be.getMobType().boss()!=type.boss())return false;be.setConfig(type,settings.get("zone").optjstring(be.getZone()),settings.get("startChannels").optjstring(be.getStartChannels()),settings.get("stopChannels").optjstring(be.getStopChannels()),settings.get("requirePower").optboolean(be.doesRequirePower()),settings.get("weight").optint(be.getSpawnWeight()));be.setSpecialConfig(mergeDevice(be.specialConfig(),settings));return true;}
    public boolean spawnSpecialEnemy(int x,int y,int z){if(!LuaScriptManager.gameplayCallAllowed(level))return false;return level.getBlockEntity(new BlockPos(x,y,z)) instanceof me.cryo.zombierool.block.system.UniversalSpawnerSystem.UniversalSpawnerBlockEntity be&&be.getMobType().special()&&me.cryo.zombierool.gameplay.WaveManager.isGameRunning()&&me.cryo.zombierool.gameplay.SpecialEnemySpawns.spawn(level,be,be.getMobType().boss());}
    public boolean spawnNapalmFire(double x,double y,double z,int seconds){if(!LuaScriptManager.gameplayCallAllowed(level))return false;if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!me.cryo.zombierool.gameplay.WaveManager.isGameRunning())return false;var fire=me.cryo.zombierool.init.ZombieroolModEntities.NAPALM_FIRE.get().create(level);if(fire==null)return false;fire.setPos(x,y,z);fire.setLifetime(Math.max(1,Math.min(120,seconds))*20);return level.addFreshEntity(fire);}
    public boolean shakePlayerScreen(String playerUUID,int ticks,double strength){var player=scriptPlayer(playerUUID);if(player==null||!Double.isFinite(strength))return false;me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.network.packet.S2CScreenShakePacket(Math.max(1,Math.min(200,ticks)),(float)Math.max(0,Math.min(12,strength)),me.cryo.zombierool.client.ScreenShakeHandler.ShakeType.IMPACT));return true;}

    private static LuaTable deviceTable(net.minecraft.nbt.CompoundTag config){
        var table=new LuaTable();for(String key:config.getAllKeys()){
            var tag=config.get(key);
            if(tag instanceof net.minecraft.nbt.CompoundTag nested)table.set(key,deviceTable(nested));
            else if(tag instanceof net.minecraft.nbt.ListTag list){var rows=new LuaTable();for(int i=0;i<list.size();i++){var row=list.get(i);if(row instanceof net.minecraft.nbt.CompoundTag compound)rows.set(i+1,deviceTable(compound));else if(row instanceof net.minecraft.nbt.NumericTag n)rows.set(i+1,LuaValue.valueOf(n.getAsDouble()));else rows.set(i+1,list.getString(i));}table.set(key,rows);}
            else if(tag instanceof net.minecraft.nbt.ByteTag&&key.equals("Count")&&config.contains("id",net.minecraft.nbt.Tag.TAG_STRING))table.set(key,LuaValue.valueOf(config.getInt(key)));
            else if(tag instanceof net.minecraft.nbt.ByteTag)table.set(key,LuaValue.valueOf(config.getBoolean(key)));
            else if(tag instanceof net.minecraft.nbt.NumericTag number)table.set(key,LuaValue.valueOf(number.getAsDouble()));
            else if(tag instanceof net.minecraft.nbt.StringTag)table.set(key,config.getString(key));
        }return table;
    }
    private static net.minecraft.nbt.CompoundTag mergeDevice(net.minecraft.nbt.CompoundTag current,LuaTable changes){
        var result=current.copy();for(String key:current.getAllKeys()){
            var value=changes.get(key);if(value.isnil())continue;var tag=current.get(key);
            if(tag instanceof net.minecraft.nbt.CompoundTag nested)result.put(key,mergeDevice(nested,value.checktable()));
            else if(tag instanceof net.minecraft.nbt.ListTag list&&list.getElementType()==net.minecraft.nbt.Tag.TAG_STRING){var rows=new net.minecraft.nbt.ListTag();var input=value.checktable();for(int i=1;i<=Math.min(4096,input.length());i++)rows.add(net.minecraft.nbt.StringTag.valueOf(input.get(i).checkjstring()));result.put(key,rows);}
            else if(tag instanceof net.minecraft.nbt.ByteTag)result.putBoolean(key,value.checkboolean());
            else if(tag instanceof net.minecraft.nbt.IntTag)result.putInt(key,value.checkint());
            else if(tag instanceof net.minecraft.nbt.NumericTag){double n=value.checkdouble();if(!Double.isFinite(n))throw new org.luaj.vm2.LuaError("Finite number required: "+key);result.putDouble(key,n);}
            else if(tag instanceof net.minecraft.nbt.StringTag)result.putString(key,value.checkjstring());
        }return result;
    }
    private me.cryo.zombierool.block.system.SoulChestSystem.Chest soulObjective(String id){
        var pos=me.cryo.zombierool.block.system.SoulObjectiveRegistry.get(level).position(id);if(pos==null)return null;
        level.getChunkAt(pos);return level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.SoulChestSystem.Chest chest?chest:null;
    }
    public LuaTable listSoulObjectives(){var out=new LuaTable();int i=1;for(String id:me.cryo.zombierool.block.system.SoulObjectiveRegistry.get(level).ids())out.set(i++,id);return out;}
    public boolean isSoulObjectiveComplete(String id){return me.cryo.zombierool.block.system.SoulObjectiveRegistry.get(level).complete(id);}
    public boolean areSoulObjectivesComplete(String ids){return me.cryo.zombierool.block.system.SoulObjectiveRegistry.get(level).allComplete(ids);}
    public LuaTable getSoulObjective(String id){
        var chest=soulObjective(id);if(chest==null)return new LuaTable();var table=deviceTable(chest.config());
        table.set("kind",chest instanceof me.cryo.zombierool.block.system.CerberusSystem.Head?"cerberus":"soul_chest");table.set("collected",chest.collected);table.set("completed",LuaValue.valueOf(chest.completed));
        table.set("x",chest.getBlockPos().getX());table.set("y",chest.getBlockPos().getY());table.set("z",chest.getBlockPos().getZ());
        if(chest instanceof me.cryo.zombierool.block.system.CerberusSystem.Head head){table.set("phase",head.phase);table.set("revealed",LuaValue.valueOf(head.revealed));}return table;
    }
    public boolean configureSoulObjective(String id,LuaTable settings){var chest=soulObjective(id);if(chest==null)return false;chest.configure(mergeDevice(chest.config(),settings));return true;}
    public boolean resetSoulObjective(String id){var chest=soulObjective(id);if(chest==null)return false;chest.reset();return true;}
    private me.cryo.zombierool.block.system.TeleporterSystem.Pad scriptTeleporter(String id){
        var positions=me.cryo.zombierool.block.system.TeleporterRegistry.get(level).named(id);if(positions.size()!=1)return null;var pos=positions.get(0);level.getChunkAt(pos);return me.cryo.zombierool.block.system.TeleporterSystem.pad(level,pos);
    }
    public LuaTable listTeleporters(){var out=new LuaTable();int i=1;for(String id:me.cryo.zombierool.block.system.TeleporterRegistry.get(level).pads.values().stream().map(n->n.getString("id")).distinct().sorted().toList())out.set(i++,id);return out;}
    public LuaTable getTeleporter(String id){var pad=scriptTeleporter(id);if(pad==null)return new LuaTable();var table=deviceTable(pad.config);table.set("linked",LuaValue.valueOf(pad.linked));table.set("busy",LuaValue.valueOf(pad.busy));table.set("powered",LuaValue.valueOf(pad.hasPower()));table.set("cooldownTicks",LuaValue.valueOf(Math.max(0,pad.cooldownUntil-level.getGameTime())));table.set("readiness",pad.readiness());return table;}
    public boolean configureTeleporter(String id,LuaTable settings){var pad=scriptTeleporter(id);if(pad==null||pad.busy)return false;pad.configure(mergeDevice(pad.config,settings));return true;}
    public boolean resetTeleporterCooldown(String id){var pad=scriptTeleporter(id);if(pad==null||pad.busy)return false;pad.cooldownUntil=0;pad.sync();return true;}
    public String activateTeleporter(String id,String playerUUID){var pad=scriptTeleporter(id);var player=scriptPlayer(playerUUID);return pad==null||player==null?"message.zombierool.teleporter.no_destination":pad.activate(player);}
    public boolean getTeleporterCondition(String channel,String condition){return me.cryo.zombierool.block.system.TeleporterRegistry.get(level).condition(channel,condition);}
    public LuaTable getNavigationLink(int x,int y,int z){return level.getBlockEntity(new BlockPos(x,y,z)) instanceof me.cryo.zombierool.block.system.TraversalSystem.Anchor anchor?deviceTable(anchor.config()):new LuaTable();}
    public boolean configureNavigationLink(int x,int y,int z,LuaTable settings){if(!(level.getBlockEntity(new BlockPos(x,y,z)) instanceof me.cryo.zombierool.block.system.TraversalSystem.Anchor anchor))return false;anchor.configure(mergeDevice(anchor.config(),settings));return true;}
    public void setSpawnerChannels(String enable,String disable,int durationSeconds){var n=new net.minecraft.nbt.CompoundTag();n.putString("enableChannels",enable);n.putString("disableChannels",disable);n.putInt("channelDuration",Math.max(0,Math.min(86400,durationSeconds)));me.cryo.zombierool.gameplay.SpawnChannelActions.apply(level,n);}
    private ServerPlayer scriptPlayer(String id){try{var p=level.getServer().getPlayerList().getPlayer(UUID.fromString(id));return p!=null&&p.level()==level?p:null;}catch(IllegalArgumentException e){return null;}}
    public LuaTable getEquipmentLoadout(String playerUUID){var p=scriptPlayer(playerUUID);var table=new LuaTable();if(p!=null)p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(c->{table.set("equipment",c.getEquipmentType());table.set("equipmentCharges",c.getEquipmentCount());table.set("tactical",c.getTacticalType());table.set("tacticalCharges",c.getTacticalCount());table.set("lethal",c.getLethalType());table.set("lethalCharges",c.getLethalCount());});return table;}
    public boolean equipEquipment(String playerUUID,String kind){var p=scriptPlayer(playerUUID);var equipment=me.cryo.zombierool.gameplay.EquipmentSystem.Kind.of(kind);if(p==null||equipment==null)return false;me.cryo.zombierool.gameplay.EquipmentSystem.equip(p,equipment);return true;}
    public boolean setEquipmentCharges(String playerUUID,int equipment,int tactical){var p=scriptPlayer(playerUUID);if(p==null)return false;var cap=p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).resolve();if(cap.isEmpty())return false;var c=cap.get();c.setEquipmentCount(me.cryo.zombierool.gameplay.EquipmentSystem.Kind.of(c.getEquipmentType())==null?0:Math.max(0,Math.min(2,equipment)));c.setTacticalCount(me.cryo.zombierool.gameplay.EquipmentSystem.Kind.of(c.getTacticalType())==null?0:Math.max(0,Math.min(2,tactical)));c.sync(p);return true;}
    public LuaTable getEnemyStatus(String entityUUID){var out=new LuaTable();Entity e;try{e=level.getEntity(UUID.fromString(entityUUID));}catch(IllegalArgumentException ex){return out;}if(!(e instanceof LivingEntity))return out;var data=e.getPersistentData();long now=level.getGameTime();for(String key:new String[]{"flash","pheromone","winter_frozen"})out.set(key+"Ticks",LuaValue.valueOf(Math.max(0,data.getLong("zr_"+key+"_until")-now)));out.set("alive",LuaValue.valueOf(e.isAlive()));out.set("boss",LuaValue.valueOf(me.cryo.zombierool.gameplay.EquipmentEffects.boss((LivingEntity)e)));return out;}

    public LuaTable getSkyDelivery(){return deviceTable(me.cryo.zombierool.block.system.MachineDeliverySystem.sky(level).status());}
    public boolean configureSkyDelivery(LuaTable settings){var d=me.cryo.zombierool.block.system.MachineDeliverySystem.sky(level);d.configure(mergeDevice(d.config(),settings));return true;}
    public String triggerSkyDelivery(){return me.cryo.zombierool.block.system.MachineDeliverySystem.sky(level).trigger("",null);}
    public String deliverSkyMachineAt(String machine,int x,int y,int z){return me.cryo.zombierool.block.system.MachineDeliverySystem.sky(level).trigger(machine,new BlockPos(x,y,z));}
    public int cancelSkyDelivery(){return me.cryo.zombierool.block.system.MachineDeliverySystem.sky(level).cancel();}
    public void resetSkyDelivery(){me.cryo.zombierool.block.system.MachineDeliverySystem.sky(level).resetGroup();}
    public void notifyDeliveryDoorChannel(int channel){me.cryo.zombierool.block.system.MachineDeliverySystem.onDoorOpened(level,channel);}
    private me.cryo.zombierool.block.system.MachineDeliverySystem.Delivery machineDelivery(int x,int y,int z){return me.cryo.zombierool.block.system.MachineDeliverySystem.at(level,new BlockPos(x,y,z));}
    public boolean placeMachineDelivery(int x,int y,int z,String facing,LuaTable settings){var p=new BlockPos(x,y,z);var d=net.minecraft.core.Direction.byName(facing);if(d==null||d.getAxis().isVertical())return false;level.getChunkAt(p);if(!level.getBlockState(p).canBeReplaced())return false;level.setBlock(p,me.cryo.zombierool.block.system.MachineDeliverySystem.BLOCK.get().defaultBlockState().setValue(me.cryo.zombierool.block.system.MachineDeliverySystem.DeliveryBlock.FACING,d),3);return configureMachineDelivery(x,y,z,settings);}
    public LuaTable getMachineDelivery(int x,int y,int z){var d=machineDelivery(x,y,z);return d==null?new LuaTable():deviceTable(d.status());}
    public LuaTable listMachineDeliveries(){var out=new LuaTable();int i=1;for(var d:me.cryo.zombierool.block.system.MachineDeliverySystem.all(level)){var p=d.getBlockPos();var row=deviceTable(d.status());row.set("x",p.getX());row.set("y",p.getY());row.set("z",p.getZ());out.set(i++,row);}return out;}
    public LuaTable listDeliveryMachines(){var out=new LuaTable();int i=1;for(String id:me.cryo.zombierool.block.system.MachineDeliverySystem.catalog())out.set(i++,id);return out;}
    public boolean configureMachineDelivery(int x,int y,int z,LuaTable settings){var d=machineDelivery(x,y,z);if(d==null)return false;try{var n=mergeDevice(d.config(),settings);var pool=settings.get("pool");if(!pool.isnil()){var rows=new net.minecraft.nbt.ListTag();for(int i=1;i<=Math.min(128,pool.checktable().length());i++)rows.add(net.minecraft.nbt.StringTag.valueOf(pool.get(i).checkjstring()));n.put("pool",rows);}d.configure(n);return true;}catch(IllegalStateException ex){return false;}}
    public String triggerMachineDelivery(int x,int y,int z){var d=machineDelivery(x,y,z);return d==null?"not_found":d.trigger("",null);}
    public String deliverMachineAt(int x,int y,int z,String machine,int targetX,int targetY,int targetZ){var d=machineDelivery(x,y,z);return d==null?"not_found":d.trigger(machine,new BlockPos(targetX,targetY,targetZ));}
    public boolean resetMachineDelivery(int x,int y,int z,boolean wholeGroup){var d=machineDelivery(x,y,z);if(d==null)return false;if(wholeGroup)d.resetGroup();else d.reset();return true;}
    public int cancelMachineDelivery(int x,int y,int z){var d=machineDelivery(x,y,z);return d==null?0:d.cancel();}
    public boolean setMachineDeliveryEnabled(int x,int y,int z,boolean enabled){var d=machineDelivery(x,y,z);if(d==null)return false;var n=d.config();n.putBoolean("enabled",enabled);d.configure(n);return true;}
    public boolean setDeliveryMachinePrice(int x,int y,int z,String machine,int price){var d=machineDelivery(x,y,z);if(d==null||!me.cryo.zombierool.block.system.MachineDeliverySystem.catalog().contains(machine))return false;var n=d.config();n.getCompound("prices").putInt(machine,price);try{d.configure(n);return true;}catch(IllegalStateException ex){return false;}}

    public LuaTable getMapDevice(int x,int y,int z) {
        LuaTable result = new LuaTable(); var d = mapDevice(x,y,z); if(d==null)return result;
        var n=d.config();
        for(String key:n.getAllKeys()) {
            if(key.equals("channel") || key.equals("effect")) result.set(key,n.getString(key));
            else if((key.equals("enabled") || key.equals("requires_power"))) result.set(key,LuaValue.valueOf(n.getBoolean(key)));
            else result.set(key,LuaValue.valueOf(n.getDouble(key)));
        }
        result.set("kind",d.kind().name());result.set("active_ticks",d.remaining);result.set("cooldown_ticks",d.cooldown);
        result.set("owner",d.owner==null?"":d.owner.toString());result.set("error",d.lastError);return result;
    }
    public boolean configureMapDevice(int x,int y,int z,LuaTable config) {
        var d=mapDevice(x,y,z);if(d==null)return false;var n=d.config();
        for(String key:n.getAllKeys()) {
            LuaValue v=config.get(key);if(v.isnil())continue;
            if(key.equals("channel") || key.equals("effect"))n.putString(key,v.checkjstring());
            else if((key.equals("enabled") || key.equals("requires_power")))n.putBoolean(key,v.checkboolean());
            else if(java.util.Set.of("interval","count","cost","duration","recharge").contains(key))n.putInt(key,v.checkint());
            else n.putFloat(key,(float)v.checkdouble());
        }
        d.stop();d.configure(n);return true;
    }
    public boolean setEmitterEnabled(int x,int y,int z,boolean enabled) {
        var d=mapDevice(x,y,z);if(d==null || !me.cryo.zombierool.block.system.MapDeviceSystem.isEmitter(d.kind()))return false;var n=d.config();n.putBoolean("enabled",enabled);d.configure(n);return true;
    }
    public boolean pulseEmitter(int x,int y,int z) {var d=mapDevice(x,y,z);return d!=null && d.emit();}
    public String activateTrap(int x,int y,int z,String playerUUID,boolean charge) {
        var d=mapDevice(x,y,z);if(d==null)return "message.zombierool.trap.not_found";
        try {return d.activate(level.getServer().getPlayerList().getPlayer(UUID.fromString(playerUUID)),charge);}
        catch(IllegalArgumentException e){return "message.zombierool.trap.invalid_player";}
    }
    public boolean stopTrap(int x,int y,int z){var d=mapDevice(x,y,z);if(d==null)return false;d.stop();return true;}
    public boolean resetTrap(int x,int y,int z){var d=mapDevice(x,y,z);if(d==null)return false;d.stop();d.cooldown=0;return true;}
    public LuaTable listMapDevices() {
        LuaTable out=new LuaTable();int i=1;
        for(var d:me.cryo.zombierool.block.system.MapDeviceSystem.devices(level)) {var pos=d.getBlockPos();var item=getMapDevice(pos.getX(),pos.getY(),pos.getZ());item.set("x",pos.getX());item.set("y",pos.getY());item.set("z",pos.getZ());out.set(i++,item);}return out;
    }
    public LuaTable listEmitterEffects(String kind) {
        java.util.TreeSet<String> ids=new java.util.TreeSet<>();
        if("sound".equalsIgnoreCase(kind)){ForgeRegistries.SOUND_EVENTS.getKeys().forEach(id -> ids.add(id.toString()));ids.addAll(me.cryo.zombierool.core.manager.DynamicResourceManager.customAudioIds());}
        else { ForgeRegistries.PARTICLE_TYPES.getKeys().forEach(id -> ids.add(id.toString())); ids.addAll(me.cryo.zombierool.core.manager.DynamicResourceManager.customParticleIds()); }
        LuaTable out=new LuaTable();int i=1;for(String id:ids)out.set(i++,id);return out;
    }
    public String spawnMrChief(int x,int y,int z) {
        var cat=me.cryo.zombierool.gameplay.MrChiefEasterEgg.spawn(level,new BlockPos(x,y,z),true);return cat==null?"":cat.getUUID().toString();
    }
    public void queueMrChief(){me.cryo.zombierool.gameplay.MrChiefEasterEgg.queueNextWave(level);}
    public String spawnZombieCat(int x,int y,int z){return spawnMrChief(x,y,z);}
    public LuaTable getZombieCatConfig(){return getMrChiefConfig();}
    public void configureZombieCat(LuaTable settings){configureMrChief(settings);}
    public LuaTable getMrChiefConfig() {
        var n=me.cryo.zombierool.config.MrChiefConfig.get(level).save(new net.minecraft.nbt.CompoundTag());var out=new LuaTable();
        out.set("enabled",LuaValue.valueOf(n.getBoolean("enabled")));out.set("chance",n.getInt("chance"));out.set("flee_seconds",n.getInt("flee_seconds"));out.set("reward",n.getInt("reward"));out.set("party_seconds",n.getInt("party_seconds"));out.set("flee_radius",n.getFloat("flee_radius"));return out;
    }
    public void configureMrChief(LuaTable config) {
        var settings=me.cryo.zombierool.config.MrChiefConfig.get(level);var n=settings.save(new net.minecraft.nbt.CompoundTag());
        for(String key:n.getAllKeys()){var v=config.get(key);if(v.isnil())continue;if(key.equals("enabled"))n.putBoolean(key,v.checkboolean());else if(key.equals("flee_radius"))n.putFloat(key,(float)v.checkdouble());else n.putInt(key,v.checkint());}settings.configure(n);
    }
    public void setTeleporterCondition(String channel, String condition, boolean active) {
        if(channel==null||condition==null||channel.length()>128||condition.length()>128)return;
        me.cryo.zombierool.block.system.TeleporterRegistry.get(level).condition(channel,condition,active);
    }
    public int getTeleporterDefcon(String channel) {
        return Math.min(5,1+me.cryo.zombierool.block.system.TeleporterRegistry.get(level).conditionCount(channel));
    }
    public void endTeleporterTrip(String channel) {
        me.cryo.zombierool.block.system.TeleporterTravel.endTrips(level,channel);
    }

    public LuaTable getAmbientDevice(int x,int y,int z){var d=mapDevice(x,y,z);return d!=null&&me.cryo.zombierool.gameplay.AmbientScenery.kind(d.kind())?deviceTable(d.config()):new LuaTable();}
    public boolean configureAmbientDevice(int x,int y,int z,LuaTable changes){var d=mapDevice(x,y,z);if(d==null||!me.cryo.zombierool.gameplay.AmbientScenery.kind(d.kind()))return false;d.configure(mergeDevice(d.config(),changes));return true;}
    public boolean setAmbientDeviceEnabled(int x,int y,int z,boolean enabled){var d=mapDevice(x,y,z);if(d==null||!me.cryo.zombierool.gameplay.AmbientScenery.kind(d.kind()))return false;var n=d.config();n.putBoolean("enabled",enabled);d.configure(n);return true;}

}

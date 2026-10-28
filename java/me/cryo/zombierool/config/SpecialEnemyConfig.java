package me.cryo.zombierool.config;

import me.cryo.zombierool.block.system.UniversalSpawnerSystem.SpawnerMobType;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Map-wide boss selection, balance and loot. Spawners only choose eligible positions. */
public final class SpecialEnemyConfig {
    private record NumberSetting(double value,double min,double max,boolean integer) {}
    private static final Map<String,NumberSetting> NUMBERS=new LinkedHashMap<>();
    static {
        number("romeroRageTicks",240,20,1200,true);number("romeroProvokeRadius",1.8,.5,6,false);number("romeroRageSpeed",.36,.05,1,false);number("romeroReturnRounds",3,1,20,true);
        number("astronautDamage",3,0,20,false);number("astronautStealPerk",1,0,1,true);number("abominationPullDistance",5,1,16,false);number("geistLeapDamage",2,0,20,false);number("thiefEscapeTicks",900,60,3600,true);
        number("crawlerFractionMin",.15,0,1,false);number("crawlerFractionMax",.40,0,1,false);
        number("firstWave",8,1,10000,true);number("interval",4,1,10000,true);
        number("panzerHealth",480,20,1000000,false);number("brutusHealth",360,20,1000000,false);
        number("waveHealthGrowth",.08,0,2,false);number("extraPlayerHealth",.5,0,3,false);number("bodyDamage",.25,.05,1,false);
        number("panzerSpeed",.30,.05,1,false);number("brutusSpeed",.36,.05,1,false);
        number("panzerFlameDamage",2,.25,20,false);
        number("panzerHoldTicks",60,15,400,true);number("brutusUnlockCost",2000,0,1000000,true);number("brutusUnlockTicks",60,20,400,true);number("bossSwitchWave",12,1,10000,true);number("bossSwitchX",0,-30000000,30000000,true);number("bossSwitchY",80,-2048,2048,true);number("bossSwitchZ",0,-30000000,30000000,true);
        number("clawSpeed",1.65,.5,4,false);number("clawRadius",.45,.05,1,false);number("clawWindup",5,1,40,true);
        number("screenShake",1,0,3,false);number("teleporterDamage",.1,0,.5,false);
        number("napalmHealth",4,1,20,false);number("napalmSpeed",.16,.05,1,false);number("napalmEnragedSpeed",.34,.05,1,false);
        number("napalmWarningRadius",12,3,32,false);number("napalmDetonationRadius",2.5,.5,6,false);
        number("fireDuration",8,1,120,true);number("skeletonHealth",4,1,200,false);number("skeletonSpeed",.34,.05,1,false);
        for(String prefix:List.of("napalm","skeleton")){
            number(prefix+"FirstWave",prefix.equals("napalm")?5:1,1,10000,true);
            number(prefix+"Radius",12,1,128,true);number(prefix+"Cooldown",prefix.equals("napalm")?30:8,1,3600,true);
            number(prefix+"MaxAlive",prefix.equals("napalm")?1:12,1,64,true);number(prefix+"Batch",prefix.equals("napalm")?1:3,1,16,true);
        }
    }
    private static void number(String key,double value,double min,double max,boolean integer){NUMBERS.put(key,new NumberSetting(value,min,max,integer));}
    public static CompoundTag defaults(){
        var tag=new CompoundTag();tag.putString("specialWaveEnemy","hellhound");tag.putString("crawlerVariant","nova");tag.putString("crawlerBehavior","gas");tag.putString("type","panzer");tag.putString("bossSwitchTrigger","none");tag.putString("bossSwitchTarget","brutus");tag.putString("bossSwitchSoulIds","");tag.put("bossOverrides",new CompoundTag());tag.putString("firstLoot","");tag.putString("recurringLoot","");tag.put("firstLootStack",new CompoundTag());tag.put("recurringLootStack",new CompoundTag());tag.putBoolean("recurringOnFirst",false);tag.putBoolean("napalmUseRadius",false);tag.putBoolean("skeletonUseRadius",true);
        var keyDrops=new CompoundTag();keyDrops.putBoolean("brutus",true);tag.put("wardenKeyDrops",keyDrops);tag.putInt("encounterBalanceVersion",2);
        NUMBERS.forEach((key,s)->{if(s.integer)tag.putInt(key,(int)s.value);else tag.putDouble(key,s.value);});return tag;
    }
    public static CompoundTag sanitize(CompoundTag input){
        var out=defaults();for(String key:List.of("specialWaveEnemy","crawlerVariant")){String v=input.getString(key);if((key.equals("specialWaveEnemy")?List.of("hellhound","pentagon_thief"):List.of("nova","geist")).contains(v))out.putString(key,v);}
        String behavior=input.getString("crawlerBehavior");out.putString("crawlerBehavior",List.of("gas","leaper").contains(behavior)?behavior:input.getString("crawlerVariant").equals("geist")?"leaper":"gas");
        String type=input.getString("type");if(MiniBossDefinitions.contains(type)||(type.contains(":")&&ResourceLocation.tryParse(type)!=null))out.putString("type",type);
        String trigger=input.getString("bossSwitchTrigger");if(List.of("none","wave","power","activator","soul_checker").contains(trigger))out.putString("bossSwitchTrigger",trigger);String target=input.getString("bossSwitchTarget");if(MiniBossDefinitions.contains(target)||(target.contains(":")&&ResourceLocation.tryParse(target)!=null))out.putString("bossSwitchTarget",target);String souls=input.getString("bossSwitchSoulIds");if(souls.length()<=512)out.putString("bossSwitchSoulIds",souls);
        var overrides=new CompoundTag();for(String id:input.getCompound("bossOverrides").getAllKeys()){if(ResourceLocation.tryParse(id)==null)continue;var raw=input.getCompound("bossOverrides").getCompound(id);var values=new CompoundTag();for(String key:List.of("health","speed","damage")){if(!raw.contains(key,Tag.TAG_ANY_NUMERIC))continue;double value=raw.getDouble(key);if(Double.isFinite(value))values.putDouble(key,Math.max(key.equals("speed")?.05:1,Math.min(key.equals("speed")?1:1000000,value)));}overrides.put(id,values);}out.put("bossOverrides",overrides);
        for(String key:List.of("firstLoot","recurringLoot")){String id=input.getString(key);if(id.length()<=256&&(id.isBlank()||ResourceLocation.tryParse(id)!=null))out.putString(key,id.trim());}
        for(String key:List.of("firstLootStack","recurringLootStack")){if(input.contains(key,Tag.TAG_COMPOUND)){var stack=net.minecraft.world.item.ItemStack.of(input.getCompound(key));if(!stack.isEmpty()){stack.setCount(Math.max(1,Math.min(stack.getCount(),stack.getMaxStackSize())));out.put(key,stack.save(new CompoundTag()));}}}
        out.putBoolean("recurringOnFirst",input.getBoolean("recurringOnFirst"));
        var keyDrops=out.getCompound("wardenKeyDrops");for(String id:input.getCompound("wardenKeyDrops").getAllKeys())if(ResourceLocation.tryParse(id)!=null&&input.getCompound("wardenKeyDrops").contains(id,Tag.TAG_BYTE))keyDrops.putBoolean(id,input.getCompound("wardenKeyDrops").getBoolean(id));out.put("wardenKeyDrops",keyDrops);
        for(String key:List.of("napalmUseRadius","skeletonUseRadius"))if(input.contains(key,Tag.TAG_BYTE))out.putBoolean(key,input.getBoolean(key));
        NUMBERS.forEach((key,s)->{if(input.contains(key,Tag.TAG_ANY_NUMERIC)){double value=input.getDouble(key);if(Double.isFinite(value)){value=Math.max(s.min,Math.min(s.max,value));if(s.integer)out.putInt(key,(int)value);else out.putDouble(key,value);}}});if(input.getInt("encounterBalanceVersion")<2&&input.getInt("thiefEscapeTicks")==240)out.putInt("thiefEscapeTicks",900);return out;
    }
    public static CompoundTag get(ServerLevel level){return WorldConfig.get(level).getSpecialEnemies();}
    public static void update(ServerLevel level,CompoundTag changes){
        String previous=get(level).getString("type");var settings=get(level);settings.merge(changes);
        // A configured item is replaced as a whole: recursive NBT merge would retain
        // the previous reward's name/camo/quest data when the new item has no tag.
        for(String key:List.of("firstLootStack","recurringLootStack"))if(changes.contains(key,Tag.TAG_COMPOUND))settings.put(key,changes.getCompound(key).copy());
        WorldConfig.get(level).setSpecialEnemies(settings);for(var player:level.players())me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.scripting.LuaWorkbenchPackets.Response(me.cryo.zombierool.gameplay.MapActors.get(level).catalogue()));syncSpawners(level);me.cryo.zombierool.gameplay.EncounterRules.enforce(level);if(changes.contains("brutusUnlockCost"))me.cryo.zombierool.gameplay.BrutusMachineLocks.sync(level);String next=get(level).getString("type");if(!previous.equals(next))me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnMiniBossTypeChanged",previous,next);
    }
    public static void syncSpawners(ServerLevel level){var settings=get(level);for(var be:SpawnerRegistry.getSpawners(level))if(be.getMobType().special())be.applyMapBossSettings(settings);}
    public static CompoundTag spawnerSettings(ServerLevel level,SpawnerMobType type){
        var settings=get(level);var out=new CompoundTag();
        if(type.boss()){out.putInt("FirstWave",settings.getInt("firstWave"));out.putInt("WaveInterval",settings.getInt("interval"));return out;}
        String prefix=type==SpawnerMobType.NAPALM_MINION?"napalm":"skeleton";
        for(String key:List.of("FirstWave","Radius","Cooldown","MaxAlive","Batch"))out.putInt(key,settings.getInt(prefix+key));
        out.putBoolean("UseRadius",settings.getBoolean(prefix+"UseRadius"));return out;
    }
    public static SpawnerMobType type(ServerLevel level){return get(level).getString("type").equals("brutus")?SpawnerMobType.BRUTUS:SpawnerMobType.PANZER;}
    public static void adoptLegacySpawner(ServerLevel level,SpawnerMobType type,CompoundTag settings){
        var config=WorldConfig.get(level);if(config.hasSpecialEnemies())return;var changes=new CompoundTag();changes.putString("type",type.getSerializedName());changes.putInt("firstWave",settings.getInt("FirstWave"));changes.putInt("interval",settings.getInt("WaveInterval"));config.setSpecialEnemies(get(level).merge(changes));
    }
    private SpecialEnemyConfig() {}
}

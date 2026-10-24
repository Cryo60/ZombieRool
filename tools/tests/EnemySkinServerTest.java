package local.enemyappearancechecks;

import me.cryo.zombierool.config.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

@Mod("enemyappearancechecks")
@Mod.EventBusSubscriber(modid="enemyappearancechecks")
public final class EnemySkinServerTest {
    private static int checks;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var server=event.getServer();server.execute(()->{
            try{
                var level=server.overworld();var config=WorldConfig.get(level);var previous=new CompoundTag();config.saveEditable(previous);
                try{
                    for(String preset:EnemySkinPresets.zombies()){
                        var patch=new CompoundTag();patch.putString("zombieSkinPreset",preset);patch.putString("hellhoundSkinPreset","auto");config.loadEditable(patch);
                        var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);zombie.setPos(0,100,0);level.addFreshEntity(zombie);
                        String skin=zombie.getCustomSkin();
                        check("original".equals(preset)?skin.isEmpty():skin.startsWith("@preset/zombies/"+preset+"/"),"Entity join selects map preset");
                        var saved=new CompoundTag();zombie.addAdditionalSaveData(saved);var restored=ZombieroolModEntities.ZOMBIE.get().create(level);restored.readAdditionalSaveData(saved);
                        check(restored.getCustomSkin().equals(skin),"NBT preserves variant across reload");
                        zombie.assignRolledSkins("zombie");check("original".equals(preset)?zombie.getCustomSkin().isEmpty():EnemySkinPresets.texture(zombie.getCustomSkin())!=null,"Wave spawn resolves built-in texture");
                        var hound=ZombieroolModEntities.HELLHOUND.get().create(level);hound.assignRolledSkins("hellhound");
                        check("original".equals(preset)?hound.getCustomSkin().isEmpty():EnemySkinPresets.texture(hound.getCustomSkin())!=null,"Auto hellhound follows source generation");
                        zombie.discard();restored.discard();hound.discard();
                    }
                    for(String preset:EnemySkinPresets.hounds()){
                        var patch=new CompoundTag();patch.putString("hellhoundSkinPreset",preset);config.loadEditable(patch);
                        var hound=ZombieroolModEntities.HELLHOUND.get().create(level);hound.setPos(0,100,0);level.addFreshEntity(hound);
                        check("original".equals(preset)?hound.getCustomSkin().isEmpty():hound.getCustomSkin().startsWith("@preset/hounds/"+preset+"/"),"Independent hound selection");hound.discard();
                    }
                    var classic=ZombieroolModEntities.ZOMBIE.get().create(level);classic.setCustomSkin("");classic.setPos(0,100,0);level.addFreshEntity(classic);
                    check(classic.getCustomSkin().isEmpty(),"Explicit classic appearance is not rerolled at entity join");classic.discard();
                    var classicTag=new CompoundTag();classicTag.putString("CustomSkin","");var loadedClassic=ZombieroolModEntities.ZOMBIE.get().create(level);loadedClassic.readAdditionalSaveData(classicTag);loadedClassic.setPos(0,100,0);level.addFreshEntity(loadedClassic);
                    check(loadedClassic.getCustomSkin().isEmpty(),"Saved classic appearance survives reload without reroll");loadedClassic.discard();
                    var custom=ZombieroolModEntities.ZOMBIE.get().create(level);custom.setCustomSkin("map_scientist");custom.setPos(0,100,0);level.addFreshEntity(custom);
                    check(custom.getCustomSkin().equals("map_scientist"),"Explicit map skin remains authoritative");custom.discard();
                    var skins=me.cryo.zombierool.core.manager.DynamicResourceManager.getAllServerSkins();var old=skins.get("zombie");
                    var flag=me.cryo.zombierool.core.manager.DynamicResourceManager.class.getDeclaredField("overrideSkins");flag.setAccessible(true);boolean before=flag.getBoolean(null);
                    try{
                        skins.put("zombie",new java.util.HashMap<>(java.util.Map.of("map_scientist",new byte[]{1})));flag.setBoolean(null,true);
                        var rolled=ZombieroolModEntities.ZOMBIE.get().create(level);rolled.assignRolledSkins("zombie");check(rolled.getCustomSkin().equals("map_scientist"),"Configured custom skin override wins over built-in presets");rolled.discard();
                    }finally{if(old==null)skins.remove("zombie");else skins.put("zombie",old);flag.setBoolean(null,before);}
                    check(EnemySkinPresets.zombiePreset("templars").equals("origins"),"Legacy crusaders migrate to Front");
                    for(String legacy:java.util.List.of("t4","t5","t6"))check(EnemySkinPresets.houndPreset(legacy).equals("infernal"),"Legacy hound selection migration");
                    check(EnemySkinPresets.hounds().size()==2&&!EnemySkinPresets.zombies().contains("templars"),"Only consolidated visible presets");
                    var api=new me.cryo.zombierool.scripting.ZombieroolAPI(level);
                    var enemy=ZombieroolModEntities.ZOMBIE.get().create(level);enemy.getPersistentData().putBoolean("zr_debug_spawn",true);enemy.setNoAi(true);enemy.setPos(0,100,0);level.getChunkAt(enemy.blockPosition());
                    check(level.addFreshEntity(enemy),"Live Lua test entity accepted");String uuid=enemy.getUUID().toString();
                    check(api.setEnemyAppearance(uuid,"five",1)&&enemy.getCustomSkin().equals("@preset/zombies/five/0"),"Live variant selection is one based");
                    check(!api.setEnemyAppearance(uuid,"five",999)&&!api.setEntitySkin(uuid,"@preset/hounds/t5/0"),"Lua rejects wrong variant/species");
                    check(api.setEnemySkinPreset("zombie","origins",true)>=1&&enemy.getCustomSkin().startsWith("@preset/zombies/origins/"),"Live batch skin replacement");
                    var globals=me.cryo.zombierool.shadow.luaj.vm2.lib.jse.JsePlatform.standardGlobals();globals.set("ZombieroolAPI",me.cryo.zombierool.scripting.LegacySoundBindings.wrap(api));globals.set("enemy",uuid);
                    globals.load("local a=ZombieroolAPI:getEnemySkinPresets(); assert(#a.zombies.origins==5); assert(a.zombies.templars==nil); assert(a.hounds.t5==nil); assert(ZombieroolAPI:setEnemyAppearance(enemy,'mob_of_dead',3)); assert(ZombieroolAPI:getEntitySkin(enemy)=='@preset/zombies/mob_of_dead/2'); assert(#ZombieroolAPI:getEnemyAppearances()>=1); ZombieroolAPI:configureLunarEnvironment(0.167,true,15); local c=ZombieroolAPI:getLunarEnvironment(); assert(c.vacuum and c.oxygenSeconds==15); ZombieroolAPI:setAtmosphereZone('test_room',-2,90,-2,2,110,2,1,false)","lunar-api-test").call();checks+=9;
                    var environment=me.cryo.zombierool.gameplay.LunarEnvironment.get(level);var fallback=new me.cryo.zombierool.gameplay.LunarEnvironment.Conditions(.167,true);
                    check(!environment.at(0,100,0,fallback).vacuum()&&environment.at(0,100,0,fallback).gravity()==1,"Pressurized zone overrides lunar defaults");
                    check(environment.at(5,100,0,fallback).vacuum(),"Outside zone retains vacuum");
                    var restoredEnvironment=me.cryo.zombierool.gameplay.LunarEnvironment.load(environment.describe());check(!restoredEnvironment.at(0,100,0,fallback).vacuum(),"Atmosphere survives world save");environment.remove("test_room");
                    var station=me.cryo.zombierool.block.system.LunarStation.STATION.get();var base=new net.minecraft.core.BlockPos(10,100,10);level.getChunkAt(base);var lower=station.defaultBlockState();level.setBlock(base,lower,3);station.setPlacedBy(level,base,lower,null,net.minecraft.world.item.ItemStack.EMPTY);
                    check(level.getBlockState(base.above()).is(station)&&level.getBlockState(base.above()).getValue(me.cryo.zombierool.block.system.LunarStation.StationBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER,"Station places two block halves");
                    level.destroyBlock(base.above(),false);check(level.getBlockState(base).isAir(),"Removing upper half removes lower half");
                    enemy.discard();
                }finally{config.loadEditable(previous);}
                System.out.println("ENEMY APPEARANCE SERVER PASS: "+checks+" checks");
            }catch(Throwable e){e.printStackTrace();System.out.println("ENEMY APPEARANCE SERVER FAIL");}
            finally{server.halt(false);}
        });
    }
}

package local.atmospheretests;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.scripting.*;
import net.minecraft.nbt.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospheretests")
public final class MapAtmosphereGameplayTest {
    private int checks;
    public MapAtmosphereGameplayTest(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    private void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE CHECK: "+text);}
    private void lua(String code,String text){String result=LuaScriptManager.executeString(code);check(result.contains("true")&&!result.contains("Error"),text+" -> "+result);}
    private void start(ServerStartedEvent event){var server=event.getServer();var l=server.overworld();try{
        // Reset only this isolated test world's custom library; repeated runs must not fill the 32 slots.
        var clean=MapAtmosphere.defaults();var oldPresets=new ListTag();for(Tag tag:MapAtmosphere.get(l).presetLibrary())oldPresets.add(StringTag.valueOf(((CompoundTag)tag).getString("id")));clean.put("_deletedPresets",oldPresets);if(!MapAtmosphere.get(l).saveEditor(l,clean))throw new AssertionError("Cannot reset isolated fixture presets");
        var defaults=MapAtmosphere.defaults();check(!defaults.getBoolean("enabled"),"Existing maps keep their current sky, image and audio by default");
        var bad=defaults.copy();bad.putDouble("gamma",Double.NaN);bad.putDouble("contrast",-999);bad.putDouble("vignette",100);bad.putString("skyTexture","invalid resource?");var safe=MapAtmosphere.sanitize(bad);check(safe.getDouble("gamma")==1&&safe.getDouble("contrast")==0&&safe.getDouble("vignette")==1,"Nonfinite and out-of-range image settings are sanitized");check(safe.getString("skyTexture").equals(defaults.getString("skyTexture")),"Invalid texture identifiers safely use the default resource");
        var rows=new ListTag();for(int i=0;i<100;i++)rows.add(MapAtmosphere.source("s"+i,"zombierool:ambient_distant_thunder",false,false,0,80,0,1,128,10,20));bad.put("sources",rows);check(MapAtmosphere.sanitize(bad).getList("sources",10).size()==MapAtmosphere.MAX_SOURCES,"A map cannot create an unbounded number of ambience sources");
        var duplicate=rows.getCompound(0).copy();rows=new ListTag();rows.add(duplicate);rows.add(duplicate.copy());bad.put("sources",rows);check(MapAtmosphere.sanitize(bad).getList("sources",10).size()==1,"Duplicate source identifiers are collapsed");
        var source=new CompoundTag();source.putString("id","alarm");source.putDouble("intervalMin",100);source.putDouble("intervalMax",1);source.putDouble("volume",100);source.putDouble("pitch",0);var normalized=MapAtmosphere.sanitizeSource(source);check(normalized.getDouble("intervalMax")==100&&normalized.getDouble("volume")==4&&normalized.getDouble("pitch")==.25,"Source intervals and gains have safe bounds");
        for(String id:MapAtmosphere.presetIds()){var preset=MapAtmosphere.preset(id);check(preset.equals(MapAtmosphere.sanitize(preset)),"Preset survives normalization: "+id);if(!id.equals("none"))check(preset.getList("sources",10).size()>=2,"Preset includes independent ambience sources: "+id);}
        check(MapAtmosphere.preset("die_rise").getList("sources",10).size()==34,"Die Rise keeps all original environmental sources without truncation");
        var origins=MapAtmosphere.preset("origins");check(origins.getList("sources",10).size()==8,"Origins includes the original battle and weather sources");
        int weather=0;for(Tag tag:origins.getList("sources",10)){var row=(CompoundTag)tag;if(row.getString("id").contains("weather")&&row.getBoolean("enabled"))weather++;}check(weather==1,"Origins enables one weather bed without stacking rain and snow");
        var d=MapAtmosphere.get(l);d.configure(l,MapAtmosphere.ashfall());check(d.settings().getList("sources",10).size()==22,"Nuketown includes every original environmental alias with resolved assets");var outside=d.settings();outside.putBoolean("enabled",false);check(d.settings().getBoolean("enabled"),"Callers cannot mutate persisted settings without configure");
        var saved=d.save(new CompoundTag());check(saved.getCompound("settings").equals(MapAtmosphere.sanitize(MapAtmosphere.ashfall())),"All sky, grading and source definitions are serialized");
        source=MapAtmosphere.source("addon","example:custom_ambient",true,false,10,20,30,.5,96,50,100);check(d.putSource(l,source),"Custom addon/resource-pack sound identifiers are accepted");source.putDouble("volume",.2);check(d.putSource(l,source)&&d.settings().getList("sources",10).size()==23,"Updating one source replaces its definition without duplication");check(d.removeSource(l,"addon")&&!d.removeSource(l,"addon"),"Removing a source is deterministic");check(!d.trigger(l,"missing"),"Unknown source trigger fails without sending a sound");
        LuaScriptManager.loadScripts(l);
        lua("return #ZombieroolAPI:getMapAtmospherePresets()>=5 and #ZombieroolAPI:getAmbientSoundLibrary()>=11 and #ZombieroolAPI:getMapSkyboxes()>=4", "Lua exposes preset, sound and sky libraries");
        lua("local zr=ZombieroolAPI; zr:setMapAtmospherePreset('ashfall'); return zr:getMapAtmosphere().enabled and #zr:getAmbientSources()==22", "Lua can select a preset and inspect all source definitions");
        lua("local zr=ZombieroolAPI; zr:setSkybox('example:textures/sky/custom.png','panorama',45,0.8); local s=zr:getMapAtmosphere(); return s.skyTexture=='example:textures/sky/custom.png' and s.skyLayout=='panorama' and s.skyYaw==45", "Lua can select custom resource-pack skies and orientation");
        lua("local zr=ZombieroolAPI; zr:setColorGrading(1.5,1.1,0.4,1.2); zr:setScreenEffects(0.7,0.3,0.05); local s=zr:getMapAtmosphere(); return s.exposure==1.5 and s.dirt==0.3 and s.vignette==0.7", "Lua exposes grading, vignette, dirt and grain");
        lua("local zr=ZombieroolAPI; zr:configureAmbientSource('custom',{sound='example:alarm',x=15,y=80,z=-9,volume=0.4,loop=true,radius=200}); local s=zr:getAmbientSource('custom'); return s.sound=='example:alarm' and s.loop and s.radius==200", "Lua defines positional looping addon ambience");
        lua("local zr=ZombieroolAPI; zr:setAmbientSourceEnabled('custom',false); return not zr:getAmbientSource('custom').enabled", "Lua independently toggles a source");
        lua("function OnAmbientSound(id,sound) ambientTest=id end; ZombieroolAPI:triggerAmbientSound('amb_thunder'); return ambientTest=='amb_thunder'", "Manual sound triggers call the Lua event with the source identity");
        lua("function OnMapAtmosphereChanged() ZombieroolAPI:setAmbientVolume(0.6) end; ZombieroolAPI:setAmbientVolume(0.8); return ZombieroolAPI:getMapAtmosphere().ambientVolume==0.6", "Recursive settings callbacks are bounded");
        lua("OnMapAtmosphereChanged=nil; return ZombieroolAPI:removeAmbientSource('custom') and #ZombieroolAPI:getAmbientSources()==22", "Lua removes sources and preserves the remaining ambience");
        check(server.getCommands().getDispatcher().getRoot().getChild("zratmosphere")==null,"Atmosphere authoring is menu-only; the old command is absent");
        var original=MapAtmosphere.preset("moon");String custom=d.createPreset("My Moon",original);original.putDouble("saturation",3);check(d.resolvePreset(custom).getDouble("saturation")==.6,"Named custom presets copy their source instead of retaining mutable references");check(d.availablePresets().contains(custom)&&d.displayPresetName(custom).equals("My Moon"),"Custom presets appear in the map library with their chosen name");
        var restored=MapAtmosphere.load(d.save(new CompoundTag()));check(restored.resolvePreset(custom).equals(d.resolvePreset(custom)),"Custom preset names and all definitions survive save/reload");
        var edited=d.resolvePreset(custom);edited.putDouble("saturation",.2);String second=d.createPreset("My Moon v2",edited);check(d.resolvePreset(custom).getDouble("saturation")==.6&&d.resolvePreset(second).getDouble("saturation")==.2,"Editing a preset through a new copy preserves the original definition");
        var editor=d.resolvePreset(second);var additions=new ListTag();var attempt=new CompoundTag();attempt.putString("id",custom);attempt.putString("name","Overwrite");attempt.put("settings",edited);additions.add(attempt);editor.put("_presets",additions);int presetCount=d.presetLibrary().size();check(d.saveEditor(l,editor)&&d.resolvePreset(custom).getDouble("saturation")==.2&&d.presetLibrary().size()==presetCount,"Editing an existing custom preset updates it without adding copies");
        for(int adjust=0;adjust<5;adjust++){edited.putDouble("exposure",1+adjust*.1);attempt.put("settings",edited);check(d.saveEditor(l,editor)&&d.presetLibrary().size()==presetCount,"Repeated custom preset adjustment retains one identity: "+adjust);}
        String newId="custom_"+java.util.UUID.randomUUID().toString().replace("-","");attempt.putString("id",newId);attempt.putString("name","Editor copy");editor.putString("presetId",newId);check(d.saveEditor(l,editor)&&d.settings().getString("presetId").equals(newId)&&d.availablePresets().contains(newId),"Saving an editor draft persists its new preset and activates it atomically");
        lua("local zr=ZombieroolAPI; local id=zr:saveMapAtmospherePreset('Lua copy'); zr:setMapAtmospherePreset(id); return zr:getMapAtmosphere().presetId==id and #zr:getMapAtmospherePresets()>=9", "Lua can save and select map-local custom presets");
        var deletion=MapAtmosphere.defaults();var deleted=new ListTag();deleted.add(StringTag.valueOf(custom));deletion.put("_deletedPresets",deleted);check(d.saveEditor(l,deletion)&&!d.availablePresets().contains(custom),"Editor can delete an existing custom preset");
        check(!MapAtmosphere.load(d.save(new CompoundTag())).availablePresets().contains(custom),"Deleted presets remain absent after a reload");
        deleted.clear();deleted.add(StringTag.valueOf("moon"));check(!d.saveEditor(l,deletion)&&d.availablePresets().contains("moon"),"Supplied presets cannot be deleted through editor packets");
        var crossFile=java.nio.file.Path.of("C:/Users/ecorange1/Downloads/skybox_lune_croix.png");
        if(java.nio.file.Files.exists(crossFile)){
            byte[] originalBytes=java.nio.file.Files.readAllBytes(crossFile);var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(originalBytes));byte[] converted=MapAtmosphereImports.normalizeSky(originalBytes);var atlas=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(converted));int size=image.getWidth()/4;
            check(image.getWidth()==4096&&image.getHeight()==3072&&atlas.getWidth()==3072&&atlas.getHeight()==2048,"Actual 4096x3072 Moon cross imports as a 3072x2048 atlas");
            int[][] cells={{2,1},{0,1},{1,0},{1,2},{1,1},{3,1}};for(int face=0;face<6;face++)check(java.util.Arrays.equals(image.getRGB(cells[face][0]*size,cells[face][1]*size,size,size,null,0,size),atlas.getRGB(face%3*size,face/3*size,size,size,null,0,size)),"Cross face "+face+" preserves every pixel and orientation");
            check(java.util.Arrays.equals(originalBytes,java.nio.file.Files.readAllBytes(crossFile)),"Import conversion leaves the user's source file untouched");
        }
        for(String id:java.util.List.of("nuketown_zombies","moon","origins","shangri_la","die_rise"))check(!MapAtmosphere.preset(id).getList("skyObjects",10).isEmpty(),id+" includes its 3D sky scene");
        var model=SkyObjects.importModel("{\"name\":\"Beacon\",\"boxes\":[{\"x\":0,\"sx\":0.5,\"sy\":1,\"sz\":0.5,\"r\":1,\"g\":0.3,\"b\":0.1}]}","beacon");check(model.getList("boxes",10).size()==1&&model.getString("name").equals("Beacon"),"Custom colored cuboid JSON imports without external assets");
        var skySettings=MapAtmosphere.preset("moon");skySettings.getList("skyObjects",10).add(model);d.configure(l,skySettings);var savedSky=MapAtmosphere.load(d.save(new CompoundTag())).settings();check(savedSky.getList("skyObjects",10).equals(skySettings.getList("skyObjects",10)),"Custom sky geometry survives world save/reload");
        var copy=d.createPreset("Moon with beacon",d.settings());check(d.resolvePreset(copy).getList("skyObjects",10).size()==2,"Custom presets retain all sky objects");
        MapAtmosphere.beginMatch(l);d.configure(l,MapAtmosphere.preset("origins"));MapAtmosphere.restoreMatch(l);check(d.settings().getList("skyObjects",10).equals(skySettings.getList("skyObjects",10)),"Match end restores the entire baseline sky scene");
        var overflow=new ListTag();for(int i=0;i<100;i++){var obj=SkyObjects.object("o"+i,"earth",0,30,16);obj.putDouble("size",Double.NaN);overflow.add(obj);}var sanitized=SkyObjects.sanitize(overflow);check(sanitized.size()==12&&sanitized.getCompound(0).getDouble("size")==16,"Scene object count and non-finite sizes are bounded");
        lua("local zr=ZombieroolAPI; return zr:configureSkyObject('lua_planet',{type='earth',yaw=60,elevation=35,size=20}) and #zr:getSkyObjects()==3", "Lua adds a sky object");
        lua("local zr=ZombieroolAPI; return zr:configureSkyObject('lua_planet',{spin=2}) and #zr:getSkyObjects()==3", "Lua updates a sky object without duplication");
        lua("local zr=ZombieroolAPI; return zr:removeSkyObject('lua_planet') and not zr:removeSkyObject('lua_planet')", "Lua removes sky objects deterministically");
        check(MapAtmosphere.preset("origins").getList("skyObjects",10).size()==1,"Origins uses one original 20-aircraft rig");
        check(MapAtmosphere.preset("die_rise").getList("skyObjects",10).stream().allMatch(t->((CompoundTag)t).getString("type").equals("meteor")),"Die Rise keeps the rocks in its original sky texture and adds only meteor FX");
        var legacy=MapAtmosphere.preset("origins");legacy.remove("skySceneVersion");var oldScene=new ListTag();oldScene.add(SkyObjects.object("squadron_0","dogfight",40,45,5));oldScene.add(SkyObjects.object("squadron_1","dogfight",-100,57,5));legacy.put("skyObjects",oldScene);check(MapAtmosphere.sanitize(legacy).getList("skyObjects",10).getCompound(0).getString("id").equals("air_battle"),"Previously saved built-in Origins prototype migrates to original rig");
        legacy.putString("presetId","custom_01234567890123456789012345678901");check(MapAtmosphere.sanitize(legacy).getList("skyObjects",10).size()==2,"Custom presets keep their authored object placement");
        legacy=MapAtmosphere.preset("moon");legacy.remove("skyObjects");check(MapAtmosphere.sanitize(legacy).getList("skyObjects",10).getCompound(0).getString("type").equals("earth"),"Older built-in presets receive their sky scene automatically");
        for(String id:MapAtmosphere.presetIds()){
            var protectedPreset=MapAtmosphere.preset(id);var request=MapAtmosphere.defaults();var removals=new ListTag();removals.add(StringTag.valueOf(id));request.put("_deletedPresets",removals);var before=d.save(new CompoundTag());
            check(!d.saveEditor(l,request)&&d.save(new CompoundTag()).equals(before)&&MapAtmosphere.preset(id).equals(protectedPreset),"Official preset deletion rejected without mutation: "+id);
        }
        var start=SkyObjects.meteorFlight(0,"meteor_0");var end=SkyObjects.meteorFlight(11.99f,"meteor_0");check(start.elevationOffset()>34&&end.elevationOffset()<-79,"Meteor falls from overhead to below the horizon");
        check(Math.abs(SkyObjects.meteorFlight(18,"meteor_0").azimuthOffset()-start.azimuthOffset())>80,"Successive meteor falls change their sky bearing");
        var cloud=MapAtmosphere.preset("nuketown_zombies").getList("skyObjects",10).getCompound(0);check(cloud.getDouble("size")/cloud.getDouble("distance")>.5,"Nuclear cloud is larger and closer");
        var battle=MapAtmosphere.preset("origins").getList("skyObjects",10).getCompound(0);check(battle.getDouble("elevation")>=80&&battle.getDouble("size")/battle.getDouble("distance")>=1,"Air battle occupies the sky overhead");
        var previous=MapAtmosphere.preset("origins");previous.putInt("skySceneVersion",2);var previousRows=new ListTag();var previousBattle=SkyObjects.object("air_battle","dogfight",0,55,45);previousBattle.putDouble("distance",180);previousRows.add(previousBattle);previous.put("skyObjects",previousRows);check(MapAtmosphere.sanitize(previous).getList("skyObjects",10).equals(MapAtmosphere.preset("origins").getList("skyObjects",10)),"Unchanged saved air battle upgrades to overhead placement");
        previousBattle.putDouble("size",60);check(MapAtmosphere.sanitize(previous).getList("skyObjects",10).getCompound(0).getDouble("size")==60,"Map-authored air battle placement remains intact");
        lua("local zr=ZombieroolAPI; zr:setMapAtmospherePreset('nuketown_zombies'); local objects=zr:getSkyObjects(); return #objects==1 and objects[1].type=='nuclear_cloud' and zr:configureSkyObject(objects[1].id,{enabled=false}) and not zr:getSkyObjects()[1].enabled", "Lua can disable the preset nuclear cloud");
        lua("local zr=ZombieroolAPI; local id=zr:getSkyObjects()[1].id; return zr:removeSkyObject(id) and #zr:getSkyObjects()==0", "Lua removes the nuclear cloud without changing the sky texture");
        var supportPos=new net.minecraft.core.BlockPos(110,80,110);l.setBlock(supportPos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        var gore=new me.cryo.zombierool.entity.GorePieceEntity(me.cryo.zombierool.init.ZombieroolModEntities.GORE_PIECE.get(),l);gore.setPos(110.5,81,110.5);
        var stuckField=me.cryo.zombierool.entity.GorePieceEntity.class.getDeclaredField("STUCK");stuckField.setAccessible(true);gore.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Boolean>)stuckField.get(null),true);
        gore.tick();gore.tick();check(gore.isStuck()&&gore.getDeltaMovement().equals(net.minecraft.world.phys.Vec3.ZERO),"Settled gore remains supported with cached static collision");
        l.setBlock(supportPos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);gore.tick();check(!gore.isStuck(),"Settled gore falls immediately when cached support is removed");
        System.out.println("ATMOSPHERE RUNTIME PASS: "+checks+" checks");
    }catch(Throwable t){t.printStackTrace();System.out.println("ATMOSPHERE RUNTIME FAILED");}finally{server.halt(false);}}
}

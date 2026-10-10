package local.hotfixtest;

import java.nio.file.*;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.scripting.LuaScriptManager;
import me.cryo.zombierool.hotfix.HotfixMatchConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

public final class MatchConfigRestartTest {
    static void check(boolean value,String message){HotfixRuntimeTest.check(value,message);}
    public static void run(ServerLevel level)throws Exception {
        WaveManager.endGame(level,null);
        var dir=level.getServer().getWorldPath(LevelResource.ROOT).resolve("zr_scripts");
        Files.deleteIfExists(dir.resolve("bossfight.lua"));
        Files.writeString(dir.resolve("main.lua"),"function OnGameStart() end");
        Files.writeString(dir.resolve("legacy_settings.lua"),"function OnCustomInteract(uuid,id) ZombieroolAPI:setCustomFog(0.9,0.8,0.7,2,6); ZombieroolAPI:setFog('none'); ZombieroolAPI:setMusicPreset('damned'); ZombieroolAPI:setSpawnIntensity('flood'); ZombieroolAPI:setSpecialWavesEnabled(false); ZombieroolAPI:setConfigFlag('superSprintersEnabled',true) end");
        var config=WorldConfig.get(level);
        config.setFogPreset("custom");config.setCustomFogR(.12f);config.setCustomFogG(.23f);config.setCustomFogB(.34f);config.setCustomFogNear(3);config.setCustomFogFar(27);config.setFogOutdoorsOnly(true);
        config.setMusicPreset("default");config.setSpawnIntensity("normal");config.setSpecialWavesEnabled(true);config.setSuperSprintersEnabled(false);
        var original=new CompoundTag();config.saveEditable(original);
        WaveManager.startGame(level);
        LuaScriptManager.callEvent("OnCustomInteract","","boss");
        check(config.getFogPreset().equals("none")&&config.getSpawnIntensity().equals("flood")&&!config.isSpecialWavesEnabled()&&WaveManager.currentSessionMusic.equals("damned"),"Legacy Lua file applies temporary encounter settings");
        var journal=level.getDataStorage().computeIfAbsent(HotfixMatchConfig::load,HotfixMatchConfig::new,"zr_hotfix_match_config");
        var persisted=journal.save(new CompoundTag());
        check(persisted.getCompound("original").equals(original),"Persistent match journal retains exact custom map baseline");
        WaveManager.restartLevel(level);
        var restored=new CompoundTag();config.saveEditable(restored);
        check(original.equals(restored),"Restart Level restores every original editable setting including custom fog");
        check(WaveManager.currentSessionMusic.equals("default"),"Restart Level restores original session music");
        LuaScriptManager.callEvent("OnCustomInteract","","boss");
        WaveManager.endGame(level,null);
        restored=new CompoundTag();config.saveEditable(restored);
        check(original.equals(restored),"Game Over also restores the original settings");
        check(!journal.save(new CompoundTag()).contains("original"),"Completed match consumes its snapshot so menu edits remain possible");
        // Simulate reopening a world after saved temporary settings and their journal.
        config.setFogPreset("none");config.setSpawnIntensity("chaos");
        var fromDisk=HotfixMatchConfig.load(persisted);
        check(fromDisk.save(new CompoundTag()).getCompound("original").equals(original),"Saved journal can recover its original settings after reload");
        level.getDataStorage().set("zr_hotfix_match_config",fromDisk);
        HotfixMatchConfig.begin(level);
        restored=new CompoundTag();config.saveEditable(restored);
        check(original.equals(restored),"Next match recovers persisted settings before taking its new snapshot");
        HotfixMatchConfig.restore(level);
        Files.deleteIfExists(dir.resolve("legacy_settings.lua"));
    }
}

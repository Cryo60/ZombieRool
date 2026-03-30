package me.cryo.zombierool.scripting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LuaScriptManager {

    private static Globals globals;
    private static ServerLevel currentLevel;

    private static final Map<String, String> dataStore = new ConcurrentHashMap<>();

    private static class LuaTimer {
        String id;
        int remainingTicks;
        LuaValue callback;

        LuaTimer(String id, int remainingTicks, LuaValue callback) {
            this.id = id;
            this.remainingTicks = remainingTicks;
            this.callback = callback;
        }
    }

    private static final Map<String, LuaTimer> activeTimers = new ConcurrentHashMap<>();

    public static void loadScripts(ServerLevel level) {
        currentLevel = level;
        dataStore.clear();
        activeTimers.clear();

        try {
            globals = JsePlatform.standardGlobals();
            globals.set("ZombieroolAPI", CoerceJavaToLua.coerce(new ZombieroolAPI(level)));

            File scriptDir = new File(level.getServer().getWorldPath(LevelResource.ROOT).toFile(), "zr_scripts");
            if (!scriptDir.exists()) {
                scriptDir.mkdirs();

                FileWriter fw = new FileWriter(new File(scriptDir, "main.lua"));
                fw.write("-- ZombieRool Main Lua Script\n");
                fw.write("-- Available Events:\n");
                fw.write("--   OnGameStart()\n");
                fw.write("--   OnWaveStart(wave)\n");
                fw.write("--   OnWaveEnd(wave)\n");
                fw.write("--   OnAllZombiesDead()\n");
                fw.write("--   OnZombieKill(playerUUID, mobType, isHeadshot)\n");
                fw.write("--   OnEntityKilled(playerUUID, entityId, isHeadshot) --> Avec l'UUID unique de l'entite\n");
                fw.write("--   OnBlockInteract(playerUUID, x, y, z)\n");
                fw.write("--   OnActionKeyPressed(playerUUID) --> Quand la touche F est pressee\n");
                fw.write("--   OnItemUsed(playerUUID, itemId)\n");
                fw.write("--   OnBlockShot(playerUUID, x, y, z)\n");
                fw.write("--   OnPowerActivated()\n");
                fw.write("--   OnPlayerDown(playerUUID)\n");
                fw.write("--   OnPlayerRevive(reviverUUID, revivedUUID)\n");
                fw.write("--   OnPlayerDeath(playerUUID)\n");
                fw.write("--   OnPlayerRespawn(playerUUID)\n");
                fw.write("--   OnBonusCollected(playerUUID, bonusId)\n");
                fw.write("--   OnPerkBought(playerUUID, perkId)\n");
                fw.write("--   OnMysteryBoxUsed(playerUUID, weaponId)\n");
                fw.write("--   OnLookTriggerActivated(playerUUID, triggerId)\n");
                fw.write("--   OnMeleeStrike(playerUUID, x, y, z)\n");
                fw.write("--   OnExplosion(playerUUID, x, y, z, radius)\n");
                fw.write("--   OnProjectileHit(playerUUID, x, y, z)\n\n");
                
                fw.write("-- Some API Functions:\n");
                fw.write("--   ZombieroolAPI:registerTimer(id, delayTicks, callback)\n");
                fw.write("--   ZombieroolAPI:cancelTimer(id)\n");
                fw.write("--   ZombieroolAPI:setData(key, value)\n");
                fw.write("--   ZombieroolAPI:getData(key) --> String\n");
                fw.write("--   ZombieroolAPI:getNearbyPlayers(x, y, z, radius) --> LuaTable (UUIDs)\n");
                fw.write("--   ZombieroolAPI:getEntityCount(entityType) --> int\n");
                fw.write("--   ZombieroolAPI:getPlayerHealth(uuid) --> float\n");
                fw.write("--   ZombieroolAPI:getPlayerMaxHealth(uuid) --> float\n");
                fw.write("--   ZombieroolAPI:isPlayerDown(uuid) --> boolean\n");
                fw.write("--   ZombieroolAPI:getPlayerYaw(uuid) --> float\n");
                fw.write("--   ZombieroolAPI:getPlayerPitch(uuid) --> float\n");
                fw.write("--   ZombieroolAPI:setPlayerGameMode(uuid, mode) --> mode: SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR\n");
                fw.write("--   ZombieroolAPI:setWave(wave)\n");
                fw.write("--   ZombieroolAPI:pauseWave()\n");
                fw.write("--   ZombieroolAPI:resumeWave()\n");
                fw.write("--   ZombieroolAPI:setMaxZombiesAlive(count)\n");
                fw.write("--   ZombieroolAPI:showTitle(uuid, title, subtitle, fadeIn, stay, fadeOut)\n");
                fw.write("--   ZombieroolAPI:showActionBar(uuid, message)\n");
                fw.write("--   ZombieroolAPI:setObjective(uuid, text) --> Creer une BossBar pour un objectif (text vide pour effacer)\n");
                fw.write("--   ZombieroolAPI:setGlowing(uuid, glowing) --> boolean\n");
                fw.write("--   ZombieroolAPI:playSound(x, y, z, soundId, volume, pitch)\n");
                fw.write("--   ZombieroolAPI:getBlockState(x, y, z) --> String (Ex: Block{minecraft:stone}[...])\n");
                fw.write("--   ZombieroolAPI:giveRandomWeapon(uuid, tier) --> tier (optional): WONDER, PAP, ASSAULT_RIFLE, ou un tag json.\n");
                fw.write("--   ZombieroolAPI:addPoints(uuid, amount)\n");
                fw.write("--   ZombieroolAPI:removePoints(uuid, amount)\n");
                fw.write("--   ZombieroolAPI:getScore(uuid)\n");
                fw.write("--   ZombieroolAPI:openObstacle(channel)\n");
                fw.write("--   ZombieroolAPI:giveWeapon(uuid, weaponId)\n");
                fw.write("--   ZombieroolAPI:givePerk(uuid, perkId)\n");
                fw.write("--   ZombieroolAPI:setMusicPreset(preset)\n");
                fw.write("--   ZombieroolAPI:setFog(preset)\n");
                fw.write("--   ZombieroolAPI:setCustomFog(r, g, b, near, far)\n");
                fw.write("--   ZombieroolAPI:playGlobalSound(soundId)\n");
                fw.write("--   ZombieroolAPI:playSoundForPlayer(uuid, soundId)\n");
                fw.write("--   ZombieroolAPI:playDynamicSoundForPlayer(uuid, soundId)\n");
                fw.write("--   ZombieroolAPI:giveItem(uuid, itemId, count)\n");
                fw.write("--   ZombieroolAPI:removeItem(uuid, itemId, count)\n");
                fw.write("--   ZombieroolAPI:hasItem(uuid, itemId, count)  --> boolean\n");
                fw.write("--   ZombieroolAPI:teleportAllPlayers(x, y, z)\n");
                fw.write("--   ZombieroolAPI:endGame(message)\n");
                fw.write("--   ZombieroolAPI:spawnBonus(bonusId, x, y, z)\n");
                fw.write("--   ZombieroolAPI:spawnBonusAtPlayer(playerUUID, bonusId)\n");
                fw.write("--   ZombieroolAPI:getRandomInt(min, max)  --> int\n");
                fw.write("--   ZombieroolAPI:applyEffect(uuid, effectId, durationTicks, amplifier)\n");
                fw.write("--   ZombieroolAPI:getActivePlayers()  --> LuaTable\n");
                fw.write("--   ZombieroolAPI:getInventory(uuid)  --> LuaTable\n");
                fw.write("--   ZombieroolAPI:getBlock(x, y, z)   --> String\n");
                fw.write("--   ZombieroolAPI:destroyBlock(x, y, z, playSound)\n");
                fw.write("--   ZombieroolAPI:fillBlocks(x1, y1, z1, x2, y2, z2, blockId)\n");
                fw.write("--   ZombieroolAPI:registerLookTrigger(id, x, y, z, radius, seconds, requireScope)\n");
                fw.write("--   ZombieroolAPI:removeLookTrigger(id)\n");
                fw.write("--   ZombieroolAPI:triggerScopeScreamer(playerUUID)\n");
                fw.write("--   ZombieroolAPI:killNearbyEntities(x, y, z, radius, entityType)\n");
                fw.write("--   ZombieroolAPI:setSpecialWavesEnabled(boolean)\n");
                fw.write("--   ZombieroolAPI:setSpawnIntensity(intensityString)\n");
                fw.write("--   ZombieroolAPI:spawnCrawler(x, y, z)\n");
                fw.write("--   ZombieroolAPI:makeZombieCrawler(uuid)\n");
                fw.write("--   ZombieroolAPI:spawnThrowable(ownerUuid, type, x, y, z, vx, vy, vz, fuseTicks) --> type: grenade, molotov, stielhandgranate, monkey_bomb\n");
                fw.write("--   ZombieroolAPI:giveBowieKnife(uuid)\n");
                fw.write("--   ZombieroolAPI:hasBowieKnife(uuid) --> boolean\n");
                fw.write("--   ZombieroolAPI:performMeleeAttack(uuid)\n");
                fw.write("--   ZombieroolAPI:triggerAmmoCrate(uuid)\n");
                fw.write("--   ZombieroolAPI:placePerkMachine(x, y, z, facing, perkId)\n");
                fw.write("--   ZombieroolAPI:placeMysteryBox(x, y, z, facing, active)\n");
                fw.write("--   ZombieroolAPI:placeWunderfizz(x, y, z, facing)\n");
                fw.write("--   ZombieroolAPI:placePowerSwitch(x, y, z, facing, powered)\n");
                fw.write("--   ZombieroolAPI:placeMeteorite(x, y, z, active)\n");
                fw.write("--   ZombieroolAPI:placeAmmoCrate(x, y, z, facing)\n");
                fw.write("--   ZombieroolAPI:getPackAPunchState(x, y, z) --> int (0=IDLE, 1=UPGRADING, 2=READY)\n");
                fw.write("--   ZombieroolAPI:insertPackAPunchWeapon(uuid, x, y, z, isFree) --> boolean\n");
                fw.write("--   ZombieroolAPI:collectPackAPunchWeapon(uuid, x, y, z) --> boolean\n\n");
                fw.write("function OnGameStart()\n");
                fw.write("    -- ZombieroolAPI:registerTimer('mon_timer', 100, function() ZombieroolAPI:broadcastMessage('5 secondes plus tard !') end)\n");
                fw.write("end\n\n");
                fw.write("function OnWaveStart(wave)\n");
                fw.write("    ZombieroolAPI:broadcastMessage('Wave ' .. wave .. ' started!')\n");
                fw.write("end\n\n");
                fw.close();
            }

            File[] scriptFiles = scriptDir.listFiles((dir, name) -> name.endsWith(".lua"));
            if (scriptFiles != null) {
                for (File script : scriptFiles) {
                    try {
                        globals.loadfile(script.getAbsolutePath()).call();
                        System.out.println("[ZombieRool Lua] Successfully loaded script: " + script.getName());
                    } catch (Throwable t) {
                        System.err.println("[ZombieRool Lua] Error loading " + script.getName() + ": " + t.getMessage());
                    }
                }
            }

        } catch (Throwable t) {
            System.err.println("[ZombieRool Lua] Error initializing Lua engine: " + t.getMessage());
        }
    }

    public static String executeString(String code) {
        if (globals == null) return "§cLua engine not initialized.";
        try {
            LuaValue chunk = globals.load(code);
            LuaValue result = chunk.call();
            return result.isnil() ? "§aExecution successful." : "§eResult: " + result.toString();
        } catch (Exception e) {
            return "§cError: " + e.getMessage();
        }
    }

    public static void callEvent(String eventName, Object... args) {
        if (globals == null) return;
        try {
            LuaValue func = globals.get(eventName);
            if (func.isfunction()) {
                LuaValue[] luaArgs = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    luaArgs[i] = CoerceJavaToLua.coerce(args[i]);
                }
                func.invoke(luaArgs);
            }
        } catch (Throwable t) {
            System.err.println("[ZombieRool Lua] Error executing event " + eventName + ": " + t.getMessage());
        }
    }

    public static void registerTimer(String id, int ticks, LuaValue callback) {
        activeTimers.put(id, new LuaTimer(id, ticks, callback));
    }

    public static void cancelTimer(String id) {
        activeTimers.remove(id);
    }

    public static void setData(String key, String value) {
        dataStore.put(key, value);
    }

    public static String getData(String key) {
        return dataStore.getOrDefault(key, "");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!activeTimers.isEmpty()) {
            Iterator<Map.Entry<String, LuaTimer>> iterator = activeTimers.entrySet().iterator();
            while (iterator.hasNext()) {
                LuaTimer timer = iterator.next().getValue();
                timer.remainingTicks--;

                if (timer.remainingTicks <= 0) {
                    iterator.remove();
                    try {
                        if (timer.callback.isfunction()) {
                            timer.callback.call();
                        }
                    } catch (Throwable t) {
                        System.err.println("[ZombieRool Lua] Error executing timer " + timer.id + ": " + t.getMessage());
                    }
                }
            }
        }
    }
}
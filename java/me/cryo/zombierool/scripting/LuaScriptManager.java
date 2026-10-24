package me.cryo.zombierool.scripting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;



import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LuaScriptManager {
    private static Globals globals;
    private static LuaSandbox.Budget budget;
    private static final ThreadLocal<java.util.Set<String>> callbacks=ThreadLocal.withInitial(java.util.HashSet::new);
    private static ServerLevel currentLevel;
    private static final ThreadLocal<java.util.UUID> eventPlayer=new ThreadLocal<>();

    private static final Map<String, String> dataStore = new ConcurrentHashMap<>();

    private static class LuaTimer {
        String id;
        int remainingTicks;
        LuaValue callback;
        java.util.UUID player;

        LuaTimer(String id, int remainingTicks, LuaValue callback) {
            this.id = id;
            this.remainingTicks = remainingTicks;
            this.callback = callback;
            this.player = eventPlayer.get();
        }
    }

    private static final Map<String, LuaTimer> activeTimers = new ConcurrentHashMap<>();

    public static void loadScripts(ServerLevel level) {
        currentLevel = level;
        dataStore.clear();
        activeTimers.clear();

        try {
            globals = LuaSandbox.create();budget=(LuaSandbox.Budget)globals.debuglib;budget.reset();
            globals.set("print",new org.luaj.vm2.lib.VarArgFunction(){public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args){StringBuilder line=new StringBuilder();for(int i=1;i<=args.narg();i++){if(i>1)line.append("  ");line.append(args.arg(i).tojstring());}log(line.toString());return LuaValue.NONE;}});
            globals.set("ZombieroolAPI", SafeLuaBindings.wrap(new ZombieroolAPI(level)));

            File scriptDir = me.cryo.zombierool.util.WorldZombieroolPaths.getScriptsDir(level);
            if (!scriptDir.exists()&&me.cryo.zombierool.util.WorldZombieroolPaths.scripts(level).length==0) {
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
                fw.write("--   OnMachineDeliveryStarted/Landed/Failed/Cancelled(cx, cy, cz, x, y, z, machine, mode, error)\n");
                fw.write("--   OnMeleeStrike(playerUUID, x, y, z)\n");
                fw.write("--   OnExplosion(playerUUID, x, y, z, radius)\n");
                fw.write("--   OnProjectileHit(playerUUID, x, y, z)\n");
                fw.write("--   OnBarrierSpotted(playerUUID, kind, x, y, z) --> kind: death ou damage, debut du compteur\n");
                fw.write("--   OnBarrierExecute(playerUUID, kind, x, y, z) --> le compteur tombe a zero\n");
                fw.write("--   OnBarrierMiss(playerUUID, kind, x, y, z) --> sorti dans la derniere demi-seconde\n\n");

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
                fw.write("--   ZombieroolAPI:playGlobalSound(soundId, volume, pitch)\n");
                fw.write("--   ZombieroolAPI:playSoundForPlayer(uuid, soundId, volume, pitch)\n");
                fw.write("--   ZombieroolAPI:playDynamicSoundForPlayer(uuid, soundId, volume, pitch)\n");
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
                fw.write("--   ZombieroolAPI:listMachineDeliveries(), listDeliveryMachines(), getMachineDelivery(x,y,z)\n");
                fw.write("--   ZombieroolAPI:configureMachineDelivery(x,y,z,settings), triggerMachineDelivery(x,y,z)\n");
                fw.write("--   ZombieroolAPI:deliverMachineAt(x,y,z,machine,tx,ty,tz), cancelMachineDelivery(x,y,z)\n");
                fw.write("--   ZombieroolAPI:resetMachineDelivery(x,y,z,wholeGroup), setMachineDeliveryEnabled(x,y,z,enabled)\n");
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

            File[] scriptFiles = me.cryo.zombierool.util.WorldZombieroolPaths.scripts(level);
            if (scriptFiles != null) {
                for (File script : scriptFiles) {
                    try {
                        budget.reset();me.cryo.zombierool.gameplay.CompatibilityFixes.loadScript(globals,script.getAbsolutePath()).call();
                        log("Loaded: "+script.getName());System.out.println("[ZombieRool Lua] Successfully loaded script: " + script.getName());
                    } catch (Throwable t) {
                        log(script.getName()+": "+t.getMessage());System.err.println("[ZombieRool Lua] Error loading " + script.getName() + ": " + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[ZombieRool Lua] Error initializing Lua engine: " + t.getMessage());
        }
    }

    private static final java.util.ArrayDeque<String> CONSOLE=new java.util.ArrayDeque<>();
    public static synchronized void log(String text){while(CONSOLE.size()>=120)CONSOLE.removeFirst();CONSOLE.addLast(text.length()>1000?text.substring(0,1000):text);}
    public static synchronized java.util.List<String> console(){return java.util.List.copyOf(CONSOLE);}
    public static String validate(String code){try{LuaSandbox.create().load(code,"editor");return "OK";}catch(Throwable e){return e.getMessage();}}
    private static LuaValue eventValue(Object value){
        if(value==null)return LuaValue.NIL;
        if(value instanceof Boolean b)return LuaValue.valueOf(b);
        if(value instanceof Number n)return LuaValue.valueOf(n.doubleValue());
        if(value instanceof String s)return LuaValue.valueOf(s);
        throw new IllegalArgumentException("Unsupported Lua event value: "+value.getClass().getSimpleName());
    }
    private static java.util.UUID eventParticipant(Object... args){if(currentLevel==null)return null;for(var arg:args)if(arg instanceof String id){try{var uuid=java.util.UUID.fromString(id);if(currentLevel.getServer().getPlayerList().getPlayer(uuid)!=null)return uuid;}catch(IllegalArgumentException ignored){}}return null;}
    private static boolean playerEventAllowed(String event,Object... args){if(currentLevel==null||event.equals("OnPlayerDeath")||event.equals("OnPlayerRespawn"))return true;for(var arg:args)if(arg instanceof String id){try{var p=currentLevel.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(id));if(p!=null&&(p.isCreative()||p.isSpectator()))return false;}catch(IllegalArgumentException ignored){}}return true;}
    public static org.luaj.vm2.LuaValue eventResult(String eventName,Object... args){
        if(globals==null||!playerEventAllowed(eventName,args))return org.luaj.vm2.LuaValue.NIL;
        if(!callbacks.get().add(eventName))return LuaValue.NIL;
        var previousPlayer=eventPlayer.get();var participant=eventParticipant(args);if(participant!=null)eventPlayer.set(participant);
        try{if(callbacks.get().size()==1)budget.reset();var f=globals.get(eventName);if(!f.isfunction())return org.luaj.vm2.LuaValue.NIL;var values=new org.luaj.vm2.LuaValue[args.length];for(int i=0;i<args.length;i++)values[i]=eventValue(args[i]);return f.invoke(values).arg1();}catch(Throwable e){log(eventName+": "+e.getMessage());return org.luaj.vm2.LuaValue.NIL;}finally{callbacks.get().remove(eventName);if(previousPlayer==null)eventPlayer.remove();else eventPlayer.set(previousPlayer);}
    }
    public static boolean gameplayCallAllowed(ServerLevel level){if(!me.cryo.zombierool.gameplay.WaveManager.isGameRunning()||me.cryo.zombierool.gameplay.WaveManager.isPausedByPlayer())return false;var owner=eventPlayer.get();if(owner!=null){var p=level.getServer().getPlayerList().getPlayer(owner);return p!=null&&p.level()==level&&me.cryo.zombierool.gameplay.MapActorBehavior.eligible(p);}return level.players().stream().anyMatch(me.cryo.zombierool.gameplay.MapActorBehavior::eligible);}
    public static boolean ready(ServerLevel level){return globals!=null&&currentLevel==level;}
    public static String executeString(String code) {
        if (globals == null) return "§cLua engine not initialized.";
        try {
            budget.reset();LuaValue chunk = globals.load(code);
            LuaValue result = chunk.call();
            return result.isnil() ? "§aExecution successful." : "§eResult: " + result.toString();
        } catch (Throwable e) {
            return "§cError: " + e.getMessage();
        }
    }

    public static boolean actorCallback(String name,String uuid) {
        if(globals==null)return true;
        if(!callbacks.get().add(name))return true;
        try {if(callbacks.get().size()==1)budget.reset();var function=globals.get(name);if(function.isfunction())function.call(LuaValue.valueOf(uuid));return true;}
        catch(Throwable error){log(name+": "+error.getMessage());return false;}
        finally{callbacks.get().remove(name);}
    }
    public static void callEvent(String eventName, Object... args) {eventResult(eventName,args);}

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
            List<LuaTimer> toExecute = new ArrayList<>();
            Iterator<Map.Entry<String, LuaTimer>> iterator = activeTimers.entrySet().iterator();
            while (iterator.hasNext()) {
                LuaTimer timer = iterator.next().getValue();
                if(timer.player!=null){var player=currentLevel==null?null:currentLevel.getServer().getPlayerList().getPlayer(timer.player);if(player==null||!me.cryo.zombierool.gameplay.WaveManager.isGameRunning()){iterator.remove();continue;}if(!me.cryo.zombierool.gameplay.MapActorBehavior.eligible(player)||me.cryo.zombierool.gameplay.WaveManager.isPausedByPlayer())continue;}
                if(currentLevel!=null&&(me.cryo.zombierool.gameplay.WaveManager.isPausedByPlayer()||currentLevel.players().stream().noneMatch(me.cryo.zombierool.gameplay.MapActorBehavior::eligible)))continue;
                timer.remainingTicks--;
                if (timer.remainingTicks <= 0) {
                    iterator.remove();
                    toExecute.add(timer);
                }
            }

            for (LuaTimer timer : toExecute) {
                // An earlier due callback can end the match or change a player's mode.
                // Validate again at invocation time, not just while building the due queue.
                if(timer.player!=null){var owner=currentLevel==null?null:currentLevel.getServer().getPlayerList().getPlayer(timer.player);if(owner==null||!me.cryo.zombierool.gameplay.WaveManager.isGameRunning())continue;if(!me.cryo.zombierool.gameplay.MapActorBehavior.eligible(owner)){timer.remainingTicks=1;activeTimers.putIfAbsent(timer.id,timer);continue;}}
                if(currentLevel!=null&&(me.cryo.zombierool.gameplay.WaveManager.isPausedByPlayer()||currentLevel.players().stream().noneMatch(me.cryo.zombierool.gameplay.MapActorBehavior::eligible))){timer.remainingTicks=1;activeTimers.putIfAbsent(timer.id,timer);continue;}
                var previousPlayer=eventPlayer.get();if(timer.player!=null)eventPlayer.set(timer.player);
                try {
                    if (timer.callback.isfunction()) {
                        budget.reset();timer.callback.call();
                    }
                } catch (Throwable t) {
                    System.err.println("[ZombieRool Lua] Error executing timer " + timer.id + ": " + t.getMessage());
                } finally {if(previousPlayer==null)eventPlayer.remove();else eventPlayer.set(previousPlayer);}
            }
        }
    }
}

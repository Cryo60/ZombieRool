package me.cryo.zombierool.scripting;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

/** Explicit gameplay bindings. Never expose Java objects or reflection to map scripts. */
public final class SafeLuaBindings {
    private SafeLuaBindings() {}
    public static LuaTable wrap(ZombieroolAPI api) {
        LuaTable table = new LuaTable();
        table.set("isGameRunning", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isGameRunning());
            }
        });
        table.set("isGamePaused", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isGamePaused());
            }
        });
        table.set("getMapActorProfiles", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMapActorProfiles();
            }
        });
        table.set("defineMapActor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.defineMapActor(args.arg(1).optjstring(null), args.arg(2).checktable()));
            }
        });
        table.set("spawnMapActor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnMapActor(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)));
            }
        });
        table.set("getMusicVolumes", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMusicVolumes();
            }
        });
        table.set("getMusicVolume", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMusicVolume(args.arg(1).optjstring(null));
            }
        });
        table.set("toggleMusicVolume", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.toggleMusicVolume(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("isMusicVolumeActive", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isMusicVolumeActive(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("setMusicVolumeEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setMusicVolumeEnabled(args.arg(1).optjstring(null), args.arg(2).optboolean(false)));
            }
        });
        table.set("resetPlayerMusic", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.resetPlayerMusic(args.arg(1).optjstring(null)));
            }
        });
        table.set("spawnMapActorNearPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnMapActorNearPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optdouble(0), args.arg(4).optdouble(0)));
            }
        });
        table.set("spawnEnemySpawnerNearPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnEnemySpawnerNearPlayer(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).optdouble(0), args.arg(6).optdouble(0)));
            }
        });
        table.set("getMapActor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMapActor(args.arg(1).optjstring(null));
            }
        });
        table.set("moveMapActor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.moveMapActor(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0)));
            }
        });
        table.set("despawnMapActor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.despawnMapActor(args.arg(1).optjstring(null)));
            }
        });
        table.set("setMapActorTarget", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setMapActorTarget(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("mapActorAttack", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.mapActorAttack(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optjstring(null), args.arg(5).optint(0)));
            }
        });
        table.set("getMapActorData", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMapActorData(args.arg(1).optjstring(null));
            }
        });
        table.set("setMapActorData", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setMapActorData(args.arg(1).optjstring(null), args.arg(2).checktable()));
            }
        });
        table.set("getMapActorTime", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getMapActorTime());
            }
        });
        table.set("playMapActorAnimation", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.playMapActorAnimation(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("mapActorEffect", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.mapActorEffect(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optjstring(null), args.arg(4).optint(0), args.arg(5).optint(0)));
            }
        });
        table.set("setMapActorCasting", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setMapActorCasting(args.arg(1).optjstring(null), args.arg(2).optint(0)));
            }
        });
        table.set("healMapActor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.healMapActor(args.arg(1).optjstring(null), args.arg(2).optdouble(0)));
            }
        });
        table.set("shootMapActor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.shootMapActor(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optjstring(null), args.arg(4).optdouble(0), args.arg(5).optdouble(0)));
            }
        });
        table.set("buyMapArmor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.buyMapArmor(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("equipMapArmor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.equipMapArmor(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("hasWardenKey", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.hasWardenKey());
            }
        });
        table.set("disableTurbines", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.disableTurbines(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("givePortableEquipment", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.givePortableEquipment(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("getPortableEquipment", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getPortableEquipment(args.arg(1).optjstring(null));
            }
        });
        table.set("playVoice", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.playVoice(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("playVoiceEvent", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.playVoiceEvent(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("setPlayerVoicePreset", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setPlayerVoicePreset(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("getEnemySkinPresets", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getEnemySkinPresets();
            }
        });
        table.set("getEntitySkin", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getEntitySkin(args.arg(1).optjstring(null)));
            }
        });
        table.set("getEntityEyeSkin", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getEntityEyeSkin(args.arg(1).optjstring(null)));
            }
        });
        table.set("getEnemyAppearances", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getEnemyAppearances();
            }
        });
        table.set("getCustomEnemySkins", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getCustomEnemySkins(args.arg(1).optjstring(null));
            }
        });
        table.set("setEntitySkin", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setEntitySkin(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("setEntityEyeSkin", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setEntityEyeSkin(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("setEnemyAppearance", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setEnemyAppearance(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optint(0)));
            }
        });
        table.set("setEnemySkinPreset", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setEnemySkinPreset(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optboolean(false)));
            }
        });
        table.set("configureLunarEnvironment", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.configureLunarEnvironment(args.arg(1).optdouble(0), args.arg(2).optboolean(false), args.arg(3).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("getMapAtmosphere", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMapAtmosphere();
            }
        });
        table.set("configureMapAtmosphere", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.configureMapAtmosphere(args.arg(1).checktable()); return LuaValue.NONE;
            }
        });
        table.set("setMapAtmosphereEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setMapAtmosphereEnabled(args.arg(1).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("setMapAtmospherePreset", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setMapAtmospherePreset(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("saveMapAtmospherePreset", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.saveMapAtmospherePreset(args.arg(1).optjstring(null)));
            }
        });
        table.set("getMapAtmospherePresets", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMapAtmospherePresets();
            }
        });
        table.set("getSkyObjects", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getSkyObjects();
            }
        });
        table.set("configureSkyObject", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureSkyObject(args.arg(1).optjstring(null), args.arg(2).checktable()));
            }
        });
        table.set("removeSkyObject", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.removeSkyObject(args.arg(1).optjstring(null)));
            }
        });
        table.set("importSkyObject", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.importSkyObject(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("getAmbientSoundLibrary", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getAmbientSoundLibrary();
            }
        });
        table.set("getMapSkyboxes", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMapSkyboxes();
            }
        });
        table.set("setSkybox", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setSkybox(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optdouble(0), args.arg(4).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("configureAmbientSource", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureAmbientSource(args.arg(1).optjstring(null), args.arg(2).checktable()));
            }
        });
        table.set("removeAmbientSource", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.removeAmbientSource(args.arg(1).optjstring(null)));
            }
        });
        table.set("triggerAmbientSound", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.triggerAmbientSound(args.arg(1).optjstring(null)));
            }
        });
        table.set("getAmbientSources", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getAmbientSources();
            }
        });
        table.set("setAmbientVolume", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setAmbientVolume(args.arg(1).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("getAmbientSource", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getAmbientSource(args.arg(1).optjstring(null));
            }
        });
        table.set("setAmbientSourceEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setAmbientSourceEnabled(args.arg(1).optjstring(null), args.arg(2).optboolean(false)));
            }
        });
        table.set("setColorGrading", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setColorGrading(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("setScreenEffects", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setScreenEffects(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("configurePlayerAtmosphere", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configurePlayerAtmosphere(args.arg(1).optjstring(null), args.arg(2).checktable()));
            }
        });
        table.set("setPlayerAtmospherePreset", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setPlayerAtmospherePreset(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("resetPlayerAtmosphere", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.resetPlayerAtmosphere(args.arg(1).optjstring(null)));
            }
        });
        table.set("getLunarEnvironment", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getLunarEnvironment();
            }
        });
        table.set("setZombieSwimming", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setZombieSwimming(args.arg(1).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("configureIntroPhase", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.configureIntroPhase(args.arg(1).optboolean(false), args.arg(2).optint(0), args.arg(3).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("configureIntroPhaseSettings", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.configureIntroPhaseSettings(args.arg(1).checktable()); return LuaValue.NONE;
            }
        });
        table.set("getIntroPhaseState", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getIntroPhaseState();
            }
        });
        table.set("startIntroPhase", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.startIntroPhase());
            }
        });
        table.set("finishIntroPhase", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.finishIntroPhase());
            }
        });
        table.set("isIntroPhase", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isIntroPhase());
            }
        });
        table.set("getIntroElapsedSeconds", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getIntroElapsedSeconds());
            }
        });
        table.set("getZombieSwimming", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getZombieSwimming());
            }
        });
        table.set("setAtmosphereZone", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setAtmosphereZone(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0), args.arg(6).optdouble(0), args.arg(7).optdouble(0), args.arg(8).optdouble(0), args.arg(9).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("getAtmosphereZone", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getAtmosphereZone(args.arg(1).optjstring(null));
            }
        });
        table.set("configureAtmosphereZone", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureAtmosphereZone(args.arg(1).optjstring(null), args.arg(2).checktable()));
            }
        });
        table.set("removeAtmosphereZone", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.removeAtmosphereZone(args.arg(1).optjstring(null)));
            }
        });
        table.set("getAtmosphereZones", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getAtmosphereZones();
            }
        });
        table.set("giveLunarHelmet", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.giveLunarHelmet(args.arg(1).optjstring(null)));
            }
        });
        table.set("giveEnvironmentalSuit", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.giveEnvironmentalSuit(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("removeLunarHelmet", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.removeLunarHelmet(args.arg(1).optjstring(null)));
            }
        });
        table.set("setLunarHelmetEquipped", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setLunarHelmetEquipped(args.arg(1).optjstring(null), args.arg(2).optboolean(false)));
            }
        });
        table.set("getLunarHelmetState", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getLunarHelmetState(args.arg(1).optjstring(null));
            }
        });
        table.set("registerInteractable", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.registerInteractable(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0), args.arg(6).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("removeInteractable", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.removeInteractable(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("registerTimer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.registerTimer(args.arg(1).optjstring(null), args.arg(2).optint(0), args.arg(3)); return LuaValue.NONE;
            }
        });
        table.set("cancelTimer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.cancelTimer(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("setData", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setData(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("getData", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getData(args.arg(1).optjstring(null)));
            }
        });
        table.set("getNearbyPlayers", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getNearbyPlayers(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0));
            }
        });
        table.set("getEntityCount", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getEntityCount(args.arg(1).optjstring(null)));
            }
        });
        table.set("getPlayerHealth", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPlayerHealth(args.arg(1).optjstring(null)));
            }
        });
        table.set("playKnuckleAnimation", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.playKnuckleAnimation(args.arg(1).optjstring(null)));
            }
        });
        table.set("getBlockPowerMode", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getBlockPowerMode(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("setBlockPowerMode", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setBlockPowerMode(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null)));
            }
        });
        table.set("getPlayerMaxHealth", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPlayerMaxHealth(args.arg(1).optjstring(null)));
            }
        });
        table.set("isPlayerDown", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isPlayerDown(args.arg(1).optjstring(null)));
            }
        });
        table.set("isPlayerCrawling", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isPlayerCrawling(args.arg(1).optjstring(null)));
            }
        });
        table.set("getPlayerYaw", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPlayerYaw(args.arg(1).optjstring(null)));
            }
        });
        table.set("getPlayerPitch", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPlayerPitch(args.arg(1).optjstring(null)));
            }
        });
        table.set("getBlockState", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getBlockState(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("setPlayerGameMode", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setPlayerGameMode(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("setWave", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setWave(args.arg(1).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("pauseWave", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.pauseWave(); return LuaValue.NONE;
            }
        });
        table.set("resumeWave", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.resumeWave(); return LuaValue.NONE;
            }
        });
        table.set("setMaxZombiesAlive", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setMaxZombiesAlive(args.arg(1).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("showTitle", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.showTitle(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optjstring(null), args.arg(4).optint(0), args.arg(5).optint(0), args.arg(6).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("showActionBar", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.showActionBar(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("playSound", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                if (args.narg() == 4) { api.playSound(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optjstring(null)); return LuaValue.NONE; }
                if (args.narg() == 5) { api.playSound(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optjstring(null), (float)args.arg(5).optdouble(0)); return LuaValue.NONE; }
                if (args.narg() == 6) { api.playSound(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optjstring(null), (float)args.arg(5).optdouble(0), (float)args.arg(6).optdouble(0)); return LuaValue.NONE; }
                throw new LuaError("Invalid argument count for playSound");
            }
        });
        table.set("setGlowing", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setGlowing(args.arg(1).optjstring(null), args.arg(2).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("setObjective", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setObjective(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("giveRandomWeapon", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.giveRandomWeapon(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("addPoints", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.addPoints(args.arg(1).optjstring(null), args.arg(2).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("removePoints", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.removePoints(args.arg(1).optjstring(null), args.arg(2).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("getScore", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getScore(args.arg(1).optjstring(null)));
            }
        });
        table.set("spawnBoss", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnBoss(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("spawnMob", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnMob(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("spawnCustomBoss", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnCustomBoss(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optjstring(null), (float)args.arg(5).optdouble(0), (float)args.arg(6).optdouble(0), (float)args.arg(7).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("giveWeapon", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.giveWeapon(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("packAPunchWeapon", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.packAPunchWeapon(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("givePerk", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.givePerk(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("unlockChannel", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.unlockChannel(args.arg(1).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("lockChannel", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.lockChannel(args.arg(1).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("clearUnlockedChannels", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.clearUnlockedChannels(); return LuaValue.NONE;
            }
        });
        table.set("clearUnlockedZones", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.clearUnlockedZones(); return LuaValue.NONE;
            }
        });
        table.set("setSpawnIntensity", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setSpawnIntensity(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("setSpecialWavesEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setSpecialWavesEnabled(args.arg(1).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("openObstacle", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.openObstacle(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("playGlobalSound", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                if (args.narg() == 1) { api.playGlobalSound(args.arg(1).optjstring(null)); return LuaValue.NONE; }
                if (args.narg() == 2) { api.playGlobalSound(args.arg(1).optjstring(null), (float)args.arg(2).optdouble(0)); return LuaValue.NONE; }
                if (args.narg() == 3) { api.playGlobalSound(args.arg(1).optjstring(null), (float)args.arg(2).optdouble(0), (float)args.arg(3).optdouble(0)); return LuaValue.NONE; }
                throw new LuaError("Invalid argument count for playGlobalSound");
            }
        });
        table.set("playSoundForPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                if (args.narg() == 2) { api.playSoundForPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE; }
                if (args.narg() == 3) { api.playSoundForPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null), (float)args.arg(3).optdouble(0)); return LuaValue.NONE; }
                if (args.narg() == 4) { api.playSoundForPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null), (float)args.arg(3).optdouble(0), (float)args.arg(4).optdouble(0)); return LuaValue.NONE; }
                throw new LuaError("Invalid argument count for playSoundForPlayer");
            }
        });
        table.set("playDynamicSoundForPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                if (args.narg() == 2) { api.playDynamicSoundForPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE; }
                if (args.narg() == 3) { api.playDynamicSoundForPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null), (float)args.arg(3).optdouble(0)); return LuaValue.NONE; }
                if (args.narg() == 4) { api.playDynamicSoundForPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null), (float)args.arg(3).optdouble(0), (float)args.arg(4).optdouble(0)); return LuaValue.NONE; }
                throw new LuaError("Invalid argument count for playDynamicSoundForPlayer");
            }
        });
        table.set("setMusicPreset", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setMusicPreset(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("setFog", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setFog(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("setCustomFog", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setCustomFog(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("setConfigFlag", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setConfigFlag(args.arg(1).optjstring(null), args.arg(2).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("setConfigString", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setConfigString(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("forceWunderfizz", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.forceWunderfizz(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("forceMysteryBox", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.forceMysteryBox(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("excludeMysteryBox", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.excludeMysteryBox(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("excludeWunderfizz", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.excludeWunderfizz(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("setAmmoCratePrice", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setAmmoCratePrice(args.arg(1).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("resetPlayerPerkPurchases", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.resetPlayerPerkPurchases(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("teleportPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.teleportPlayer(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("spawnParticle", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnParticle(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("spawnParticleAdv", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnParticleAdv(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optint(0), args.arg(6).optdouble(0), args.arg(7).optdouble(0), args.arg(8).optdouble(0), args.arg(9).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("broadcastMessage", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.broadcastMessage(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("printConsole", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.printConsole(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("setBlock", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setBlock(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("getBlock", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getBlock(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("destroyBlock", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.destroyBlock(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("fillBlocks", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.fillBlocks(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optint(0), args.arg(5).optint(0), args.arg(6).optint(0), args.arg(7).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("registerLookTrigger", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.registerLookTrigger(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0), args.arg(6).optdouble(0), args.arg(7).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("restoreEntityTemplate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.restoreEntityTemplate(args.arg(1).optjstring(null)));
            }
        });
        table.set("spawnLookTarget", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnLookTarget(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optjstring(null)));
            }
        });
        table.set("removeLookTrigger", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.removeLookTrigger(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("getPlayerName", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPlayerName(args.arg(1).optjstring(null)));
            }
        });
        table.set("giveItem", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.giveItem(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("removeItem", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.removeItem(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("hasItem", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.hasItem(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optint(0)));
            }
        });
        table.set("getInventory", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getInventory(args.arg(1).optjstring(null));
            }
        });
        table.set("teleportAllPlayers", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.teleportAllPlayers(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("endGame", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.endGame(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("spawnBonus", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnBonus(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("spawnBonusAtPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnBonusAtPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("getRandomInt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getRandomInt(args.arg(1).optint(0), args.arg(2).optint(0)));
            }
        });
        table.set("applyEffect", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.applyEffect(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optint(0), args.arg(4).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("getActivePlayers", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getActivePlayers();
            }
        });
        table.set("triggerElderGuardianOverlay", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.triggerElderGuardianOverlay(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("triggerScopeScreamer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.triggerScopeScreamer(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("killNearbyEntities", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.killNearbyEntities(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("getPlayerX", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPlayerX(args.arg(1).optjstring(null)));
            }
        });
        table.set("getPlayerY", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPlayerY(args.arg(1).optjstring(null)));
            }
        });
        table.set("getPlayerZ", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPlayerZ(args.arg(1).optjstring(null)));
            }
        });
        table.set("isPlayerInRadius", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isPlayerInRadius(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0)));
            }
        });
        table.set("getZombiesInRadius", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getZombiesInRadius(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)));
            }
        });
        table.set("sendMessageToPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.sendMessageToPlayer(args.arg(1).optjstring(null), args.arg(2).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("damagePlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.damagePlayer(args.arg(1).optjstring(null), (float)args.arg(2).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("healPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.healPlayer(args.arg(1).optjstring(null), (float)args.arg(2).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("hasPerk", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.hasPerk(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("refillAmmo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.refillAmmo(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("getRemainingEnemies", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getRemainingEnemies());
            }
        });
        table.set("getCurrentWave", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getCurrentWave());
            }
        });
        table.set("isPowerActivated", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isPowerActivated());
            }
        });
        table.set("killAllZombies", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.killAllZombies(); return LuaValue.NONE;
            }
        });
        table.set("strikeLightning", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.strikeLightning(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("createExplosion", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.createExplosion(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), (float)args.arg(4).optdouble(0), (float)args.arg(5).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("setWeather", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setWeather(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("spawnCrawler", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnCrawler(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0)); return LuaValue.NONE;
            }
        });
        table.set("makeZombieCrawler", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.makeZombieCrawler(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("spawnThrowable", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.spawnThrowable(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0), args.arg(6).optdouble(0), args.arg(7).optdouble(0), args.arg(8).optdouble(0), args.arg(9).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("giveBowieKnife", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.giveBowieKnife(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("hasBowieKnife", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.hasBowieKnife(args.arg(1).optjstring(null)));
            }
        });
        table.set("performMeleeAttack", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.performMeleeAttack(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("triggerAmmoCrate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.triggerAmmoCrate(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("placePerkMachine", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.placePerkMachine(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("placeMysteryBox", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.placeMysteryBox(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("placeWunderfizz", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.placeWunderfizz(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("placePowerSwitch", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.placePowerSwitch(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("defineBuildable", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.defineBuildable(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optjstring(null), args.arg(4).optjstring(null), args.arg(5).optjstring(null), args.arg(6).optint(0), args.arg(7).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("bindBuildablePart", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.bindBuildablePart(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optint(0), args.arg(4).optint(0), args.arg(5).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("bindBuildableBench", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.bindBuildableBench(args.arg(1).optjstring(null), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("bindBuildableItemPart", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.bindBuildableItemPart(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optint(0), args.arg(4).optint(0), args.arg(5).optint(0), args.arg(6).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("bindBuildableDestination", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.bindBuildableDestination(args.arg(1).optjstring(null), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("getCarriedBuildableResult", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getCarriedBuildableResult(args.arg(1).optjstring(null)));
            }
        });
        table.set("removeBuildable", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.removeBuildable(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("unbindBuildable", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.unbindBuildable(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("getBuildableInstalledCount", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getBuildableInstalledCount(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("isBuildableComplete", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isBuildableComplete(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("getCarriedBuildablePart", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getCarriedBuildablePart(args.arg(1).optjstring(null)));
            }
        });
        table.set("dropBuildablePart", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.dropBuildablePart(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("placeMeteorite", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.placeMeteorite(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("placeAmmoCrate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.placeAmmoCrate(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("getPackAPunchState", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getPackAPunchState(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("insertPackAPunchWeapon", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.insertPackAPunchWeapon(args.arg(1).optjstring(null), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optint(0), args.arg(5).optboolean(false)));
            }
        });
        table.set("collectPackAPunchWeapon", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.collectPackAPunchWeapon(args.arg(1).optjstring(null), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optint(0)));
            }
        });
        table.set("placeGlassDefenseDoor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.placeGlassDefenseDoor(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null)));
            }
        });
        table.set("getGlassDefenseDoor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getGlassDefenseDoor(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0));
            }
        });
        table.set("damageGlassDefenseDoor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.damageGlassDefenseDoor(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("repairGlassDefenseDoor", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.repairGlassDefenseDoor(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optint(0)));
            }
        });
        table.set("placeMapDevice", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.placeMapDevice(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).optjstring(null)));
            }
        });
        table.set("getSpecialEnemiesConfig", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getSpecialEnemiesConfig();
            }
        });
        table.set("setMiniBossLootItem", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setMiniBossLootItem(args.arg(1).optboolean(false), args.arg(2).optjstring(null), args.arg(3).optint(0), args.arg(4).optjstring(null)));
            }
        });
        table.set("getAddonMapConfig", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getAddonMapConfig();
            }
        });
        table.set("configureSpecialEnemies", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.configureSpecialEnemies(args.arg(1).checktable()); return LuaValue.NONE;
            }
        });
        table.set("getMiniBossType", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getMiniBossType());
            }
        });
        table.set("despawnEncounter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.despawnEncounter(args.arg(1).optjstring(null)));
            }
        });
        table.set("setRomeroEnraged", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setRomeroEnraged(args.arg(1).optjstring(null), args.arg(2).optboolean(false)));
            }
        });
        table.set("getEncounterState", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getEncounterState(args.arg(1).optjstring(null));
            }
        });
        table.set("findSafeEnemyTeleport", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.findSafeEnemyTeleport(args.arg(1).optjstring(null));
            }
        });
        table.set("spawnEncounter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnEncounter(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)));
            }
        });
        table.set("listMiniBossTypes", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listMiniBossTypes();
            }
        });
        table.set("isMachineLocked", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isMachineLocked(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("lockMachine", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.lockMachine(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("setMiniBossType", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setMiniBossType(args.arg(1).optjstring(null)));
            }
        });
        table.set("listBossLootTables", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listBossLootTables();
            }
        });
        table.set("getMiniBossKillCount", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getMiniBossKillCount());
            }
        });
        table.set("resetMiniBossLoot", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.resetMiniBossLoot(); return LuaValue.NONE;
            }
        });
        table.set("getMapConfig", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMapConfig();
            }
        });
        table.set("configureMap", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.configureMap(args.arg(1).checktable()); return LuaValue.NONE;
            }
        });
        table.set("listSpecialEnemies", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listSpecialEnemies();
            }
        });
        table.set("getSpecialEnemy", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getSpecialEnemy(args.arg(1).optjstring(null));
            }
        });
        table.set("releasePanzerGrab", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.releasePanzerGrab(args.arg(1).optjstring(null)));
            }
        });
        table.set("shockMiniBoss", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.shockMiniBoss(args.arg(1).optjstring(null)));
            }
        });
        table.set("damageSpecialEnemy", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.damageSpecialEnemy(args.arg(1).optjstring(null), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0), args.arg(6).optjstring(null)));
            }
        });
        table.set("listSpecialSpawners", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listSpecialSpawners();
            }
        });
        table.set("getSpecialSpawner", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getSpecialSpawner(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0));
            }
        });
        table.set("configureSpecialSpawner", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureSpecialSpawner(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).checktable()));
            }
        });
        table.set("spawnSpecialEnemy", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnSpecialEnemy(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("spawnNapalmFire", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnNapalmFire(args.arg(1).optdouble(0), args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optint(0)));
            }
        });
        table.set("shakePlayerScreen", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.shakePlayerScreen(args.arg(1).optjstring(null), args.arg(2).optint(0), args.arg(3).optdouble(0)));
            }
        });
        table.set("listSoulObjectives", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listSoulObjectives();
            }
        });
        table.set("isSoulObjectiveComplete", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.isSoulObjectiveComplete(args.arg(1).optjstring(null)));
            }
        });
        table.set("areSoulObjectivesComplete", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.areSoulObjectivesComplete(args.arg(1).optjstring(null)));
            }
        });
        table.set("getSoulObjective", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getSoulObjective(args.arg(1).optjstring(null));
            }
        });
        table.set("configureSoulObjective", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureSoulObjective(args.arg(1).optjstring(null), args.arg(2).checktable()));
            }
        });
        table.set("resetSoulObjective", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.resetSoulObjective(args.arg(1).optjstring(null)));
            }
        });
        table.set("listTeleporters", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listTeleporters();
            }
        });
        table.set("getTeleporter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getTeleporter(args.arg(1).optjstring(null));
            }
        });
        table.set("configureTeleporter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureTeleporter(args.arg(1).optjstring(null), args.arg(2).checktable()));
            }
        });
        table.set("resetTeleporterCooldown", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.resetTeleporterCooldown(args.arg(1).optjstring(null)));
            }
        });
        table.set("activateTeleporter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.activateTeleporter(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("getTeleporterCondition", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getTeleporterCondition(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("getNavigationLink", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getNavigationLink(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0));
            }
        });
        table.set("configureNavigationLink", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureNavigationLink(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).checktable()));
            }
        });
        table.set("setSpawnerChannels", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setSpawnerChannels(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("getEquipmentLoadout", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getEquipmentLoadout(args.arg(1).optjstring(null));
            }
        });
        table.set("equipEquipment", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.equipEquipment(args.arg(1).optjstring(null), args.arg(2).optjstring(null)));
            }
        });
        table.set("setEquipmentCharges", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setEquipmentCharges(args.arg(1).optjstring(null), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("getEnemyStatus", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getEnemyStatus(args.arg(1).optjstring(null));
            }
        });
        table.set("getSkyDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getSkyDelivery();
            }
        });
        table.set("configureSkyDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureSkyDelivery(args.arg(1).checktable()));
            }
        });
        table.set("triggerSkyDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.triggerSkyDelivery());
            }
        });
        table.set("deliverSkyMachineAt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.deliverSkyMachineAt(args.arg(1).optjstring(null), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optint(0)));
            }
        });
        table.set("cancelSkyDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.cancelSkyDelivery());
            }
        });
        table.set("resetSkyDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.resetSkyDelivery(); return LuaValue.NONE;
            }
        });
        table.set("notifyDeliveryDoorChannel", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.notifyDeliveryDoorChannel(args.arg(1).optint(0)); return LuaValue.NONE;
            }
        });
        table.set("placeMachineDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.placeMachineDelivery(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).checktable()));
            }
        });
        table.set("getMachineDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMachineDelivery(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0));
            }
        });
        table.set("listMachineDeliveries", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listMachineDeliveries();
            }
        });
        table.set("listDeliveryMachines", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listDeliveryMachines();
            }
        });
        table.set("configureMachineDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureMachineDelivery(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).checktable()));
            }
        });
        table.set("triggerMachineDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.triggerMachineDelivery(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("deliverMachineAt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.deliverMachineAt(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).optint(0), args.arg(6).optint(0), args.arg(7).optint(0)));
            }
        });
        table.set("resetMachineDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.resetMachineDelivery(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)));
            }
        });
        table.set("cancelMachineDelivery", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.cancelMachineDelivery(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("setMachineDeliveryEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setMachineDeliveryEnabled(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)));
            }
        });
        table.set("setDeliveryMachinePrice", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setDeliveryMachinePrice(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).optint(0)));
            }
        });
        table.set("getMapDevice", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMapDevice(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0));
            }
        });
        table.set("configureMapDevice", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureMapDevice(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).checktable()));
            }
        });
        table.set("setEmitterEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setEmitterEnabled(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)));
            }
        });
        table.set("pulseEmitter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.pulseEmitter(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("activateTrap", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.activateTrap(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optjstring(null), args.arg(5).optboolean(false)));
            }
        });
        table.set("stopTrap", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.stopTrap(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("resetTrap", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.resetTrap(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("listMapDevices", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listMapDevices();
            }
        });
        table.set("listEmitterEffects", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.listEmitterEffects(args.arg(1).optjstring(null));
            }
        });
        table.set("spawnMrChief", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnMrChief(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("queueMrChief", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.queueMrChief(); return LuaValue.NONE;
            }
        });
        table.set("spawnZombieCat", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.spawnZombieCat(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0)));
            }
        });
        table.set("getZombieCatConfig", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getZombieCatConfig();
            }
        });
        table.set("configureZombieCat", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.configureZombieCat(args.arg(1).checktable()); return LuaValue.NONE;
            }
        });
        table.set("getMrChiefConfig", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getMrChiefConfig();
            }
        });
        table.set("configureMrChief", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.configureMrChief(args.arg(1).checktable()); return LuaValue.NONE;
            }
        });
        table.set("setTeleporterCondition", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.setTeleporterCondition(args.arg(1).optjstring(null), args.arg(2).optjstring(null), args.arg(3).optboolean(false)); return LuaValue.NONE;
            }
        });
        table.set("getTeleporterDefcon", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.getTeleporterDefcon(args.arg(1).optjstring(null)));
            }
        });
        table.set("endTeleporterTrip", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                api.endTeleporterTrip(args.arg(1).optjstring(null)); return LuaValue.NONE;
            }
        });
        table.set("getAmbientDevice", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return api.getAmbientDevice(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0));
            }
        });
        table.set("configureAmbientDevice", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.configureAmbientDevice(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).checktable()));
            }
        });
        table.set("setAmbientDeviceEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs supplied) {
                Varargs args = supplied.arg1() == table ? supplied.subargs(2) : supplied;
                return value(api.setAmbientDeviceEnabled(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0), args.arg(4).optboolean(false)));
            }
        });
        return table;
    }
    private static LuaValue value(String v) { return v == null ? LuaValue.NIL : LuaValue.valueOf(v); }
    private static LuaValue value(boolean v) { return LuaValue.valueOf(v); }
    private static LuaValue value(double v) { return LuaValue.valueOf(v); }
}

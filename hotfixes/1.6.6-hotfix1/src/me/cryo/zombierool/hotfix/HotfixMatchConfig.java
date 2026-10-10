package me.cryo.zombierool.hotfix;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.*;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.network.chat.Component;

/** Per-world baseline for temporary match configuration, including legacy Lua APIs. */
public final class HotfixMatchConfig extends SavedData {
    private CompoundTag original;
    public static HotfixMatchConfig load(CompoundTag tag) {
        var journal=new HotfixMatchConfig();
        if(tag.contains("original",10))journal.original=tag.getCompound("original").copy();
        return journal;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if(original!=null)tag.put("original",original.copy());
        return tag;
    }
    private static HotfixMatchConfig get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HotfixMatchConfig::load,HotfixMatchConfig::new,"zr_hotfix_match_config");
    }
    public static void begin(ServerLevel level) {
        restore(level);
        var journal=get(level);
        journal.original=new CompoundTag();
        WorldConfig.get(level).saveEditable(journal.original);
        journal.setDirty();
    }
    public static void restore(ServerLevel level) {
        var journal=get(level);
        if(journal.original==null)return;
        var config=WorldConfig.get(level);
        config.loadEditable(journal.original.copy());config.setDirty();
        journal.original=null;journal.setDirty();
        WaveManager.currentSessionMusic=config.getMusicPreset();
        if(WaveManager.currentSessionMusic==null||WaveManager.currentSessionMusic.isEmpty())WaveManager.currentSessionMusic="default";
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),new S2CSetFogPresetPacket(config.getFogPreset(),config.getCustomFogR(),config.getCustomFogG(),config.getCustomFogB(),config.getCustomFogNear(),config.getCustomFogFar(),config.isFogOutdoorsOnly()));
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),new S2CSetEyeColorPacket(config.getEyeColorPreset()));
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),new S2CSyncWeatherPacket(config.areParticlesEnabled()&&config.getParticleTypeId()!=null,config.getParticleTypeId()==null?"":config.getParticleTypeId().toString(),config.getParticleDensity(),config.getParticleMode()));
        if(config.getDayNightMode().equals("day"))level.setDayTime(6000);
        else if(config.getDayNightMode().equals("night"))level.setDayTime(18000);
        for(var player:level.getServer().getPlayerList().getPlayers())if(player.serverLevel()==level)player.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:"+WaveManager.currentSessionMusic));
    }
}

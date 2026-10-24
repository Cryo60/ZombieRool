package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import me.cryo.zombierool.entity.SpecialEnemyEntity;

/** One napalm per map, with a persisted wave cooldown after death. Still a minion. */
public final class NapalmSpawnRules extends SavedData {
    private int nextWave=0;
    private static NapalmSpawnRules data(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(tag->{var d=new NapalmSpawnRules();d.nextWave=Math.max(0,tag.getInt("NextWave"));return d;},NapalmSpawnRules::new,"zombierool_napalm");}
    @Override public CompoundTag save(CompoundTag tag){tag.putInt("NextWave",nextWave);return tag;}
    public static boolean available(ServerLevel level){
        if(WaveManager.getCurrentWave()<data(level).nextWave)return false;
        for(var dimension:level.getServer().getAllLevels()){
            if(SpawnRedistribution.pendingType(dimension,me.cryo.zombierool.block.system.UniversalSpawnerSystem.SpawnerMobType.NAPALM_MINION)>0)return false;
            for(var entity:dimension.getAllEntities())if(entity instanceof SpecialEnemyEntity mob&&mob.isNapalm()&&mob.isAlive())return false;
        }
        return true;
    }
    public static void died(ServerLevel level){var d=data(level);d.nextWave=WaveManager.getCurrentWave()+1+level.random.nextInt(2);d.setDirty();}
    public static void reset(ServerLevel level){var d=data(level);d.nextWave=0;d.setDirty();}
}

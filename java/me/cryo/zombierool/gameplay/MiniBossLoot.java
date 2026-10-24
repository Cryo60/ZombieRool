package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.config.SpecialEnemyConfig;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import java.util.*;

/** First kill is per match, independent of how many spawner chunks are loaded. */
public final class MiniBossLoot extends SavedData {
    private int kills;
    private static MiniBossLoot data(ServerLevel level){return level.getDataStorage().computeIfAbsent(tag->{var d=new MiniBossLoot();d.kills=Math.max(0,tag.getInt("Kills"));return d;},MiniBossLoot::new,"zombierool_boss_loot");}
    @Override public CompoundTag save(CompoundTag tag){tag.putInt("Kills",kills);return tag;}
    public static int kills(ServerLevel level){return data(level).kills;}
    public static void reset(ServerLevel level){var d=data(level);d.kills=0;d.setDirty();}
    public static List<String> tables(ServerLevel level){return level.getServer().getLootData().getKeys(LootDataType.TABLE).stream().map(ResourceLocation::toString).sorted().toList();}
    public static void killed(SpecialEnemyEntity boss,DamageSource source){
        if(!(boss.level() instanceof ServerLevel level)||!WaveManager.isGameRunning())return;
        WardenLocks.drop(boss);
        var state=data(level);boolean first=state.kills++==0;state.setDirty();var config=SpecialEnemyConfig.get(level);
        ServerPlayer killer=source.getEntity() instanceof ServerPlayer p?p:me.cryo.zombierool.block.system.MapDeviceSystem.damageOwner();
        if(first)drop(level,boss,source,killer,config,"firstLoot");
        if(!first||config.getBoolean("recurringOnFirst"))drop(level,boss,source,killer,config,"recurringLoot");
        LuaScriptManager.callEvent("OnMiniBossKilled",killer==null?"":killer.getUUID().toString(),boss.kind(),boss.getUUID().toString(),first,state.kills);
    }
    private static void drop(ServerLevel level,SpecialEnemyEntity boss,DamageSource damage,ServerPlayer killer,CompoundTag config,String key){
        var item=net.minecraft.world.item.ItemStack.of(config.getCompound(key+"Stack"));
        if(!item.isEmpty()){
            var entity=new net.minecraft.world.entity.item.ItemEntity(level,boss.getX(),boss.getY()+.3,boss.getZ(),item.copy());
            entity.getPersistentData().putBoolean("zr_boss_loot",true);entity.setDefaultPickUpDelay();level.addFreshEntity(entity);
            LuaScriptManager.callEvent("OnMiniBossLoot",boss.getUUID().toString(),"item:"+net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()));return;
        }
        String id=config.getString(key);
        if(id.isBlank())return;var location=ResourceLocation.tryParse(id);if(location==null||!level.getServer().getLootData().getKeys(LootDataType.TABLE).contains(location))return;
        var builder=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,boss.position()).withParameter(LootContextParams.THIS_ENTITY,boss).withParameter(LootContextParams.DAMAGE_SOURCE,damage);
        if(damage.getEntity()!=null)builder.withParameter(LootContextParams.KILLER_ENTITY,damage.getEntity());
        if(damage.getDirectEntity()!=null)builder.withParameter(LootContextParams.DIRECT_KILLER_ENTITY,damage.getDirectEntity());
        if(killer!=null)builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER,killer).withLuck(killer.getLuck());
        var params=builder.create(LootContextParamSets.ENTITY);
        for(var stack:level.getServer().getLootData().getLootTable(location).getRandomItems(params)){
            if(stack.isEmpty())continue;
            var drop=new net.minecraft.world.entity.item.ItemEntity(level,boss.getX(),boss.getY()+.3,boss.getZ(),stack);
            drop.getPersistentData().putBoolean("zr_boss_loot",true);drop.setDefaultPickUpDelay();level.addFreshEntity(drop);
        }
        LuaScriptManager.callEvent("OnMiniBossLoot",boss.getUUID().toString(),id);
    }
    private MiniBossLoot() {}
}

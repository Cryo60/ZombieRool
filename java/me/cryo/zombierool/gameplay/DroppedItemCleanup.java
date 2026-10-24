package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A saved epoch also invalidates drops in chunks that were unloaded at match reset. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class DroppedItemCleanup {
    private static final String TAG="zr_drop_epoch";
    private DroppedItemCleanup(){}
    public static final class Epoch extends SavedData {
        long value;
        static Epoch load(CompoundTag n){var data=new Epoch();data.value=n.getLong("epoch");return data;}
        @Override public CompoundTag save(CompoundTag n){n.putLong("epoch",value);return n;}
    }
    private static Epoch epoch(ServerLevel level){return level.getDataStorage().computeIfAbsent(Epoch::load,Epoch::new,"zombierool_drop_cleanup");}
    public static void clear(ServerLevel level){
        var data=epoch(level);data.value++;data.setDirty();
        for(var entity:level.getAllEntities())if(entity instanceof ItemEntity || entity instanceof EquipmentEntity || entity instanceof me.cryo.zombierool.entity.VrilHumanEntity)entity.discard();
        for(var player:level.players())EquipmentSystem.cancel(player);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof ItemEntity || event.getEntity() instanceof EquipmentEntity))return;
        long current=epoch(level).value;var n=event.getEntity().getPersistentData();
        if(event.loadedFromDisk()&&current>0&&(!n.contains(TAG)||n.getLong(TAG)!=current)){event.setCanceled(true);return;}
        n.putLong(TAG,current);
    }
}

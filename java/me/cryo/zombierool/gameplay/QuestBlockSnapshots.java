package me.cryo.zombierool.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

/** Map-script scenery can disappear during a match without losing its authored block entity. */
public final class QuestBlockSnapshots extends SavedData {
    private final CompoundTag blocks=new CompoundTag();
    public static QuestBlockSnapshots get(ServerLevel level){return level.getDataStorage().computeIfAbsent(QuestBlockSnapshots::load,QuestBlockSnapshots::new,"zombierool_quest_blocks");}
    public static QuestBlockSnapshots load(CompoundTag tag){var data=new QuestBlockSnapshots();data.blocks.merge(tag.getCompound("blocks"));return data;}
    @Override public CompoundTag save(CompoundTag tag){tag.put("blocks",blocks.copy());return tag;}
    public boolean hidden(ServerLevel level,String id,BlockPos pos,boolean hidden){
        return hidden(level,id,pos,hidden,3);
    }
    public boolean hidden(ServerLevel level,String id,BlockPos pos,boolean hidden,int flags){
        if(id==null||!id.matches("[A-Za-z0-9_.:-]{1,80}")||!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos))return false;
        var snapshot=blocks.getCompound(id);
        if(!snapshot.isEmpty()&&snapshot.getLong("pos")!=pos.asLong())return false;
        if(hidden){
            if(snapshot.isEmpty()){
                var state=level.getBlockState(pos);if(state.isAir())return true;
                if(blocks.size()>=256)return false;
                snapshot.putLong("pos",pos.asLong());snapshot.put("state",NbtUtils.writeBlockState(state));
                var entity=level.getBlockEntity(pos);if(entity!=null)snapshot.put("entity",entity.saveWithFullMetadata());
                blocks.put(id,snapshot);setDirty();
            }
            // Never erase a different block placed by the map author while the prop is hidden.
            var original=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),snapshot.getCompound("state"));
            if(!level.getBlockState(pos).isAir()&&!level.getBlockState(pos).is(original.getBlock()))return false;
            if(level.getBlockState(pos).isAir())return true;
            // This is a saved prop, not a broken container: do not drop a second
            // copy of contents that will be restored from the block entity NBT.
            MatchWorldJournal.note(level,pos);
            level.removeBlockEntity(pos);
            return level.setBlock(pos,Blocks.AIR.defaultBlockState(),flags);
        }
        if(snapshot.isEmpty())return true;
        var state=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),snapshot.getCompound("state"));
        var current=level.getBlockState(pos);
        if(!current.isAir()&&!current.equals(state))return false;
        // The match journal may already have put back the block. Older JARs
        // recorded it after removing its block entity, leaving an empty display.
        if(current.isAir()&&!level.setBlock(pos,state,flags))return false;
        var entity=level.getBlockEntity(pos);
        if(entity!=null&&snapshot.contains("entity")){entity.load(snapshot.getCompound("entity"));entity.setChanged();level.sendBlockUpdated(pos,state,state,3);}
        blocks.remove(id);setDirty();return true;
    }

    /** Restore scenery as soon as a match ends, including props hidden in its journal. */
    public void restoreAll(ServerLevel level){
        for(String id:java.util.Set.copyOf(blocks.getAllKeys())){
            var pos=BlockPos.of(blocks.getCompound(id).getLong("pos"));
            // MatchWorldJournal restores its own entries too; loading here is
            // limited to the at most 256 explicitly saved quest props.
            level.getChunkAt(pos);
            hidden(level,id,pos,false);
        }
    }
}

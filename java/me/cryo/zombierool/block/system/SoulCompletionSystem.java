package me.cryo.zombierool.block.system;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class SoulCompletionSystem {
    private static final java.util.Map<Level,java.util.Set<Checker>> LOADED=new java.util.WeakHashMap<>();
    public static void refreshAll(Level level){for(Checker c:java.util.List.copyOf(LOADED.getOrDefault(level,java.util.Set.of())))c.tick();}
    public static void unload(Level level){LOADED.remove(level);}
    public static class LogicBlock extends Block implements EntityBlock {
        public LogicBlock(){super(Properties.of().strength(-1,3600000).sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.POWERED,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(BlockStateProperties.POWERED);}
        @Override public boolean isSignalSource(BlockState s){return true;}
        @Override public int getSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return s.getValue(BlockStateProperties.POWERED)?15:0;}
        @Override public int getDirectSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return getSignal(s,l,p,d);}
        @Override public boolean canConnectRedstone(BlockState s,BlockGetter l,BlockPos p,Direction d){return true;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Checker(p,s);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide&&t==MapAuthorSystem.COMPLETION_TYPE.get()?(level,p,state,be)->((Checker)be).tick():null;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand h,BlockHitResult hit){if(player instanceof ServerPlayer sp&&sp.isCreative()&&l.getBlockEntity(p) instanceof Checker c)TransportPackets.open(sp,p,"soul_checker",c.config());return InteractionResult.sidedSuccess(l.isClientSide);}
    }
    public static class Checker extends BlockEntity {
        public String ids="",enableChannels="",disableChannels="";public int channelDuration;public boolean enabled=true;private long seen=-1,firedEpoch=-1;private boolean fired;
        public Checker(BlockPos p,BlockState s){super(MapAuthorSystem.COMPLETION_TYPE.get(),p,s);}
        public CompoundTag config(){var n=new CompoundTag();n.putString("objectiveIds",ids);n.putBoolean("enabled",enabled);n.putString("enableChannels",enableChannels);n.putString("disableChannels",disableChannels);n.putInt("channelDuration",channelDuration);return n;}
        public void configure(CompoundTag n){ids=n.getString("objectiveIds").trim();if(ids.length()>512)ids=ids.substring(0,512);enabled=n.getBoolean("enabled");enableChannels=channels(n.getString("enableChannels"));disableChannels=channels(n.getString("disableChannels"));channelDuration=Math.max(0,Math.min(86400,n.getInt("channelDuration")));fired=false;seen=-1;setChanged();tick();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        private static String channels(String text){return text.length()<=256?text.trim():"";}
        public void tick(){
            if(!(level instanceof ServerLevel server))return;var registry=SoulObjectiveRegistry.get(server);
            if(firedEpoch!=registry.epoch){firedEpoch=registry.epoch;fired=false;setChanged();}
            if(seen!=registry.revision){
                seen=registry.revision;boolean active=enabled&&registry.allComplete(ids);
                if(getBlockState().getValue(BlockStateProperties.POWERED)!=active){level.setBlock(worldPosition,getBlockState().setValue(BlockStateProperties.POWERED,active),3);level.updateNeighborsAt(worldPosition,getBlockState().getBlock());}
                if(!active&&fired){fired=false;setChanged();}
            }
            if(getBlockState().getValue(BlockStateProperties.POWERED)&&me.cryo.zombierool.gameplay.WaveManager.isGameRunning()&&!fired){
                me.cryo.zombierool.gameplay.SpawnChannelActions.apply(server,config());fired=true;setChanged();
            }
        }
        @Override public void onLoad(){super.onLoad();if(level!=null)LOADED.computeIfAbsent(level,l->new java.util.HashSet<>()).add(this);seen=-1;tick();}
        @Override public void setRemoved(){if(level!=null)LOADED.getOrDefault(level,new java.util.HashSet<>()).remove(this);super.setRemoved();}
        @Override public void load(CompoundTag n){super.load(n);ids=n.getString("objectiveIds");enableChannels=n.getString("enableChannels");disableChannels=n.getString("disableChannels");channelDuration=n.getInt("channelDuration");fired=n.getBoolean("fired");firedEpoch=n.getLong("firedEpoch");enabled=!n.contains("enabled")||n.getBoolean("enabled");seen=-1;}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.merge(config());n.putBoolean("fired",fired);n.putLong("firedEpoch",firedEpoch);}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
}

package me.cryo.zombierool.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class MapActorMarkerBlock extends AbstractTechnicalBlock implements net.minecraft.world.level.block.EntityBlock {
    public MapActorMarkerBlock(){super(BlockBehaviour.Properties.of().strength(-1,3600000).noCollission().noOcclusion());}
    @Override protected void addTechnicalTooltip(java.util.List<net.minecraft.network.chat.Component> lines){lines.add(net.minecraft.network.chat.Component.translatable("block.zombierool.map_actor_marker.tooltip"));}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return net.minecraft.world.level.block.Block.box(0,0,0,16,1,16);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.phys.shapes.CollisionContext c){return net.minecraft.world.phys.shapes.Shapes.empty();}
    @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.level.pathfinder.PathComputationType type){return type==net.minecraft.world.level.pathfinder.PathComputationType.LAND;}
    @Override public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.entity.Mob mob){return net.minecraft.world.level.pathfinder.BlockPathTypes.OPEN;}
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos p,BlockState s){return new MarkerData(p,s);}
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(BlockState s,net.minecraft.world.phys.HitResult hit,BlockGetter l,BlockPos p,Player player){var item=new net.minecraft.world.item.ItemStack(this);return item;}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack item){super.setPlacedBy(l,p,s,placer,item);if(l instanceof net.minecraft.server.level.ServerLevel server&&placer instanceof Player player&&player.isCreative()&&player.hasPermissions(2)&&item.hasTag()){var rule=item.getTag().getCompound("BlockEntityTag").getCompound("Rule");if(!rule.isEmpty()){me.cryo.zombierool.gameplay.MapActors.get(server).marker(p,rule);if(l.getBlockEntity(p) instanceof MarkerData be)be.configure(rule);}}}
    public static final class MarkerData extends net.minecraft.world.level.block.entity.BlockEntity {
        private net.minecraft.nbt.CompoundTag rule=new net.minecraft.nbt.CompoundTag();
        public MarkerData(BlockPos p,BlockState s){super(me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BE.get(),p,s);}
        public void configure(net.minecraft.nbt.CompoundTag n){rule=n.copy();setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override public void onLoad(){super.onLoad();if(level instanceof net.minecraft.server.level.ServerLevel server){var data=me.cryo.zombierool.gameplay.MapActors.get(server);var stored=data.marker(worldPosition);if(!rule.isEmpty()&&!rule.equals(stored))data.marker(worldPosition,rule);else if(rule.isEmpty())rule=stored;}}
        @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag n){super.saveAdditional(n);if(level instanceof net.minecraft.server.level.ServerLevel server)rule=me.cryo.zombierool.gameplay.MapActors.get(server).marker(worldPosition);n.put("Rule",rule.copy());}
        @Override public void load(net.minecraft.nbt.CompoundTag n){super.load(n);rule=n.getCompound("Rule").copy();if(level instanceof net.minecraft.server.level.ServerLevel server&&!rule.isEmpty())me.cryo.zombierool.gameplay.MapActors.get(server).marker(worldPosition,rule);}
        @Override public net.minecraft.nbt.CompoundTag getUpdateTag(){var n=new net.minecraft.nbt.CompoundTag();saveAdditional(n);return n;}
        @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isCreative()&&player.hasPermissions(2)){
            if(player instanceof net.minecraft.server.level.ServerPlayer server){var n=new net.minecraft.nbt.CompoundTag();n.putString("action","actor_open");n.putLong("pos",pos.asLong());me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->server),new me.cryo.zombierool.scripting.LuaWorkbenchPackets.Response(n));}
            return InteractionResult.sidedSuccess(level.isClientSide);
        }return InteractionResult.PASS;
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(!state.is(next.getBlock())&&level instanceof net.minecraft.server.level.ServerLevel server)me.cryo.zombierool.gameplay.MapActors.get(server).removeMarker(pos);super.onRemove(state,level,pos,next,moving);}
}

package me.cryo.zombierool.block.system;

import me.cryo.zombierool.block.AbstractTechnicalBlock;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Remember a crossed corridor gate per mob; reject only backwards crossings of that gate. */
public final class OneWayGate {
    private static final Map<Level,Map<BlockPos,Anchor>> LOADED=new WeakHashMap<>();
    private OneWayGate(){}
    public static void unload(Level level){LOADED.remove(level);}
    public static void tick(Mob mob){
        if((!(mob instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity)&&!(mob instanceof me.cryo.zombierool.entity.MapActorEntity))||!mob.isAlive())return;
        CompoundTag data=mob.getPersistentData();String dimension=mob.level().dimension().location().toString();
        Vec3 now=mob.position(),old=data.getString("zr_gate_dimension").equals(dimension)?new Vec3(data.getDouble("zr_gate_x"),data.getDouble("zr_gate_y"),data.getDouble("zr_gate_z")):now;
        CompoundTag passed=data.getCompound("zr_passed_gates");
        for(Anchor gate:LOADED.getOrDefault(mob.level(),Map.of()).values())if(gate.enabled&&gate.inside(now)&&gate.side(old)<0&&gate.side(now)>=0){
            String key=dimension+":"+gate.getBlockPos().asLong()+":"+gate.id;
            if(!passed.getBoolean(key)){passed.putBoolean(key,true);me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(mob);mob.getNavigation().stop();}
        }
        data.put("zr_passed_gates",passed);data.putString("zr_gate_dimension",dimension);data.putDouble("zr_gate_x",now.x);data.putDouble("zr_gate_y",now.y);data.putDouble("zr_gate_z",now.z);
    }
    public static boolean allowed(Mob mob,Node from,Node to){
        if((!(mob instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity)&&!(mob instanceof me.cryo.zombierool.entity.MapActorEntity)))return true;
        var gates=LOADED.get(mob.level());if(gates==null||gates.isEmpty())return true;
        var passed=mob.getPersistentData().getCompound("zr_passed_gates");if(passed.isEmpty())return true;
        Vec3 a=new Vec3(from.x+.5,from.y,from.z+.5),b=new Vec3(to.x+.5,to.y,to.z+.5);
        String dimension=mob.level().dimension().location().toString();
        for(Anchor gate:gates.values()){
            if(!gate.enabled||!passed.getBoolean(dimension+":"+gate.getBlockPos().asLong()+":"+gate.id)||gate.side(a)<0||gate.side(b)>=0)continue;
            double t=gate.side(a)/(gate.side(a)-gate.side(b));
            if(gate.inside(a.lerp(b,t)))return false;
        }
        return true;
    }
    public static class Block extends AbstractTechnicalBlock implements EntityBlock {
        public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
        public Block(){super(Properties.of().strength(-1,3600000).noCollission().noOcclusion());registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection());}
        @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
        @Override public BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
        @Override protected VoxelShape getTechnicalCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override protected void addTechnicalTooltip(List<Component> list){list.add(Component.translatable("tooltip.zombierool.one_way_gate"));}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Anchor(p,s);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){if(!l.isClientSide&&player instanceof ServerPlayer sp&&sp.isCreative()&&l.getBlockEntity(p) instanceof Anchor a)TransportPackets.open(sp,p,"oneway",a.config());return InteractionResult.sidedSuccess(l.isClientSide);}
    }
    public static class Anchor extends BlockEntity {
        public boolean enabled=true;public int width=3,height=4;public UUID id=UUID.randomUUID();
        public Anchor(BlockPos p,BlockState s){super(MapAuthorSystem.GATE_TYPE.get(),p,s);}
        public double side(Vec3 v){Direction d=getBlockState().getValue(Block.FACING);return (v.x-worldPosition.getX()-.5)*d.getStepX()+(v.z-worldPosition.getZ()-.5)*d.getStepZ();}
        public boolean inside(Vec3 v){Direction d=getBlockState().getValue(Block.FACING);double across=(v.x-worldPosition.getX()-.5)*d.getStepZ()-(v.z-worldPosition.getZ()-.5)*d.getStepX();return Math.abs(across)<=width/2.0&&v.y>=worldPosition.getY()-.5&&v.y<=worldPosition.getY()+height;}
        public CompoundTag config(){var n=new CompoundTag();n.putBoolean("enabled",enabled);n.putInt("gateWidth",width);n.putInt("gateHeight",height);return n;}
        public void configure(CompoundTag n){enabled=n.getBoolean("enabled");width=Math.max(1,Math.min(64,n.getInt("gateWidth")));height=Math.max(1,Math.min(32,n.getInt("gateHeight")));setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        @Override public void onLoad(){super.onLoad();if(level!=null)LOADED.computeIfAbsent(level,l->new HashMap<>()).put(worldPosition,this);}
        @Override public void setRemoved(){if(level!=null)LOADED.getOrDefault(level,new HashMap<>()).remove(worldPosition);super.setRemoved();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.merge(config());n.putUUID("gateId",id);}
        @Override public void load(CompoundTag n){super.load(n);enabled=!n.contains("enabled")||n.getBoolean("enabled");width=n.contains("gateWidth")?Math.max(1,Math.min(64,n.getInt("gateWidth"))):3;height=n.contains("gateHeight")?Math.max(1,Math.min(32,n.getInt("gateHeight"))):4;if(n.hasUUID("gateId"))id=n.getUUID("gateId");}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
}

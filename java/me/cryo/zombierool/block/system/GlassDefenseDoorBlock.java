package me.cryo.zombierool.block.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Glass damage 7..0, then five rebuildable planks; the match journal restores glass. */
public class GlassDefenseDoorBlock extends DefenseDoorSystem.DefenseDoorBlock {
    public static final BooleanProperty BREACHED = BooleanProperty.create("breached");
    public GlassDefenseDoorBlock() {
        registerDefaultState(defaultBlockState().setValue(STAGE,7).setValue(BREACHED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(BREACHED);
    }
    @Override public boolean canRepair(BlockState state) { return state.getValue(BREACHED) && super.canRepair(state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state=super.getStateForPlacement(context);
        if(state==null)return null;
        for(var direction:new net.minecraft.core.Direction[]{state.getValue(FACING).getClockWise(),state.getValue(FACING).getCounterClockWise()}){
            BlockState adjacent=context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            if(adjacent.is(this)&&adjacent.getValue(HALF)==DoubleBlockHalf.LOWER&&adjacent.getValue(FACING)==state.getValue(FACING)&&adjacent.getValue(CENTERED)==state.getValue(CENTERED)){
                state=state.setValue(HINGE,direction==state.getValue(FACING).getClockWise()?net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT:net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);break;
            }
        }
        return state;
    }
    @Override public BlockState updateShape(BlockState state,net.minecraft.core.Direction direction,BlockState neighbor,net.minecraft.world.level.LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        BlockState updated=super.updateShape(state,direction,neighbor,level,pos,neighborPos);
        if(updated.is(this)&&direction.getAxis().isHorizontal()&&neighbor.is(this)
                &&neighbor.getValue(HALF)==updated.getValue(HALF)&&neighbor.getValue(FACING)==updated.getValue(FACING)
                &&neighbor.getValue(CENTERED)==updated.getValue(CENTERED)){
            var facing=updated.getValue(FACING);
            if(direction==facing.getClockWise()||direction==facing.getCounterClockWise())
                return updated.setValue(HINGE,direction==facing.getClockWise()?net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT:net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
        }
        return updated;
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context) {
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player && player.isCreative()) return Shapes.empty();
        if(state.getValue(STAGE)==0) return Shapes.empty();
        if(state.getValue(CENTERED)){boolean x=state.getValue(FACING).getAxis()==net.minecraft.core.Direction.Axis.X;if(state.getValue(OPEN))x=!x;return x?Block.box(6.5,0,0,9.5,16,16):Block.box(0,0,6.5,16,16,9.5);}
        return net.minecraft.world.level.block.Blocks.IRON_DOOR.defaultBlockState().setValue(FACING,state.getValue(FACING)).setValue(HINGE,state.getValue(HINGE)).setValue(OPEN,state.getValue(OPEN)).getCollisionShape(world,pos,context);
    }
    @Override public void updateStage(Level world,BlockPos pos,int requested) {
        BlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof GlassDefenseDoorBlock))return;
        if(state.getValue(HALF)==DoubleBlockHalf.UPPER){pos=pos.below();state=world.getBlockState(pos);}
        if(!(state.getBlock() instanceof GlassDefenseDoorBlock)||state.getValue(PERMANENTLY_OPEN))return;
        int current=state.getValue(STAGE);boolean breached=state.getValue(BREACHED);
        if(requested>current && !breached)return;
        int stage=Math.max(0,Math.min(breached?5:7,requested));
        if(stage==current)return;
        if(breached){
            if(stage<current){var id=new net.minecraft.resources.ResourceLocation("zombierool","wood_snap_"+String.format(java.util.Locale.ROOT,"%02d",world.random.nextInt(6)));world.playSound(null,pos,net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),SoundSource.BLOCKS,1.0f,1.0f);}
            if(stage>current){Player player=world.getNearestPlayer(pos.getX(),pos.getY(),pos.getZ(),3,false);if(player!=null&&DefenseDoorSystem.RepairTracker.tryAddRepair(player))me.cryo.zombierool.gameplay.PointManager.modifyScore(player,10);}
            world.setBlock(pos,state.setValue(STAGE,stage),3);
            BlockState top=world.getBlockState(pos.above());if(top.is(this))world.setBlock(pos.above(),top.setValue(STAGE,stage).setValue(BREACHED,true),3);
            me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnGlassDoorPlanksChanged",pos.getX(),pos.getY(),pos.getZ(),stage);return;
        }
        BlockState changed=state.setValue(STAGE,stage).setValue(BREACHED,stage==0);
        world.setBlock(pos,changed,3);
        BlockPos upper=pos.above();BlockState top=world.getBlockState(upper);
        if(top.is(this))world.setBlock(upper,top.setValue(STAGE,stage).setValue(BREACHED,stage==0),3);
        world.playSound(null,pos,SoundEvents.GLASS_BREAK,SoundSource.BLOCKS,0.65f,1.0f);
        me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnGlassDoorDamaged",pos.getX(),pos.getY(),pos.getZ(),stage,stage==0);
    }
    @Override public void attack(BlockState state,Level level,BlockPos pos,Player player) {
        // Server LeftClickBlock handles this once, including Adventure mode.
    }
    public static void damage(Level level,BlockPos pos) {
        BlockState state=level.getBlockState(pos);
        if(state.getBlock() instanceof GlassDefenseDoorBlock door && state.getValue(STAGE)>0)door.updateStage(level,pos,state.getValue(STAGE)-1);
    }
}

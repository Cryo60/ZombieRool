package me.cryo.zombierool.block;
import me.cryo.zombierool.config.GlobalSwitchState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class ActivatorBlock extends SignalTechnicalBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED=net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(POWERED);}
    public ActivatorBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.METAL)
                .strength(-1, 3600000)
                .noCollission()
                .noOcclusion());
        registerDefaultState(defaultBlockState().setValue(POWERED,false));
    }

    @Override
    protected void addTechnicalTooltip(List<Component> list) {
        list.add(Component.translatable("block.zombierool.activator.tooltip.1"));
        list.add(Component.translatable("block.zombierool.activator.tooltip.2"));
        list.add(Component.translatable("block.zombierool.activator.tooltip.3"));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!world.isClientSide && state.getBlock()!=oldState.getBlock()) {
            GlobalSwitchState.registerActivator(world, pos);
            GlobalSwitchState.notifyActivators(world);
            if (world instanceof ServerLevel server) me.cryo.zombierool.gameplay.MapPower.refreshSoon(server);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide && state.getBlock() != newState.getBlock()) {
            GlobalSwitchState.unregisterActivator(world, pos);
            world.updateNeighborsAt(pos, this);
            if (world instanceof ServerLevel server) me.cryo.zombierool.gameplay.MapPower.refreshSoon(server);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return getSignal(state, level, pos, direction); }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return true; }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getValue(POWERED)?15:0;
    }
}

package me.cryo.zombierool.block;
import me.cryo.zombierool.config.MultiReceptorState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.ChatFormatting;
import java.util.Collections;
import java.util.List;

public class AbstractReceptorBlock extends SignalTechnicalBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED=net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(POWERED);}
    private final String channelName;
    private final String tooltipName;
    public String signalChannel(){return channelName;}

    public AbstractReceptorBlock(SignalChannel channel) {
        this(channel.id(), channel.displayName());
    }

    public AbstractReceptorBlock(String channelName, String tooltipName) {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noCollission()
            .noOcclusion());
        this.channelName = channelName;
        this.tooltipName = tooltipName;
        registerDefaultState(defaultBlockState().setValue(POWERED,false));
    }

    @Override
    protected void addTechnicalTooltip(List<Component> list) {
        list.add(Component.translatable("block.zombierool.receptor.generic.tooltip.1", tooltipName).withStyle(ChatFormatting.BLUE));
        list.add(Component.translatable("block.zombierool.receptor.generic.tooltip.2", tooltipName).withStyle(ChatFormatting.GRAY));
        list.add(Component.translatable("block.zombierool.receptor.generic.tooltip.3").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) { return getSignal(state, level, pos, direction); }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getValue(POWERED)?15:0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        return true;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!world.isClientSide && state.getBlock()!=oldState.getBlock()) {
            MultiReceptorState.registerReceptor(world, channelName, pos);
            MultiReceptorState.notifyReceptors(world,channelName);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide && state.getBlock() != newState.getBlock()) {
            MultiReceptorState.unregisterReceptor(world, channelName, pos);
            world.updateNeighborsAt(pos, this);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}

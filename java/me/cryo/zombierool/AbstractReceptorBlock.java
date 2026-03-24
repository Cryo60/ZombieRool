package me.cryo.zombierool.block;
import me.cryo.zombierool.MultiReceptorState;
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

public abstract class AbstractReceptorBlock extends Block {
    private final String channelName;
    private final String tooltipName;

    public AbstractReceptorBlock(String channelName, String tooltipName) {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noCollission()
            .noOcclusion());
        this.channelName = channelName;
        this.tooltipName = tooltipName;
    }

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        list.add(Component.translatable("block.zombierool.receptor.generic.tooltip.1", tooltipName).withStyle(ChatFormatting.BLUE));
        list.add(Component.translatable("block.zombierool.receptor.generic.tooltip.2", tooltipName).withStyle(ChatFormatting.GRAY));
        list.add(Component.translatable("block.zombierool.receptor.generic.tooltip.3").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 15;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        if (world instanceof Level level) {
            return MultiReceptorState.isActivated(level, channelName) ? 15 : 0;
        }
        return 0;
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
        if (!world.isClientSide) {
            MultiReceptorState.registerReceptor(world, channelName, pos);
            world.updateNeighborsAt(pos, this);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide) {
            MultiReceptorState.unregisterReceptor(world, channelName, pos);
            world.updateNeighborsAt(pos, this);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}

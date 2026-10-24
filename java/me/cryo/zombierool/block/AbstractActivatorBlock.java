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

public class AbstractActivatorBlock extends SignalTechnicalBlock {
    private final String channelName;
    private final String tooltipName;

    public AbstractActivatorBlock(SignalChannel channel) {
        this(channel.id(), channel.displayName());
    }

    public AbstractActivatorBlock(String channelName, String tooltipName) {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noCollission()
            .noOcclusion());
        this.channelName = channelName;
        this.tooltipName = tooltipName;
    }

    @Override
    protected void addTechnicalTooltip(List<Component> list) {
        list.add(Component.translatable("block.zombierool.activator.generic.tooltip.1", tooltipName).withStyle(ChatFormatting.BLUE));
        list.add(Component.translatable("block.zombierool.activator.generic.tooltip.2").withStyle(ChatFormatting.GRAY));
        list.add(Component.translatable("block.zombierool.activator.generic.tooltip.3", tooltipName).withStyle(ChatFormatting.GRAY));
    }

    public String signalChannel(){return channelName;}

@Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        return true;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide && state.getBlock() != previous.getBlock()) MultiReceptorState.updateActivator(level, channelName, pos, level.hasNeighborSignal(pos));
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && state.getBlock() != next.getBlock()) MultiReceptorState.removeActivator(level, channelName, pos);
        super.onRemove(state, level, pos, next, moving);
    }
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (!level.isClientSide) MultiReceptorState.updateActivator(level, channelName, pos, level.hasNeighborSignal(pos));
    }
}

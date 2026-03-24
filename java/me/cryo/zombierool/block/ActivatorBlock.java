package me.cryo.zombierool.block;
import me.cryo.zombierool.GlobalSwitchState;
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

public class ActivatorBlock extends Block {
    public ActivatorBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.METAL)
                .strength(-1, 3600000)
                .noCollission() 
                .noOcclusion()); 
    }

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        list.add(Component.translatable("block.zombierool.activator.tooltip.1"));
        list.add(Component.translatable("block.zombierool.activator.tooltip.2"));
        list.add(Component.translatable("block.zombierool.activator.tooltip.3"));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!world.isClientSide) {
            GlobalSwitchState.registerActivator(world, pos);
            world.updateNeighborsAt(pos, this);
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide) {
            GlobalSwitchState.unregisterActivator(world, pos);
            world.updateNeighborsAt(pos, this);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        if (world instanceof ServerLevel serverWorld) {
            return GlobalSwitchState.isActivated(serverWorld) ? 15 : 0;
        }
        return 0;
    }
}
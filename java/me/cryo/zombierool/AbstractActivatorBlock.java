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
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;

public abstract class AbstractActivatorBlock extends Block {
    private final String channelName;
    private final String tooltipName;

    public AbstractActivatorBlock(String channelName, String tooltipName) {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.METAL)
                .strength(-1, 3600000)
                .noCollission()
                .noOcclusion());
        this.channelName = channelName;
        this.tooltipName = tooltipName;
    }

    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) return false;
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        String name = isEnglishClient() ? tooltipName + " Activator" : "Activateur " + tooltipName;
        list.add(Component.literal("§9" + name));
        list.add(Component.literal(isEnglishClient() ? "§7When it receives a Redstone signal," : "§7Lorsqu'il reçoit un signal de Redstone,"));
        list.add(Component.literal(isEnglishClient() ? "§7it sends a signal to all '" + tooltipName + " Receptors' on the map." : "§7il envoie un signal à tous les 'Récepteurs " + tooltipName + "' sur la carte."));
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 15;
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
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!world.isClientSide) {
            boolean powered = world.hasNeighborSignal(pos);
            MultiReceptorState.setActivated(world, channelName, powered);
            world.updateNeighborsAt(pos, this);
            for (BlockPos receptorPos : MultiReceptorState.getReceptorPositions(world, channelName)) {
                if (world.hasChunkAt(receptorPos)) {
                    world.updateNeighborsAt(receptorPos, world.getBlockState(receptorPos).getBlock());
                }
            }
        }
    }
}
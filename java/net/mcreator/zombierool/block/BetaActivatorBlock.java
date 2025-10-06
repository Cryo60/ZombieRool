package net.mcreator.zombierool.block;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

import net.mcreator.zombierool.MultiReceptorState;

import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Collections;

import net.minecraft.network.chat.Component; // Added import for Component
import net.minecraft.world.item.TooltipFlag; // Added import for TooltipFlag
import net.minecraft.client.Minecraft; // Added import for Minecraft client


public class BetaActivatorBlock extends Block {
    public BetaActivatorBlock() {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noCollission() // Added for consistency with other invisible blocks
            .noOcclusion()); // Added for consistency with other invisible blocks
    }

    /**
     * Helper method to check if the client's language is English.
     * This is crucial for dynamic translation of item names and tooltips.
     * @return true if the client's language code starts with "en", false otherwise.
     */
    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    /**
     * Helper method for dynamic translation based on the client's language.
     * @param frenchMessage The message to display if the client's language is French or not English.
     * @param englishMessage The message to display if the client's language is English.
     * @return The appropriate translated message.
     */
    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        list.add(Component.literal(getTranslatedMessage("§9Activateur Bêta", "§9Beta Activator")));
        list.add(Component.literal(getTranslatedMessage("§7Lorsqu'il reçoit un signal de Redstone,", "§7When it receives a Redstone signal,")));
        list.add(Component.literal(getTranslatedMessage("§7il envoie un signal à tous les 'Récepteurs Bêta' sur la carte.", "§7it sends a signal to all 'Beta Receptors' on the map.")));
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
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!world.isClientSide) {
            boolean powered = world.hasNeighborSignal(pos);
            MultiReceptorState.setActivated(world, "beta", powered);
    
            // informe d’abord le bloc activator lui-même
            world.updateNeighborsAt(pos, this);
    
            // puis balance un update à chaque récepteur enregistré
            for (BlockPos receptorPos : MultiReceptorState.getReceptorPositions(world, "beta")) {
                if (world.hasChunkAt(receptorPos)) {
                    // c’est comme dans PowerSwitchBlock :
                    // ça forcera Minecraft à appeler getSignal() sur le récepteur
                    world.updateNeighborsAt(receptorPos, world.getBlockState(receptorPos).getBlock());
                }
            }
        }
    }
}

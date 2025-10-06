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

public class UltimaReceptorBlock extends Block {
    public UltimaReceptorBlock() {
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
        list.add(Component.literal(getTranslatedMessage("§9Récepteur Ultima", "§9Ultima Receptor")));
        list.add(Component.literal(getTranslatedMessage("§7Quand l'Activateur Ultima reçoit un signal,", "§7When the Ultima Activator receives a signal,")));
        list.add(Component.literal(getTranslatedMessage("§7ce récepteur envoie un signal de Redstone tout autour de lui.", "§7this receptor sends a Redstone signal all around itself.")));
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
        System.out.println("UltimaReceptor check signal !"); // Changed from AlphaReceptor
        if (world instanceof Level level) {
            return MultiReceptorState.isActivated(level, "ultima") ? 15 : 0;
        }
        return 0;
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
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!world.isClientSide) {
            MultiReceptorState.registerReceptor(world, "ultima", pos);
            // informe ses voisins qu’un signal peut maintenant exister ici
            world.updateNeighborsAt(pos, this);
        }
    }
    
    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide) {
            MultiReceptorState.unregisterReceptor(world, "ultima", pos);
            world.updateNeighborsAt(pos, this);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}

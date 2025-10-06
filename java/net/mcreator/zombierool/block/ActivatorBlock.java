package net.mcreator.zombierool.block;

import net.mcreator.zombierool.GlobalSwitchState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component; // Import for Component
import net.minecraft.world.item.TooltipFlag; // Import for TooltipFlag
import net.minecraft.client.Minecraft; // Import for Minecraft client
import net.minecraft.world.item.ItemStack; // Import for ItemStack

import java.util.List; // Import for List

public class ActivatorBlock extends Block {
    
    public ActivatorBlock() {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noCollission() // Add noCollission for consistency with other invisible blocks
            .noOcclusion()); // Add noOcclusion for consistency with other invisible blocks
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
        list.add(Component.literal(getTranslatedMessage("§9Bloc Activateur", "§9Activator Block")));
        list.add(Component.literal(getTranslatedMessage("§7Reçoit un signal du 'Power Switch'.", "§7Receives a signal from the 'Power Switch'.")));
        list.add(Component.literal(getTranslatedMessage("§7Émet un signal de Redstone lorsqu'activé.", "§7Emits a Redstone signal when activated.")));
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
        // Si ce n'est pas un ServerLevel (donc côté client), on retourne 0
        return 0;
    }

}

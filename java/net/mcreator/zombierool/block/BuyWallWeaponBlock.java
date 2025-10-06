package net.mcreator.zombierool.block;

import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.storage.loot.LootParams;               // remplace LootContext
import net.minecraft.world.item.BlockItem;                           // pour instanceof BlockItem
import net.minecraftforge.registries.ForgeRegistries;                  // pour ForgeRegistries.BLOCKS
import net.minecraft.world.SimpleMenuProvider;                        // pour SimpleMenuProvider

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Containers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;

import java.util.Collections;
import java.util.List;
import io.netty.buffer.Unpooled;

import net.mcreator.zombierool.network.CaptureWallTexturePacket;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.world.inventory.WallWeaponManagerMenu;
import net.minecraft.world.item.TooltipFlag; // Import for TooltipFlag
import net.minecraft.client.Minecraft; // Import for Minecraft client


public class BuyWallWeaponBlock extends Block implements EntityBlock {
    public BuyWallWeaponBlock() {
        super(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASEDRUM)
            .sound(SoundType.EMPTY).strength(-1, 3600000));
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
        list.add(Component.literal(getTranslatedMessage("§9Mur d'Armes Achetable", "§9Purchasable Wall Weapon")));
        list.add(Component.literal(getTranslatedMessage("§7Définit les armes au mur avec un prix (en Créatif).", "§7Defines wall weapons with a price (in Creative).")));
        list.add(Component.literal(getTranslatedMessage("§7Peut imiter l'apparence d'un bloc (clic droit en Créatif).", "§7Can mimic the appearance of a block (right-click in Creative).")));
    }
    
    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }


    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild;
        boolean sneaking = player.isShiftKeyDown();

        // 1) Créatif + Shift → ouvrer GUI
        if (creative && sneaking) {
            if (!world.isClientSide && player instanceof ServerPlayer sp) {
                MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new WallWeaponManagerMenu(
                        id, inv,
                        new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos)
                    ),
                    Component.literal("Wall Weapon Manager")
                );
                NetworkHooks.openScreen(sp, provider, pos);
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        // 2) Créatif + tient un BlockItem → capture type de bloc
        if (creative && !sneaking && !held.isEmpty() && held.getItem() instanceof BlockItem bi) {
            if (world.isClientSide) {
                ResourceLocation blockRL = ForgeRegistries.BLOCKS.getKey(bi.getBlock());
                NetworkHandler.INSTANCE.sendToServer(new CaptureWallTexturePacket(pos, blockRL));
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
        BlockEntity tileEntity = worldIn.getBlockEntity(pos);
        return tileEntity instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuyWallWeaponBlockEntity(pos, state);
    }

    @Override
    public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int eventID, int eventParam) {
        super.triggerEvent(state, world, pos, eventID, eventParam);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity == null ? false : blockEntity.triggerEvent(eventID, eventParam);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BuyWallWeaponBlockEntity be) {
                Containers.dropContents(world, pos, be);
                world.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState blockState, Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);
        if (tileentity instanceof BuyWallWeaponBlockEntity be)
            return AbstractContainerMenu.getRedstoneSignalFromContainer(be);
        else
            return 0;
    }
}

package net.mcreator.zombierool.block;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraft.world.item.BlockItem;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Containers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.core.Direction;
import net.mcreator.zombierool.entity.ZombieEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.mcreator.zombierool.block.entity.TraitorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.mcreator.zombierool.init.ZombieroolModBlocks; // Corrected import for PathBlock

import java.util.List;
import java.util.Collections;

import net.minecraft.network.chat.Component; // Import for Component
import net.minecraft.world.item.TooltipFlag; // Import for TooltipFlag
import net.minecraft.client.Minecraft; // Import for Minecraft client

public class TraitorBlock extends Block implements EntityBlock {
    public TraitorBlock() {
        super(BlockBehaviour.Properties.of()
            .ignitedByLava()
            .instrument(NoteBlockInstrument.BASS)
            .sound(new ForgeSoundType(1.0f, 1.0f,
                () -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.zombie.break_wooden_door")),
                () -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.wood.step")),
                () -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.wood.place")),
                () -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.wood.hit")),
                () -> ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.wood.fall"))
            ))
            .strength(-1, 3600000)
            .noCollission()
            .noOcclusion()
        );
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
        list.add(Component.literal(getTranslatedMessage("§9Bloc Traître", "§9Traitor Block")));
        list.add(Component.literal(getTranslatedMessage("§7Peut imiter l'apparence d'un bloc (clic droit en Créatif).", "§7Can mimic the appearance of a block (right-click in Creative).")));
        list.add(Component.literal(getTranslatedMessage("§7Se détruit au contact d'un zombie.", "§7Destroys itself on contact with a zombie.")));
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) {
            // Détection d'un zombie dans un cube 3x3x3 centré sur le bloc (inclut la hauteur)
            AABB area = new AABB(pos).inflate(1); // 1 bloc autour dans toutes les directions
            boolean hasZombie = !level.getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, area).isEmpty();
    
            if (hasZombie) {
               level.setBlock(pos, ZombieroolModBlocks.PATH.get().defaultBlockState(), 3);
            }
        }
    }


    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof ZombieEntity) {
            return Shapes.empty(); // Le zombie passe à travers
        }
    
        return Shapes.block(); // Toutes les autres entités rencontrent une collision "mur"
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        return adjacentBlockState.getBlock() == this ? true : super.skipRendering(state, adjacentBlockState, side);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // dit “utilise le BlockEntityRenderer, pas le modèle JSON classique”
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
    public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
        BlockEntity tileEntity = worldIn.getBlockEntity(pos);
        return tileEntity instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TraitorBlockEntity(pos, state);
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
            if (blockEntity instanceof TraitorBlockEntity be) {
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
        if (tileentity instanceof TraitorBlockEntity be)
            return AbstractContainerMenu.getRedstoneSignalFromContainer(be);
        else
            return 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!world.isClientSide) {
            BlockEntity entity = world.getBlockEntity(pos);
            if (entity instanceof TraitorBlockEntity traitorEntity) {
                ItemStack heldItem = player.getItemInHand(hand);
                if (!heldItem.isEmpty()) {
                    if (heldItem.getItem() instanceof BlockItem blockItem) {
                        Block block = blockItem.getBlock();
                        traitorEntity.setCopiedBlock(block);
                        traitorEntity.setChanged();
                        world.sendBlockUpdated(pos, state, state, 3);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null :
            (lvl, pos, st, be) -> {
                if (be instanceof TraitorBlockEntity traitorBlockEntity) {
                    TraitorBlockEntity.tick(lvl, pos, st, traitorBlockEntity);
                }
            };
    }
}

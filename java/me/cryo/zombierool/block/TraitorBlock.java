package me.cryo.zombierool.block;
import me.cryo.zombierool.block.entity.TraitorBlockEntity;
import me.cryo.zombierool.block.system.MimicSystem;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.ChatFormatting;
import java.util.List;

public class TraitorBlock extends MimicSystem.AbstractMimicBlock {
    public static final BooleanProperty HAS_MIMIC = BooleanProperty.create("has_mimic");

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
                .noOcclusion()
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(HAS_MIMIC, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_MIMIC);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof ZombieEntity) {
            return Shapes.empty();
        }
        return super.getCollisionShape(state, world, pos, context);
    }

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        list.add(Component.translatable("block.zombierool.traitor.tooltip.1"));
        list.add(Component.translatable("block.zombierool.traitor.tooltip.2"));
        list.add(Component.translatable("block.zombierool.traitor.tooltip.3"));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(HAS_MIMIC) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TraitorBlockEntity(pos, state);
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
        if (!player.isCreative()) return InteractionResult.PASS;
        ItemStack heldItem = player.getItemInHand(hand);
        if (!heldItem.isEmpty() && heldItem.getItem() instanceof BlockItem blockItem) {
            Block blockToCopy = blockItem.getBlock();
            if (!(blockToCopy instanceof MimicSystem.IMimicBlock)) {
                if (!world.isClientSide) {
                    BlockEntity entity = world.getBlockEntity(pos);
                    if (entity instanceof TraitorBlockEntity traitorEntity) {
                        BlockState placementState = MimicSystem.getStateForMimic(player, hand, heldItem, hit, blockToCopy);
                        traitorEntity.setMimic(placementState);
                        player.displayClientMessage(Component.translatable("message.zombierool.texture_copied").withStyle(ChatFormatting.GREEN), true);
                    }
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
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
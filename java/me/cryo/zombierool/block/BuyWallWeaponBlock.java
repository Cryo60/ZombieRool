package me.cryo.zombierool.block;

import io.netty.buffer.Unpooled;
import me.cryo.zombierool.block.entity.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.block.system.MimicSystem;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.network.CaptureWallTexturePacket;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.world.inventory.WallWeaponManagerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class BuyWallWeaponBlock extends MimicSystem.AbstractMimicBlock {
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public BuyWallWeaponBlock() {
	    super(BlockBehaviour.Properties.of()
	            .instrument(NoteBlockInstrument.BASEDRUM)
	            .sound(SoundType.STONE)
	            .strength(-1, 3600000)
	            .isValidSpawn((bs, wg, pos, et) -> false)
	            .noOcclusion()
	    );
	    this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public boolean useShapeForLightOcclusion(BlockState state) {
	    return true;
	}

	private static boolean isEnglishClient() {
	    if (Minecraft.getInstance() == null) {
	        return false;
	    }
	    return Minecraft.getInstance().options.languageCode.startsWith("en");
	}

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
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
	    builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
	    Level level = context.getLevel();
	    BlockPos pos = context.getClickedPos();

	    for (Direction dir : context.getNearestLookingDirections()) {
	        if (dir.getAxis().isHorizontal()) {
	            Direction opposite = dir.getOpposite();
	            BlockPos front = pos.relative(opposite);
	            BlockPos belowFront = front.below();
	            BlockPos aboveFront = front.above();
	            
	            boolean isAirFront = level.getBlockState(front).isAir();
	            boolean isPathBelow = level.getBlockState(belowFront).is(ZombieroolModBlocks.PATH.get());
	            boolean isPathAbove = level.getBlockState(aboveFront).is(ZombieroolModBlocks.PATH.get());
	            
	            if (isAirFront && (isPathBelow || isPathAbove)) {
	                return this.defaultBlockState().setValue(FACING, opposite);
	            }
	        }
	    }
	    return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	public BlockState rotate(BlockState state, Rotation rot) {
	    return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	public BlockState mirror(BlockState state, Mirror mirrorIn) {
	    return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
	    return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
	    ItemStack held = player.getItemInHand(hand);
	    boolean creative = player.getAbilities().instabuild;
	    boolean sneaking = player.isShiftKeyDown();

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
	    
	    if (creative && !sneaking && !held.isEmpty() && held.getItem() instanceof BlockItem bi) {
	        if (world.isClientSide) {
	            // FIX CRASH: Ne pas copier les blocs de type IMimicBlock pour éviter les boucles infinies
	            if (!(bi.getBlock() instanceof MimicSystem.IMimicBlock)) {
	                ResourceLocation blockRL = ForgeRegistries.BLOCKS.getKey(bi.getBlock());
	                NetworkHandler.INSTANCE.sendToServer(new CaptureWallTexturePacket(pos, blockRL, hit.getDirection(), hit.getLocation(), hit.isInside()));
	            } else {
	                player.displayClientMessage(Component.literal("§cVous ne pouvez pas imiter un bloc technique !").withStyle(net.minecraft.ChatFormatting.RED), true);
	            }
	        }
	        return InteractionResult.sidedSuccess(world.isClientSide);
	    }

	    return InteractionResult.PASS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
	    return new BuyWallWeaponBlockEntity(pos, state);
	}

	@Override
	public int getAnalogOutputSignal(BlockState blockState, Level world, BlockPos pos) {
	    BlockEntity tileentity = world.getBlockEntity(pos);
	    if (tileentity instanceof BuyWallWeaponBlockEntity be)
	        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(be);
	    else
	        return 0;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
	    return (lvl, pos, st, blockEntity) -> {
	        if (blockEntity instanceof BuyWallWeaponBlockEntity be) {
	            be.tick(lvl, pos, st);
	        }
	    };
	}
}
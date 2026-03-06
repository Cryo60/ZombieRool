package me.cryo.zombierool.block;

import me.cryo.zombierool.block.entity.TraitorBlockEntity;
import me.cryo.zombierool.block.system.MimicSystem;
import me.cryo.zombierool.client.LinkRenderer;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class TraitorBlock extends MimicSystem.AbstractMimicBlock {
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
	}
	
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	    if (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof ZombieEntity) {
	        return Shapes.empty();
	    }
	    return super.getCollisionShape(state, world, pos, context);
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
	    list.add(Component.literal(getTranslatedMessage("§9Bloc Traître", "§9Traitor Block")));
	    list.add(Component.literal(getTranslatedMessage("§7Peut imiter l'apparence d'un bloc (clic droit en Créatif).", "§7Can mimic the appearance of a block (right-click in Creative).")));
	    list.add(Component.literal(getTranslatedMessage("§7Se détruit au contact d'un zombie.", "§7Destroys itself on contact with a zombie.")));
	}
	
	@Override
	public RenderShape getRenderShape(BlockState state) {
	    if (FMLEnvironment.dist == Dist.CLIENT) {
	        Player player = Minecraft.getInstance().player;
	        if (player != null && (!player.isCreative() || LinkRenderer.isSurvivalViewEnabled)) {
	            return RenderShape.INVISIBLE;
	        }
	    }
	    return RenderShape.ENTITYBLOCK_ANIMATED;
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
	    if (!world.isClientSide) {
	        BlockEntity entity = world.getBlockEntity(pos);
	        if (entity instanceof TraitorBlockEntity traitorEntity) {
	            ItemStack heldItem = player.getItemInHand(hand);
	            if (!heldItem.isEmpty()) {
	                if (heldItem.getItem() instanceof BlockItem blockItem) {
	                    net.minecraft.world.level.block.Block block = blockItem.getBlock();
	                    BlockPlaceContext ctx = new BlockPlaceContext(player, hand, heldItem, hit);
	                    BlockState placementState = block.getStateForPlacement(ctx);
	                    if (placementState == null) placementState = block.defaultBlockState();
	                    traitorEntity.setMimic(placementState);
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

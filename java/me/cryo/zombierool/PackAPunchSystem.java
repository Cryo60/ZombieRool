// [main\java\me\cryo\zombierool\block\system\PackAPunchSystem.java]
package me.cryo.zombierool.block.system;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.util.PlayerVoiceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PackAPunchSystem {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Block> PACK_A_PUNCH = BLOCKS.register("pack_a_punch", PackAPunchBlock::new);
    public static final RegistryObject<Item> PACK_A_PUNCH_ITEM = ITEMS.register("pack_a_punch", () -> new BlockItem(PACK_A_PUNCH.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<PackAPunchBlockEntity>> BE = BLOCK_ENTITIES.register("pack_a_punch", () -> BlockEntityType.Builder.of(PackAPunchBlockEntity::new, PACK_A_PUNCH.get()).build(null));

    static {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(new ResourceLocation(ZombieroolMod.MODID, "zb_rct"))) {
            event.accept(PACK_A_PUNCH_ITEM.get());
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BE.get(), PackAPunchRenderer::new);
    }

    public static class Manager {
        public static void tryUsePack(Player player, Level level, BlockPos pos) {
            BlockEntity te = level.getBlockEntity(pos);
            if (!(te instanceof PackAPunchBlockEntity be)) return;

            if (!level.getBlockState(pos).getValue(PackAPunchBlock.POWERED)) {
                player.displayClientMessage(Component.translatable("message.zombierool.power_required").withStyle(ChatFormatting.RED), true);
                return;
            }

            if (be.getState() == 0) {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty() || !WeaponFacade.isWeapon(held)) {
                    player.displayClientMessage(Component.translatable("message.zombierool.packapunch.hold_weapon").withStyle(ChatFormatting.RED), true);
                    return;
                }
                
                if (!WeaponFacade.canBePackAPunched(held)) {
                    player.displayClientMessage(Component.translatable("message.zombierool.packapunch.invalid_weapon").withStyle(ChatFormatting.RED), true);
                    return;
                }

                boolean hasIngot = player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof IngotSaleItem);
                int price = be.getPrice();
                
                if (!hasIngot && PointManager.getScore(player) < price) {
                    player.displayClientMessage(Component.translatable("message.zombierool.packapunch.no_points", price).withStyle(ChatFormatting.RED), true);
                    PlayerVoiceManager.playNoMoneySound(player, level);
                    level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "pap_deny")), SoundSource.BLOCKS, 1f, 1f);
                    return;
                }

                if (hasIngot) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.getItem() instanceof IngotSaleItem) {
                            stack.shrink(1);
                            break;
                        }
                    }
                    player.displayClientMessage(Component.translatable("message.zombierool.packapunch.ingot_consumed").withStyle(ChatFormatting.YELLOW), true);
                } else {
                    PointManager.modifyScore(player, -price);
                    player.displayClientMessage(Component.translatable("message.zombierool.packapunch.points_consumed", price).withStyle(ChatFormatting.YELLOW), true);
                }

                be.startUpgrading(held.copy(), player.getUUID());
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.BLOCKS, 1f, 1f);
                level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "pap_upgrade")), SoundSource.BLOCKS, 1f, 1f);

            } else if (be.getState() == 2) {
                if (be.getOwner() != null && !be.getOwner().equals(player.getUUID())) {
                    player.displayClientMessage(Component.translatable("message.zombierool.packapunch.not_yours").withStyle(ChatFormatting.RED), true);
                    level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "pap_deny")), SoundSource.BLOCKS, 1f, 1f);
                    return;
                }

                ItemStack upgraded = be.getCurrentWeapon();
                boolean added = player.getInventory().add(upgraded);
                if (!added) {
                    player.drop(upgraded, false);
                }

                player.inventoryMenu.broadcastChanges();
                player.displayClientMessage(Component.translatable("message.zombierool.packapunch.success").withStyle(ChatFormatting.GREEN), true);
                PlayerVoiceManager.playWeaponUpgraded(player, level);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 1f);
                be.reset();
            } else {
                player.displayClientMessage(Component.translatable("message.zombierool.packapunch.in_use").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    public static class PackAPunchBlock extends HorizontalDirectionalBlock implements EntityBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

        protected static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.4, 1.0);

        public PackAPunchBlock() {
            super(BlockBehaviour.Properties.of()
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.METAL)
                    .strength(1f, 10f)
                    .noOcclusion()
            );
            this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
        }

        @Override
        public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
            super.appendHoverText(itemstack, world, list, flag);
            list.add(Component.translatable("block.zombierool.packapunch.tooltip.1"));
            list.add(Component.translatable("block.zombierool.packapunch.tooltip.2"));
            list.add(Component.translatable("block.zombierool.packapunch.tooltip.3"));
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, POWERED);
        }

        @Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
		    boolean powered = level.hasNeighborSignal(pos) 
		                   || level.hasNeighborSignal(pos.above()) 
		                   || level.hasNeighborSignal(pos.below());
		    level.setBlock(pos, state.setValue(POWERED, powered), 3);
		}

        @Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
		    BlockPos pos = context.getClickedPos();
		    Level level = context.getLevel();
		    boolean powered = level.hasNeighborSignal(pos) 
		                   || level.hasNeighborSignal(pos.above()) 
		                   || level.hasNeighborSignal(pos.below());
		    return this.defaultBlockState()
		        .setValue(FACING, context.getHorizontalDirection().getOpposite())
		        .setValue(POWERED, powered);
		}
		
		@Override
		public void neighborChanged(BlockState state, Level world, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
		    if (!world.isClientSide) {
		        boolean powered = world.hasNeighborSignal(pos) 
		                       || world.hasNeighborSignal(pos.above()) 
		                       || world.hasNeighborSignal(pos.below());
		        if (state.getValue(POWERED) != powered) {
		            world.setBlock(pos, state.setValue(POWERED, powered), 3);
		        }
		    }
		}

        @Override
        public BlockState rotate(BlockState state, Rotation rot) {
            return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirrorIn) {
            return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new PackAPunchBlockEntity(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? 
                (lvl, pos, st, be) -> {
                    if (be instanceof PackAPunchBlockEntity pbe) PackAPunchBlockEntity.clientTick(lvl, pos, st, pbe);
                } : 
                (lvl, pos, st, be) -> {
                    if (be instanceof PackAPunchBlockEntity pbe) PackAPunchBlockEntity.serverTick(lvl, pos, st, pbe);
                };
        }

        @Override
        public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
            if (state.getBlock() != newState.getBlock()) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof PackAPunchBlockEntity be) {
                    if (!be.getCurrentWeapon().isEmpty()) {
                        Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), be.getCurrentWeapon());
                    }
                }
                super.onRemove(state, world, pos, newState, isMoving);
            }
        }
    }

    public static class PackAPunchBlockEntity extends BlockEntity {
        private ItemStack currentWeapon = ItemStack.EMPTY;
        private UUID owner = null;
        private int state = 0; 
        private int timer = 0;
        private int price = 5000; 

        public PackAPunchBlockEntity(BlockPos pos, BlockState state) {
            super(BE.get(), pos, state);
        }

        public void startUpgrading(ItemStack weapon, UUID ownerId) {
            this.currentWeapon = weapon;
            this.owner = ownerId;
            this.state = 1;
            this.timer = 0;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }

        public void reset() {
            this.currentWeapon = ItemStack.EMPTY;
            this.owner = null;
            this.state = 0;
            this.timer = 0;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }

        public ItemStack getCurrentWeapon() { return currentWeapon; }
        public UUID getOwner() { return owner; }
        public int getState() { return state; }
        public int getTimer() { return timer; }
        
        public int getPrice() { return price; }
        public void setPrice(int price) {
            this.price = price;
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            }
        }

        public static void serverTick(Level level, BlockPos pos, BlockState blockState, PackAPunchBlockEntity be) {
            if (be.state == 1) {
                be.timer++;
                if (be.timer >= 100) {
                    WeaponFacade.applyPackAPunch(be.currentWeapon);
                    WeaponFacade.setAmmo(be.currentWeapon, WeaponFacade.getMaxAmmo(be.currentWeapon));
                    WeaponFacade.setReserve(be.currentWeapon, WeaponFacade.getMaxReserve(be.currentWeapon));
                    
                    if (be.currentWeapon.getItem() instanceof WeaponSystem.BaseGunItem gun && gun.hasDurability()) {
                        gun.setDurability(be.currentWeapon, gun.getMaxDurability(be.currentWeapon));
                    }
                    
                    be.state = 2;
                    be.timer = 0;
                    be.setChanged();
                    level.sendBlockUpdated(pos, blockState, blockState, 3);
                    
                    level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "pap_ready")), SoundSource.BLOCKS, 1f, 1f);
                }
            } else if (be.state == 2) {
                be.timer++;
                if (be.timer >= 200) { 
                    be.reset();
                    level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "pap_deny")), SoundSource.BLOCKS, 1f, 1f);
                }
            }
        }

        public static void clientTick(Level level, BlockPos pos, BlockState blockState, PackAPunchBlockEntity be) {
            if (be.state > 0) {
                be.timer++;
                PackAPunchSoundHandler.handleSounds(be);
            } else {
                PackAPunchSoundHandler.stopSounds(pos);
            }
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            if (this.level != null && this.level.isClientSide) {
                PackAPunchSoundHandler.stopSounds(this.worldPosition);
            }
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            if (tag.contains("Weapon")) {
                currentWeapon = ItemStack.of(tag.getCompound("Weapon"));
            } else {
                currentWeapon = ItemStack.EMPTY;
            }
            if (tag.hasUUID("Owner")) {
                owner = tag.getUUID("Owner");
            } else {
                owner = null;
            }
            if (tag.contains("Price")) {
                price = tag.getInt("Price");
            }
            state = tag.getInt("State");
            timer = tag.getInt("Timer");
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (!currentWeapon.isEmpty()) {
                tag.put("Weapon", currentWeapon.save(new CompoundTag()));
            }
            if (owner != null) {
                tag.putUUID("Owner", owner);
            }
            tag.putInt("Price", price);
            tag.putInt("State", state);
            tag.putInt("Timer", timer);
        }

        @Override
        public ClientboundBlockEntityDataPacket getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public @NotNull CompoundTag getUpdateTag() {
            return saveWithoutMetadata();
        }

        @Override
        public AABB getRenderBoundingBox() {
            return new AABB(worldPosition).inflate(2.0);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class PackAPunchRenderer implements BlockEntityRenderer<PackAPunchBlockEntity> {

        public PackAPunchRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(PackAPunchBlockEntity be, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
            BlockState stateBlock = be.getBlockState();
            
            // RENDERING THE MACHINE BLOCK (Untouched)
            poseStack.pushPose();
            poseStack.translate(0.0, 1.0, 0.0);
            BakedModel bakedModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(stateBlock);
            RenderType renderType = ItemBlockRenderTypes.getRenderType(stateBlock, false);
            VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(),
                    vertexConsumer,
                    stateBlock,
                    bakedModel,
                    1.0F, 1.0F, 1.0F,
                    combinedLight,
                    combinedOverlay
            );
            poseStack.popPose();

            ItemStack weapon = be.getCurrentWeapon();
            if (weapon.isEmpty()) return;

            int state = be.getState();
            float time = be.getTimer() + partialTicks;
            Direction facing = stateBlock.getValue(PackAPunchBlock.FACING);

            // RENDERING THE WEAPON (Lowered by 1 block so it fits inside the mechanism)
            poseStack.pushPose();
            
            poseStack.translate(0.5, 0.9, 0.5);

            float stepX = facing.getStepX();
            float stepZ = facing.getStepZ();
            
            float gunRotY = -facing.toYRot() + 90f; 

            if (state == 1) { 
                float progress = Math.min(1.0f, time / 100.0f);
                float inOut;
                if (progress < 0.2f) {
                    inOut = 1.0f - (progress / 0.2f);
                } 
                else if (progress > 0.8f) {
                    inOut = (progress - 0.8f) / 0.2f;
                } 
                else {
                    inOut = 0.0f;
                }

                poseStack.translate(stepX * inOut * 0.8, 0, stepZ * inOut * 0.8);

                if (inOut == 0.0f) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(time * 25f));
                    poseStack.mulPose(Axis.XP.rotationDegrees(time * 5f));
                } else {
                    poseStack.mulPose(Axis.YP.rotationDegrees(gunRotY));
                }
            } else if (state == 2) { 
                poseStack.translate(stepX * 1.2, Math.sin(time * 0.1) * 0.05, stepZ * 1.2);
                poseStack.mulPose(Axis.YP.rotationDegrees(gunRotY));
            }

            poseStack.scale(0.6f, 0.6f, 0.6f);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                weapon, ItemDisplayContext.GROUND, combinedLight, combinedOverlay, poseStack, buffer, be.getLevel(), 0
            );

            poseStack.popPose();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class PackAPunchSoundHandler {
        private static final Map<BlockPos, SoundInstance> activeLoops = new HashMap<>();

        public static void handleSounds(PackAPunchBlockEntity be) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            
            BlockPos pos = be.getBlockPos();
            int state = be.getState();
            String desiredSound = state == 1 ? "pap_loop" : (state == 2 ? "pap_ready_loop" : null);

            if (desiredSound != null) {
                SoundInstance current = activeLoops.get(pos);
                ResourceLocation desiredLoc = new ResourceLocation("zombierool", desiredSound);
                
                if (current == null || !current.getLocation().equals(desiredLoc) || !mc.getSoundManager().isActive(current)) {
                    stopSounds(pos);
                    
                    SimpleSoundInstance newSound = new SimpleSoundInstance(
                        desiredLoc, 
                        SoundSource.BLOCKS, 1.0f, 1.0f,
                        net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom(),
                        true, 0, SoundInstance.Attenuation.LINEAR,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, false
                    );
                    mc.getSoundManager().play(newSound);
                    activeLoops.put(pos, newSound);
                }
            } else {
                stopSounds(pos);
            }
        }

        public static void stopSounds(BlockPos pos) {
            SoundInstance current = activeLoops.remove(pos);
            if (current != null) {
                Minecraft.getInstance().getSoundManager().stop(current);
            }
        }
    }
}
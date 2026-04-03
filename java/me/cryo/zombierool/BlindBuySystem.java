package me.cryo.zombierool.block.system;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.C2SSetBlindBuyConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.ChatFormatting;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BlindBuySystem {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("blind_buy_cabinet", BlindBuyCabinetBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("blind_buy_cabinet", () -> new BlockItem(BLOCK.get(), new Item.Properties()) {
        @Override
        public InteractionResult place(BlockPlaceContext context) {
            BlockPlaceContext offsetContext = new BlockPlaceContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos().above(), context.isInside()));
            return super.place(offsetContext);
        }
    });

    public static final RegistryObject<BlockEntityType<BlindBuyCabinetBlockEntity>> BE = BLOCK_ENTITIES.register("blind_buy_cabinet", () -> BlockEntityType.Builder.of(BlindBuyCabinetBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<BlindBuyManagerMenu>> MENU = MENUS.register("sys_blind_buy_manager", () -> IForgeMenuType.create(BlindBuyManagerMenu::new));

    private static final Map<Level, Set<BlindBuyCabinetBlockEntity>> CABINETS = new ConcurrentHashMap<>();

    public static void registerCabinet(Level level, BlindBuyCabinetBlockEntity be) {
        CABINETS.computeIfAbsent(level, k -> Collections.synchronizedSet(new HashSet<>())).add(be);
    }

    public static void unregisterCabinet(Level level, BlindBuyCabinetBlockEntity be) {
        Set<BlindBuyCabinetBlockEntity> set = CABINETS.get(level);
        if (set != null) {
            set.remove(be);
        }
    }

    public static void resetAllCabinets(Level level) {
        Set<BlindBuyCabinetBlockEntity> set = CABINETS.get(level);
        if (set != null) {
            for (BlindBuyCabinetBlockEntity be : set) {
                if (be.isRemoved()) continue;
                BlockState state = be.getBlockState();
                if (state.hasProperty(BlindBuyCabinetBlock.OPEN) && state.getValue(BlindBuyCabinetBlock.OPEN)) {
                    level.setBlock(be.getBlockPos(), state.setValue(BlindBuyCabinetBlock.OPEN, false), 3);
                }
            }
        }
    }

    static {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(new ResourceLocation(ZombieroolMod.MODID, "zombie_arsenal"))) {
            event.accept(ITEM.get());
        }
    }

    public static class BlindBuyCabinetBlock extends Block implements EntityBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
        protected static final VoxelShape SHAPE_NORTH = Shapes.block();
        protected static final VoxelShape SHAPE_SOUTH = Shapes.block();
        protected static final VoxelShape SHAPE_WEST  = Shapes.block();
        protected static final VoxelShape SHAPE_EAST  = Shapes.block();

        public BlindBuyCabinetBlock() {
            super(BlockBehaviour.Properties.of()
                .sound(SoundType.WOOD)
                .strength(2.0f, 3600000)
                .noOcclusion());
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            switch (state.getValue(FACING)) {
                case SOUTH: return SHAPE_SOUTH;
                case WEST:  return SHAPE_WEST;
                case EAST:  return SHAPE_EAST;
                case NORTH: default: return SHAPE_NORTH;
            }
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, OPEN);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
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
            return RenderShape.MODEL;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new BlindBuyCabinetBlockEntity(pos, state);
        }

        @Override
        public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (player.isCreative() && player.isShiftKeyDown()) {
                if (!world.isClientSide && player instanceof ServerPlayer sp) {
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof BlindBuyCabinetBlockEntity cabinet) {
                        NetworkHooks.openScreen(sp, cabinet, pos);
                    }
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
            return InteractionResult.PASS;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip.add(Component.translatable("block.zombierool.blind_buy.tooltip.1").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("block.zombierool.blind_buy.tooltip.2").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("block.zombierool.blind_buy.tooltip.3").withStyle(ChatFormatting.GRAY));
        }
    }

    public static class BlindBuyCabinetBlockEntity extends BlockEntity implements MenuProvider {
        private int price = 0;
        private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }
            }
        };
        private final LazyOptional<IItemHandler> optionalHandler = LazyOptional.of(() -> itemHandler);

        public BlindBuyCabinetBlockEntity(BlockPos pos, BlockState state) {
            super(BE.get(), pos, state);
        }

        public int getPrice() { return price; }
        public void setPrice(int price) {
            this.price = price;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        public ItemStack getWeapon() { return itemHandler.getStackInSlot(0); }
        public void triggerPurchase() { }

        @Override
        public void onLoad() {
            super.onLoad();
            if (level != null && !level.isClientSide) {
                BlindBuySystem.registerCabinet(level, this);
            }
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            if (level != null && !level.isClientSide) {
                BlindBuySystem.unregisterCabinet(level, this);
            }
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            this.price = tag.getInt("Price");
            this.itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putInt("Price", this.price);
            tag.put("Inventory", this.itemHandler.serializeNBT());
        }

        @Override
        public void handleUpdateTag(CompoundTag tag) {
            super.handleUpdateTag(tag);
            this.load(tag);
        }

        @Override
        public ClientboundBlockEntityDataPacket getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public CompoundTag getUpdateTag() {
            CompoundTag tag = super.getUpdateTag();
            saveAdditional(tag);
            return tag;
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Cabinet Configuration");
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return new BlindBuyManagerMenu(id, inventory, this);
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ITEM_HANDLER) return optionalHandler.cast();
            return super.getCapability(cap, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            optionalHandler.invalidate();
        }
    }

    public static class BlindBuyManagerMenu extends AbstractContainerMenu {
        public final BlindBuyCabinetBlockEntity blockEntity;

        public BlindBuyManagerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
            this(id, inv, (BlindBuyCabinetBlockEntity) inv.player.level().getBlockEntity(extraData.readBlockPos()));
        }

        public BlindBuyManagerMenu(int id, Inventory inv, BlindBuyCabinetBlockEntity blockEntity) {
            super(MENU.get(), id);
            this.blockEntity = blockEntity;

            blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                this.addSlot(new SlotItemHandler(handler, 0, 80, 24));
            });

            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 9; ++j) {
                    this.addSlot(new Slot(inv, j + i * 9 + 9, 20 + j * 18, 90 + i * 18));
                }
            }

            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(inv, k, 20 + k * 18, 148));
            }
        }

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            ItemStack itemstack = ItemStack.EMPTY;
            Slot slot = this.slots.get(index);

            if (slot != null && slot.hasItem()) {
                ItemStack itemstack1 = slot.getItem();
                itemstack = itemstack1.copy();

                if (index == 0) {
                    if (!this.moveItemStackTo(itemstack1, 1, 37, true)) return ItemStack.EMPTY;
                } else if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }

                if (itemstack1.isEmpty()) slot.set(ItemStack.EMPTY);
                else slot.setChanged();
            }

            return itemstack;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class BlindBuyManagerScreen extends me.cryo.zombierool.client.gui.UnifiedConfigScreen<BlindBuyManagerMenu> {
        private EditBox priceBox;

        public BlindBuyManagerScreen(BlindBuyManagerMenu menu, Inventory inv, Component title) {
            super(menu, inv, Component.literal("Mystery Cabinet"));
            this.imageWidth = 200;
            this.imageHeight = 175;
        }

        @Override
        protected void init() {
            super.init();
            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;

            priceBox = new EditBox(this.font, startX + 65, startY + 55, 60, 16, Component.empty());
            priceBox.setMaxLength(6);
            priceBox.setValue(String.valueOf(this.menu.blockEntity.getPrice()));

            this.addRenderableWidget(priceBox);

            this.addRenderableWidget(Button.builder(Component.literal("§aSave"), btn -> {
                try {
                    int price = Integer.parseInt(priceBox.getValue());
                    NetworkHandler.INSTANCE.sendToServer(new C2SSetBlindBuyConfigPacket(this.menu.blockEntity.getBlockPos(), price, 0));
                    this.minecraft.player.closeContainer();
                } catch (Exception e) {}
            }).bounds(startX + 130, startY + 53, 60, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(g);
            super.render(g, mouseX, mouseY, partialTick);
            this.priceBox.render(g, mouseX, mouseY, partialTick);
            this.renderTooltip(g, mouseX, mouseY);
        }

        @Override
        protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;

            g.fillGradient(startX, startY, startX + this.imageWidth, startY + this.imageHeight, 0xEE000000, 0xEE222222);
            g.renderOutline(startX, startY, this.imageWidth, this.imageHeight, 0xFFAA00);

            drawSlotBg(g, startX + 79, startY + 23);

            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 9; ++j) {
                    drawSlotBg(g, startX + 19 + j * 18, startY + 89 + i * 18);
                }
            }

            for (int k = 0; k < 9; ++k) {
                drawSlotBg(g, startX + 19 + k * 18, startY + 147);
            }
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            g.drawCenteredString(this.font, "§l" + this.title.getString(), this.imageWidth / 2, 8, 0xFFAA00);
            g.drawString(this.font, "Hidden Weapon:", 10, 28, 0xAAAAAA, false);
            g.drawString(this.font, "Price:", 20, 59, 0xAAAAAA, false);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class BlindBuyCabinetRenderer implements BlockEntityRenderer<BlindBuyCabinetBlockEntity> {
        public BlindBuyCabinetRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(BlindBuyCabinetBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
            boolean isOpen = entity.getBlockState().getValue(BlindBuyCabinetBlock.OPEN);
            Direction facing = entity.getBlockState().getValue(BlindBuyCabinetBlock.FACING);

            poseStack.pushPose();
            poseStack.translate(0.5, 0.0, 0.5);
            float yRot = -facing.toYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

            float offsetX = 0.0f;
            float offsetY = 9.0f / 16.0f;
            float offsetZ = -0.2f;

            poseStack.translate(offsetX, offsetY, offsetZ);

            if (!isOpen) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
                float textScale = 0.035f;
                poseStack.scale(-textScale, -textScale, textScale);

                Font font = Minecraft.getInstance().font;
                String text = "?";
                float textWidth = font.width(text);

                font.drawInBatch(text, -textWidth / 2f, -font.lineHeight / 2f, 0xFFFFFFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
                poseStack.popPose();
            } else {
                ItemStack weapon = entity.getWeapon();
                if (!weapon.isEmpty()) {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));
                    poseStack.scale(0.7f, 0.7f, 0.7f);

                    Minecraft.getInstance().getItemRenderer().renderStatic(
                        weapon, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, entity.getLevel(), 0
                    );

                    poseStack.popPose();
                }
            }

            poseStack.popPose();
        }
    }
}
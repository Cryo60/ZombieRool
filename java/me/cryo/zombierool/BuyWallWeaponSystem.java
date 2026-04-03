package me.cryo.zombierool.block.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.extensions.IForgeMenuType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.netty.buffer.Unpooled;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.client.gui.UnifiedConfigScreen;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.integration.TacZIntegration;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BuyWallWeaponSystem {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("buy_wall_weapon", BuyWallWeaponBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("buy_wall_weapon", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<BuyWallWeaponBlockEntity>> BE = BLOCK_ENTITIES.register("buy_wall_weapon", () -> BlockEntityType.Builder.of(BuyWallWeaponBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<WallWeaponManagerMenu>> MENU = MENUS.register("sys_wall_weapon_manager", () -> IForgeMenuType.create(WallWeaponManagerMenu::new));

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

    public static class BuyWallWeaponBlock extends MimicSystem.AbstractMimicBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final BooleanProperty HAS_MIMIC = BooleanProperty.create("has_mimic");

        public BuyWallWeaponBlock() {
            super(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.STONE).strength(-1, 3600000).isValidSpawn((bs, wg, pos, et) -> false).noOcclusion());
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_MIMIC, false));
        }

        @Override public boolean useShapeForLightOcclusion(BlockState state) { return true; }

        @Override public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
            super.appendHoverText(itemstack, world, list, flag);
            list.add(Component.translatable("block.zombierool.buy_wall_weapon.tooltip.1").withStyle(ChatFormatting.BLUE));
            list.add(Component.translatable("block.zombierool.buy_wall_weapon.tooltip.2").withStyle(ChatFormatting.GRAY));
            list.add(Component.translatable("block.zombierool.buy_wall_weapon.tooltip.3").withStyle(ChatFormatting.GRAY));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HAS_MIMIC); }

        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            for (Direction dir : context.getNearestLookingDirections()) {
                if (dir.getAxis().isHorizontal()) {
                    Direction opposite = dir.getOpposite();
                    BlockPos front = pos.relative(opposite);
                    if (level.getBlockState(front).isAir() && (level.getBlockState(front.below()).is(ZombieroolModBlocks.PATH.get()) || level.getBlockState(front.above()).is(ZombieroolModBlocks.PATH.get()))) {
                        return this.defaultBlockState().setValue(FACING, opposite);
                    }
                }
            }
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        public BlockState rotate(BlockState state, Rotation rot) { return state.setValue(FACING, rot.rotate(state.getValue(FACING))); }
        public BlockState mirror(BlockState state, Mirror mirrorIn) { return state.rotate(mirrorIn.getRotation(state.getValue(FACING))); }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return state.getValue(HAS_MIMIC) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
        }

        @Override
        public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!player.isCreative()) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            if (!held.isEmpty() && held.getItem() instanceof BlockItem bi) {
                Block blockToCopy = bi.getBlock();
                if (!(blockToCopy instanceof MimicSystem.IMimicBlock)) {
                    if (!world.isClientSide) {
                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof MimicSystem.IMimicContainer mimicContainer) {
                            BlockState placementState = MimicSystem.getStateForMimic(player, hand, held, hit, blockToCopy);
                            mimicContainer.setMimic(placementState);
                            player.displayClientMessage(Component.translatable("message.zombierool.texture_copied").withStyle(ChatFormatting.GREEN), true);
                        }
                    }
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }
            } else if (held.isEmpty()) {
                if (!world.isClientSide && player instanceof ServerPlayer sp) {
                    NetworkHooks.openScreen(sp, (MenuProvider) world.getBlockEntity(pos), pos);
                }
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
            return InteractionResult.PASS;
        }

        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BuyWallWeaponBlockEntity(pos, state); }

        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return (lvl, pos, st, be) -> { if (be instanceof BuyWallWeaponBlockEntity weapon) weapon.tick(lvl, pos, st); };
        }

        @Override public boolean isSignalSource(BlockState state) { return true; }
        @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
            if (level.getBlockEntity(pos) instanceof BuyWallWeaponBlockEntity be) return be.isEmitting() ? 15 : 0;
            return 0;
        }
        @Override
        public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
            return getSignal(state, level, pos, dir);
        }
    }

    public static class BuyWallWeaponBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer, MimicSystem.IMimicContainer, MenuProvider {
        private NonNullList<ItemStack> stacks = NonNullList.withSize(1, ItemStack.EMPTY);
        private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());
        private int price = 0;
        private ResourceLocation itemToSell = null;
        private BlockState mimicBlockState = null;
        private boolean orientationFixed = false;
        private int redstoneMode = 0;
        private int pulseTimer = 0;
        private boolean isEmitting = false;

        private static final Map<String, String> ID_MIGRATION_MAP = new HashMap<>();
        static {
            ID_MIGRATION_MAP.put("M1GarandItem", "m1garand");
            ID_MIGRATION_MAP.put("m_1_garand_weapon", "m1garand");
            ID_MIGRATION_MAP.put("RaygunWeaponItem", "raygun");
        }

        public BuyWallWeaponBlockEntity(BlockPos pos, BlockState state) { super(BE.get(), pos, state); }

        public void tick(Level level, BlockPos pos, BlockState state) {
            if (level.isClientSide) return;
            if (this.mimicBlockState != null && state.hasProperty(BuyWallWeaponBlock.HAS_MIMIC) && !state.getValue(BuyWallWeaponBlock.HAS_MIMIC)) {
                state = state.setValue(BuyWallWeaponBlock.HAS_MIMIC, true);
                level.setBlock(pos, state, 3);
            }
            if (!orientationFixed) {
                fixOrientation(level, pos, state);
                orientationFixed = true;
            }
            if (pulseTimer > 0) {
                pulseTimer--;
                if (pulseTimer == 0) {
                    isEmitting = false;
                    setChanged();
                    level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                }
            }
        }

        private void fixOrientation(Level level, BlockPos pos, BlockState state) {
            if (!(state.getBlock() instanceof BuyWallWeaponBlock)) return;
            Direction currentFacing = state.getValue(BuyWallWeaponBlock.FACING);
            BlockPos front = pos.relative(currentFacing);
            if (level.getBlockState(front).isAir() && (level.getBlockState(front.below()).is(ZombieroolModBlocks.PATH.get()) || level.getBlockState(front.above()).is(ZombieroolModBlocks.PATH.get()))) return;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (dir == currentFacing) continue;
                BlockPos f = pos.relative(dir);
                if (level.getBlockState(f).isAir() && (level.getBlockState(f.below()).is(ZombieroolModBlocks.PATH.get()) || level.getBlockState(f.above()).is(ZombieroolModBlocks.PATH.get()))) {
                    level.setBlock(pos, state.setValue(BuyWallWeaponBlock.FACING, dir), 3);
                    return;
                }
            }
        }

        public void triggerPurchase() {
            if (redstoneMode == 1) {
                isEmitting = true;
                setChanged();
                if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            } else if (redstoneMode == 2) {
                isEmitting = true;
                pulseTimer = 20;
                setChanged();
                if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }

        @Override public void load(CompoundTag nbt) {
            super.load(nbt);
            this.stacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(nbt, this.stacks);
            this.price = nbt.getInt("Price");
            this.redstoneMode = nbt.getInt("RedstoneMode");
            this.isEmitting = nbt.getBoolean("IsEmitting");
            this.pulseTimer = nbt.getInt("PulseTimer");
            if (nbt.contains("OrientationFixed")) this.orientationFixed = nbt.getBoolean("OrientationFixed");
            if (nbt.contains("ItemToSell", Tag.TAG_STRING)) {
                String fullId = nbt.getString("ItemToSell");
                if (ID_MIGRATION_MAP.containsKey(fullId)) fullId = "zombierool:" + ID_MIGRATION_MAP.get(fullId);
                ResourceLocation legacyRl = ResourceLocation.tryParse(fullId);
                this.itemToSell = legacyRl;
                if (legacyRl != null && ForgeRegistries.ITEMS.containsKey(legacyRl)) {
                    if (this.stacks.get(0).isEmpty()) this.stacks.set(0, new ItemStack(ForgeRegistries.ITEMS.getValue(legacyRl)));
                }
            }
            this.mimicBlockState = MimicSystem.loadMimic(nbt, this.level, "CapturedBlock", true);
        }

        @Override public void saveAdditional(CompoundTag nbt) {
            super.saveAdditional(nbt);
            ContainerHelper.saveAllItems(nbt, this.stacks);
            nbt.putInt("Price", this.price);
            nbt.putInt("RedstoneMode", this.redstoneMode);
            nbt.putBoolean("IsEmitting", this.isEmitting);
            nbt.putInt("PulseTimer", this.pulseTimer);
            nbt.putBoolean("OrientationFixed", this.orientationFixed);
            if (this.itemToSell != null) nbt.putString("ItemToSell", this.itemToSell.toString());
            MimicSystem.saveMimic(nbt, this.mimicBlockState);
        }

        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; setChanged(); }
        public int getRedstoneMode() { return redstoneMode; }
        public void setRedstoneMode(int mode) { this.redstoneMode = mode; setChanged(); }
        public boolean isEmitting() { return isEmitting; }

        public ResourceLocation getItemToSell() {
            ItemStack stack = this.stacks.get(0);
            if (stack.isEmpty() && this.itemToSell != null) return this.itemToSell;
            if (stack.isEmpty()) return null;
            return ForgeRegistries.ITEMS.getKey(stack.getItem());
        }

        public void setItemToSell(ResourceLocation itemToSell) {
            this.itemToSell = itemToSell;
            if (itemToSell != null) {
                Item item = ForgeRegistries.ITEMS.getValue(itemToSell);
                if (item != null && item != Items.AIR) this.stacks.set(0, new ItemStack(item));
            }
            setChanged();
        }

        @Override public BlockState getMimic() { return mimicBlockState; }
        @Override 
        public void setMimic(BlockState state) { 
            this.mimicBlockState = state; 
            if (this.level != null && this.worldPosition != null) {
                BlockState cur = this.level.getBlockState(this.worldPosition);
                if (cur.hasProperty(BuyWallWeaponBlock.HAS_MIMIC)) {
                    this.level.setBlock(this.worldPosition, cur.setValue(BuyWallWeaponBlock.HAS_MIMIC, state != null), 3);
                }
            }
            setChanged(); 
        }

        @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
        @Override public CompoundTag getUpdateTag() { return this.saveWithFullMetadata(); }
        @Override public int getContainerSize() { return stacks.size(); }
        @Override public boolean isEmpty() { return stacks.get(0).isEmpty(); }
        @Override public Component getDefaultName() { return Component.literal("buy_wall_weapon"); }
        @Override public int getMaxStackSize() { return 64; }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inv) { return new WallWeaponManagerMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition)); }
        @Override protected NonNullList<ItemStack> getItems() { return this.stacks; }
        @Override protected void setItems(NonNullList<ItemStack> stacks) { this.stacks = stacks; }
        @Override public boolean canPlaceItem(int index, ItemStack stack) { return true; }
        @Override public int[] getSlotsForFace(Direction side) { return IntStream.range(0, 1).toArray(); }
        @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) { return true; }
        @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) { return index != 0; }

        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
            if (!this.remove && facing != null && capability == ForgeCapabilities.ITEM_HANDLER) return handlers[facing.ordinal()].cast();
            return super.getCapability(capability, facing);
        }

        @Override public void setRemoved() { super.setRemoved(); for (LazyOptional<? extends IItemHandler> handler : handlers) handler.invalidate(); }
    }

    public static class WallWeaponManagerMenu extends AbstractContainerMenu {
        public final BlockPos pos;
        private ContainerLevelAccess access;
        private IItemHandler internal;
        public BlockEntity boundBlockEntity;
        private final ContainerData data;

        public WallWeaponManagerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
            super(MENU.get(), id);
            this.pos = extraData != null ? extraData.readBlockPos() : BlockPos.ZERO;
            this.access = ContainerLevelAccess.create(inv.player.level(), this.pos);
            this.internal = new ItemStackHandler(1);
            boundBlockEntity = inv.player.level().getBlockEntity(this.pos);
            if (boundBlockEntity != null) {
                boundBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(cap -> this.internal = cap);
            }
            this.data = new ContainerData() {
                @Override public int get(int index) {
                    if (boundBlockEntity instanceof BuyWallWeaponBlockEntity be) {
                        return index == 0 ? be.getPrice() : be.getRedstoneMode();
                    }
                    return 0;
                }
                @Override public void set(int index, int value) {
                    if (boundBlockEntity instanceof BuyWallWeaponBlockEntity be) {
                        if (index == 0) be.setPrice(value);
                        else be.setRedstoneMode(value);
                    }
                }
                @Override public int getCount() { return 2; }
            };
            this.addDataSlots(data);
            this.addSlot(new SlotItemHandler(internal, 0, 120, 20));
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 9; ++j) {
                    this.addSlot(new Slot(inv, j + i * 9 + 9, 49 + j * 18, 125 + i * 18));
                }
            }
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(inv, k, 49 + k * 18, 183));
            }
        }

        public int getConfiguredPrice() { return this.data.get(0); }
        public int getRedstoneMode() { return this.data.get(1); }

        @Override public boolean stillValid(Player player) { return stillValid(this.access, player, player.level().getBlockState(pos).getBlock()); }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            ItemStack result = ItemStack.EMPTY;
            Slot slot = this.slots.get(index);
            if (slot != null && slot.hasItem()) {
                ItemStack stackInSlot = slot.getItem();
                result = stackInSlot.copy();
                if (index < 1) {
                    if (!this.moveItemStackTo(stackInSlot, 1, this.slots.size(), true)) return ItemStack.EMPTY;
                } else if (!this.moveItemStackTo(stackInSlot, 0, 1, false)) return ItemStack.EMPTY;
                if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            }
            return result;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (player instanceof ServerPlayer) {
                for (int j = 1; j < internal.getSlots(); ++j) {
                    ItemStack extracted = internal.extractItem(j, internal.getStackInSlot(j).getCount(), false);
                    if (!extracted.isEmpty()) player.drop(extracted, false);
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WallWeaponManagerScreen extends UnifiedConfigScreen<WallWeaponManagerMenu> {
        private EditBox priceBox;
        private int currentRedstoneMode = 0;

        public WallWeaponManagerScreen(WallWeaponManagerMenu menu, Inventory inv, Component title) {
            super(menu, inv, Component.literal("Wall Weapon"));
        }

        @Override
        protected void init() {
            super.init();
            currentRedstoneMode = this.menu.getRedstoneMode();
            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;
            priceBox = new EditBox(this.font, startX + 80, startY + 40, 60, 16, Component.empty());
            priceBox.setMaxLength(6);
            if (this.menu.getConfiguredPrice() > 0) priceBox.setValue(String.valueOf(this.menu.getConfiguredPrice()));
            this.addRenderableWidget(priceBox);

            this.addRenderableWidget(CycleButton.builder((Integer mode) -> Component.literal(mode == 1 ? "CONTINUOUS" : mode == 2 ? "PULSE" : "OFF"))
                .withValues(0, 1, 2).withInitialValue(currentRedstoneMode)
                .create(startX + 80, startY + 65, 100, 20, Component.literal("Redstone: "), (btn, val) -> currentRedstoneMode = val));

            this.addRenderableWidget(Button.builder(Component.literal("§aSave"), btn -> {
                int p = 0; 
                try { p = Integer.parseInt(priceBox.getValue()); } catch(Exception e){}
                NetworkHandler.INSTANCE.sendToServer(new C2SSetWallWeaponConfigPacket(this.menu.pos, p, currentRedstoneMode, this.menu.getSlot(0).getItem()));
                this.minecraft.player.closeContainer();
            }).bounds(startX + 80, startY + 100, 100, 20).build());
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            g.drawString(this.font, "Item:", 10, 24, 0xAAAAAA, false);
            g.drawString(this.font, "Price:", 10, 44, 0xAAAAAA, false);
        }
    }

    public record C2SSetWallWeaponConfigPacket(BlockPos pos, int price, int redstoneMode, ItemStack stack) {
        public static void encode(C2SSetWallWeaponConfigPacket pkt, FriendlyByteBuf buf) { 
            buf.writeBlockPos(pkt.pos()); buf.writeInt(pkt.price()); buf.writeInt(pkt.redstoneMode()); buf.writeItem(pkt.stack()); 
        }
        public static C2SSetWallWeaponConfigPacket decode(FriendlyByteBuf buf) { 
            return new C2SSetWallWeaponConfigPacket(buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readItem()); 
        }
        public static void handle(C2SSetWallWeaponConfigPacket pkt, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> { 
                ServerPlayer s = ctx.get().getSender(); 
                if (s != null && s.hasPermissions(2) && s.level().getBlockEntity(pkt.pos()) instanceof BuyWallWeaponBlockEntity be) { 
                    be.setPrice(pkt.price()); 
                    be.setRedstoneMode(pkt.redstoneMode()); 
                    be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(cap -> { 
                        if (cap instanceof ItemStackHandler h) h.setStackInSlot(0, pkt.stack().copy()); 
                    }); 
                    be.setChanged(); 
                    s.level().sendBlockUpdated(pkt.pos(), be.getBlockState(), be.getBlockState(), 3); 
                }
            }); 
            ctx.get().setPacketHandled(true);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class BuyWallWeaponRenderer implements BlockEntityRenderer<BuyWallWeaponBlockEntity> {
        private static final Map<ResourceLocation, ResourceLocation> OUTLINE_CACHE = new HashMap<>();
        private static final Map<UUID, Set<String>> PURCHASED_ITEMS = new HashMap<>();
        private static int textureCounter = 0;
        private static final Set<ResourceLocation> FAILED_TEXTURES = new HashSet<>();

        public BuyWallWeaponRenderer(BlockEntityRendererProvider.Context context) {}

        private static class ColorOverrideVertexConsumer implements VertexConsumer {
            private final VertexConsumer delegate;
            private final int r, g, b, a;
            public ColorOverrideVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
                this.delegate = delegate; this.r = r; this.g = g; this.b = b; this.a = a;
            }
            @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
            @Override public VertexConsumer color(int red, int green, int blue, int alpha) { delegate.color(r, g, b, a); return this; }
            @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
            @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
            @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
            @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
            @Override public void endVertex() { delegate.endVertex(); }
            @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(this.r, this.g, this.b, this.a); }
            @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
        }

        @Override
        public void render(BuyWallWeaponBlockEntity entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
            Direction face = entity.getBlockState().getValue(BuyWallWeaponBlock.FACING);
            BlockPos frontPos = entity.getBlockPos().relative(face);
            int frontLight = LevelRenderer.getLightColor(entity.getLevel(), frontPos);

            BlockState mimicState = entity.getMimic();
            if (mimicState != null) {
                poseStack.pushPose();
                MimicSystem.renderMimic(mimicState, entity.getBlockPos(), entity.getLevel(), poseStack, buffer, frontLight, combinedOverlay);
                poseStack.popPose();
            }

            ItemStack stack = ItemStack.EMPTY;
            var opt = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve();
            if (opt.isPresent()) {
                stack = opt.get().getStackInSlot(0);
            } else if (entity.getItemToSell() != null) {
                var item = ForgeRegistries.ITEMS.getValue(entity.getItemToSell());
                if (item != null && item != Items.AIR) {
                    stack = new ItemStack(item);
                }
            }

            if (!stack.isEmpty()) {
                Player player = Minecraft.getInstance().player;
                boolean hasPurchased = player != null && hasPlayerPurchased(player, stack);

                BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.getLevel(), null, 0);
                TextureAtlasSprite sprite = model.getParticleIcon();
                ResourceLocation originalTexture = sprite.contents().name();

                boolean isTacz = WeaponFacade.isTaczWeapon(stack);
                boolean isMissingTexture = originalTexture.getPath().equals("missingno") || isTacz;

                if (stack.getItem() instanceof me.cryo.zombierool.item.throwable.ThrowableCore.BaseThrowableItem) {
                    isMissingTexture = true; 
                } else if (isTacz) {
                    String gunIdStr = stack.getOrCreateTag().getString("GunId");
                    if (!gunIdStr.isEmpty()) {
                        ResourceLocation gunId = new ResourceLocation(gunIdStr);
                        originalTexture = TacZIntegration.getGunIcon(gunId);
                        isMissingTexture = false;
                    }
                } else if (isMissingTexture) {
                    ResourceLocation itemReg = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (itemReg != null) {
                        originalTexture = new ResourceLocation(itemReg.getNamespace(), "item/weapons/" + itemReg.getPath());
                        isMissingTexture = false;
                    }
                }

                ResourceLocation outlineTexture = null;
                ResourceLocation fullTexture = null;

                if (!isMissingTexture) {
                    outlineTexture = getOrCreateOutline(originalTexture);
                    fullTexture = hasPurchased ? getOrCreateFullTexture(originalTexture) : null;
                }

                poseStack.pushPose();

                double surfaceDepth = 0.5;
                if (mimicState != null) {
                    VoxelShape shape = MimicSystem.getMimicShape(mimicState, entity.getLevel(), entity.getBlockPos(), CollisionContext.of(Minecraft.getInstance().player));
                    if (!shape.isEmpty()) {
                        AABB bounds = shape.bounds();
                        switch (face) {
                            case NORTH -> surfaceDepth = 0.5 - bounds.minZ;
                            case SOUTH -> surfaceDepth = bounds.maxZ - 0.5;
                            case WEST  -> surfaceDepth = 0.5 - bounds.minX;
                            case EAST  -> surfaceDepth = bounds.maxX - 0.5;
                        }
                    }
                }

                double offset = surfaceDepth + 0.003;
                double x = 0.5 + face.getStepX() * offset;
                double y = 0.5;
                double z = 0.5 + face.getStepZ() * offset;

                poseStack.translate(x, y, z);
                float angle = switch(face) {
                    case NORTH -> 180f;
                    case SOUTH -> 0f;
                    case WEST  -> 90f;
                    case EAST  -> -90f;
                    default    -> 0f;
                };
                poseStack.mulPose(Axis.YP.rotationDegrees(angle));

                if (outlineTexture == null) {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));
                    float scaleXY = 0.5f;
                    float scaleZ = hasPurchased ? 0.05f : 0.001f;
                    poseStack.scale(scaleXY, scaleXY, scaleZ);

                    MultiBufferSource finalBuffer = buffer;
                    if (!hasPurchased) {
                        finalBuffer = rt -> new ColorOverrideVertexConsumer(buffer.getBuffer(rt), 255, 255, 255, 255);
                    }

                    Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack, ItemDisplayContext.FIXED, frontLight, combinedOverlay, poseStack, finalBuffer, entity.getLevel(), 0
                    );
                    poseStack.popPose();
                } else {
                    float s = 0.6f;
                    poseStack.scale(s, s, 0.01f);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));

                    if (hasPurchased && fullTexture != null) {
                        poseStack.pushPose();
                        poseStack.translate(0, 0, -0.002);
                        renderChalkQuad(poseStack, buffer, outlineTexture, frontLight, 0, 1, 0, 1);
                        poseStack.popPose();

                        poseStack.pushPose();
                        poseStack.scale(0.95f, 0.95f, 1.0f);
                        poseStack.translate(0, 0, 0.002);
                        renderChalkQuad(poseStack, buffer, fullTexture, frontLight, 0, 1, 0, 1);
                        poseStack.popPose();
                    } else {
                        renderChalkQuad(poseStack, buffer, outlineTexture, frontLight, 0, 1, 0, 1);
                    }
                }
                poseStack.popPose();
            }
        }

        private void renderChalkQuad(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, int light, float u0, float u1, float v0, float v1) {
            if (texture == null) return;
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
            Matrix4f matrix = poseStack.last().pose();
            float size = 0.5f;
            int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

            consumer.vertex(matrix, -size, -size, 0).color(255, 255, 255, 255).uv(u1, v1).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, size, -size, 0).color(255, 255, 255, 255).uv(u0, v1).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, size, size, 0).color(255, 255, 255, 255).uv(u0, v0).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, -size, size, 0).color(255, 255, 255, 255).uv(u1, v0).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
        }

        public static void markAsPurchased(Player player, String weaponId) {
            PURCHASED_ITEMS.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(weaponId);
        }

        private static boolean hasPlayerPurchased(Player player, ItemStack weaponOnWall) {
            String wId = WeaponFacade.getWeaponId(weaponOnWall);
            Set<String> purchased = PURCHASED_ITEMS.get(player.getUUID());
            if (purchased != null && purchased.contains(wId)) return true;

            boolean isTacz = WeaponFacade.isTaczWeapon(weaponOnWall);
            me.cryo.zombierool.core.system.WeaponSystem.Definition def = WeaponFacade.getDefinition(weaponOnWall);

            for (ItemStack s : player.getInventory().items) {
                if (isTacz && WeaponFacade.isTaczWeapon(s)) {
                    if (weaponOnWall.getOrCreateTag().getString("GunId").equals(s.getOrCreateTag().getString("GunId"))) return true;
                } else if (!isTacz && def != null && WeaponFacade.isWeapon(s)) {
                    me.cryo.zombierool.core.system.WeaponSystem.Definition d = WeaponFacade.getDefinition(s);
                    String defId = def.id != null ? def.id.replace("zombierool:", "") : "";
                    String dId = d != null && d.id != null ? d.id.replace("zombierool:", "") : "";
                    if (!dId.isEmpty() && dId.equals(defId)) return true;
                } else if (!isTacz && def == null && s.getItem() == weaponOnWall.getItem()) {
                    return true;
                }
            }
            return false;
        }

        public static void clearAllPurchases() { PURCHASED_ITEMS.clear(); }

        @Nullable
        private static ResourceLocation getOrCreateOutline(ResourceLocation originalTexture) {
            if (OUTLINE_CACHE.containsKey(originalTexture)) return OUTLINE_CACHE.get(originalTexture);
            if (FAILED_TEXTURES.contains(originalTexture)) return null;

            try {
                com.mojang.blaze3d.platform.NativeImage original = loadTexture(originalTexture);
                if (original == null) { FAILED_TEXTURES.add(originalTexture); return null; }

                com.mojang.blaze3d.platform.NativeImage outline = createChalkOutline(original);
                DynamicTexture dynamicTexture = new DynamicTexture(outline);
                ResourceLocation outlineLocation = new ResourceLocation("zombierool", "dynamic/chalk_outline_" + textureCounter++);
                Minecraft.getInstance().getTextureManager().register(outlineLocation, dynamicTexture);

                OUTLINE_CACHE.put(originalTexture, outlineLocation);
                original.close();
                return outlineLocation;
            } catch (Exception e) {
                FAILED_TEXTURES.add(originalTexture);
                return null;
            }
        }

        @Nullable
        private static ResourceLocation getOrCreateFullTexture(ResourceLocation originalTexture) {
            String cacheKey = originalTexture.toString() + "_full";
            ResourceLocation lookupKey = new ResourceLocation("zombierool", cacheKey.replace(":", "_").replace("/", "_"));

            if (OUTLINE_CACHE.containsKey(lookupKey)) return OUTLINE_CACHE.get(lookupKey);
            if (FAILED_TEXTURES.contains(originalTexture)) return null;

            try {
                com.mojang.blaze3d.platform.NativeImage original = loadTexture(originalTexture);
                if (original == null) { FAILED_TEXTURES.add(originalTexture); return null; }

                int width = original.getWidth();
                int height = original.getHeight();
                int maxSize = Math.max(width, height);
                com.mojang.blaze3d.platform.NativeImage squared = new com.mojang.blaze3d.platform.NativeImage(maxSize, maxSize, true);

                for (int y = 0; y < maxSize; y++) for (int x = 0; x < maxSize; x++) squared.setPixelRGBA(x, y, 0);

                int offsetX = (maxSize - width) / 2;
                int offsetY = (maxSize - height) / 2;

                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) squared.setPixelRGBA(x + offsetX, y + offsetY, original.getPixelRGBA(x, y));

                DynamicTexture dynamicTexture = new DynamicTexture(squared);
                ResourceLocation fullLocation = new ResourceLocation("zombierool", "dynamic/full_texture_" + textureCounter++);
                Minecraft.getInstance().getTextureManager().register(fullLocation, dynamicTexture);

                OUTLINE_CACHE.put(lookupKey, fullLocation);
                original.close();
                return fullLocation;
            } catch (Exception e) {
                FAILED_TEXTURES.add(originalTexture);
                return null;
            }
        }

        @Nullable
        private static com.mojang.blaze3d.platform.NativeImage loadTexture(ResourceLocation location) {
            try {
                var rm = Minecraft.getInstance().getResourceManager();
                String namespace = location.getNamespace();
                String cleanPath = location.getPath().replace("gun/icon/", "").replace("textures/", "").replace(".png", "");

                if (cleanPath.startsWith("icon/")) cleanPath = cleanPath.substring(5);

                Set<ResourceLocation> attempts = new HashSet<>(List.of(
                    new ResourceLocation(namespace, "textures/gun/icon/" + cleanPath + ".png"),
                    new ResourceLocation(namespace, "textures/icon/" + cleanPath + ".png"),
                    new ResourceLocation(namespace, "textures/item/" + cleanPath + ".png"),
                    new ResourceLocation(namespace, "textures/" + cleanPath + ".png"),
                    new ResourceLocation(namespace, cleanPath + ".png"),
                    new ResourceLocation(namespace, "gun/icon/" + cleanPath + ".png")
                ));

                for (ResourceLocation p : attempts) {
                    var res = rm.getResource(p);
                    if (res.isPresent()) {
                        try (InputStream stream = res.get().open()) { return com.mojang.blaze3d.platform.NativeImage.read(stream); } catch (Exception e) {}
                    }
                }
            } catch (Exception e) {}
            return null;
        }

        private static com.mojang.blaze3d.platform.NativeImage createChalkOutline(com.mojang.blaze3d.platform.NativeImage original) {
            int width = original.getWidth(), height = original.getHeight();
            int thickness = Math.max(1, width / 64);
            int maxSize = Math.max(width, height);

            com.mojang.blaze3d.platform.NativeImage outline = new com.mojang.blaze3d.platform.NativeImage(maxSize, maxSize, true);
            int offsetX = (maxSize - width) / 2, offsetY = (maxSize - height) / 2;

            for (int y = 0; y < maxSize; y++) for (int x = 0; x < maxSize; x++) outline.setPixelRGBA(x, y, 0);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (((original.getPixelRGBA(x, y) >> 24) & 0xFF) > 0) {
                        boolean isEdge = false;
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;
                                int nx = x + dx, ny = y + dy;
                                if (nx < 0 || nx >= width || ny < 0 || ny >= height || ((original.getPixelRGBA(nx, ny) >> 24) & 0xFF) == 0) { isEdge = true; break; }
                            }
                            if (isEdge) break;
                        }

                        if (isEdge) {
                            int halfThickness = thickness / 2;
                            for (int dy = -halfThickness; dy <= halfThickness; dy++) {
                                for (int dx = -halfThickness; dx <= halfThickness; dx++) {
                                    int nx = x + offsetX + dx, ny = y + offsetY + dy;
                                    if (nx >= 0 && nx < maxSize && ny >= 0 && ny < maxSize) outline.setPixelRGBA(nx, ny, 0xFFFFFFFF);
                                }
                            }
                        }
                    }
                }
            }
            return outline;
        }
    }
}
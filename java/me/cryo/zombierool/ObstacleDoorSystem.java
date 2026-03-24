package me.cryo.zombierool.block.system;
import io.netty.buffer.Unpooled;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.client.gui.UnifiedConfigScreen;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.C2SObstacleDoorGUIPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.LevelRenderer;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ObstacleDoorSystem {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("obstacle_door", ObstacleDoorBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("obstacle_door", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ObstacleDoorBlockEntity>> BE = BLOCK_ENTITIES.register("obstacle_door", () -> BlockEntityType.Builder.of(ObstacleDoorBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<ObstacleDoorManagerMenu>> MENU = MENUS.register("sys_obstacle_door_manager", () -> IForgeMenuType.create(ObstacleDoorManagerMenu::new));

    public static final Set<ObstacleDoorBlockEntity> OBSTACLES = ConcurrentHashMap.newKeySet();

    static {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus);
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(new ResourceLocation(ZombieroolMod.MODID, "zb_rct"))) {
            event.accept(ITEM.get());
        }
    }

    public static void handlePacket(ServerPlayer player, C2SObstacleDoorGUIPacket message) {
        Level world = player.level();
        BlockPos pos = new BlockPos(message.x(), message.y(), message.z());
        if (!world.isClientSide()) {
            if (message.isCreative()) {
                if (world.getBlockEntity(pos) instanceof ObstacleDoorBlockEntity be) {
                    List<BlockPos> connectedBlocks = new ArrayList<>();
                    Set<BlockPos> checked = new HashSet<>();
                    findAllConnectedBlocks(world, pos, connectedBlocks, checked);
                    for (BlockPos blockPos : connectedBlocks) {
                        if (world.getBlockEntity(blockPos) instanceof ObstacleDoorBlockEntity memberBe) {
                            memberBe.setPrix(message.prix());
                            memberBe.setCanal(message.canal());
                            memberBe.setChanged();
                            world.sendBlockUpdated(blockPos, world.getBlockState(blockPos), world.getBlockState(blockPos), 3);
                        }
                    }
                    player.sendSystemMessage(Component.literal("Configuration saved on " + connectedBlocks.size() + " blocks!"));
                }
            } else {
                if (message.prix() <= 0) {
                    player.sendSystemMessage(Component.literal("Invalid price!"));
                    return;
                }
                List<BlockPos> connectedBlocks = new ArrayList<>();
                Set<BlockPos> checked = new HashSet<>();
                findAllConnectedBlocks(world, pos, connectedBlocks, checked);

                int totalCost = message.prix();
                if (PointManager.getScore(player) < totalCost) {
                    player.sendSystemMessage(Component.literal("Not enough points: " + totalCost));
                    return;
                }
                PointManager.modifyScore(player, -totalCost);
                for (BlockPos blockPos : connectedBlocks) {
                    world.setBlock(blockPos, ZombieroolModBlocks.PATH.get().defaultBlockState(), 3);
                }
                world.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    private static void findAllConnectedBlocks(Level world, BlockPos pos, List<BlockPos> found, Set<BlockPos> checked) {
        if (checked.contains(pos) || !(world.getBlockState(pos).getBlock() instanceof ObstacleDoorBlock)) return;
        checked.add(pos); found.add(pos);
        for (Direction dir : Direction.values()) findAllConnectedBlocks(world, pos.relative(dir), found, checked);
    }

    public static class ObstacleDoorBlock extends FenceBlock implements EntityBlock, MimicSystem.IMimicBlock {
        public static final BooleanProperty CONN_UP = BooleanProperty.create("conn_up");
        public static final BooleanProperty CONN_DOWN = BooleanProperty.create("conn_down");
        public static final BooleanProperty HAS_COPIED_BLOCK = BooleanProperty.create("has_copied_block");

        public ObstacleDoorBlock() {
            super(BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.METAL).strength(-1, 3600000).noOcclusion().isRedstoneConductor((bs, br, bp) -> false).dynamicShape().forceSolidOn().randomTicks().isViewBlocking((state, world, pos) -> false).isSuffocating((state, world, pos) -> false));
            this.registerDefaultState(this.stateDefinition.any().setValue(CONN_UP, false).setValue(CONN_DOWN, false).setValue(HAS_COPIED_BLOCK, false));
        }

        @Override public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) { return 0; }
        @Override public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }

        @Override public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
            if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer c && c.getMimic() != null) return c.getMimic().propagatesSkylightDown(world, pos);
            return true;
        }

        @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer c && c.getMimic() != null) {
                VoxelShape shape = MimicSystem.getMimicShape(c.getMimic(), world, pos, context);
                if (shape != null) return shape;
            }
            return super.getShape(state, world, pos, context);
        }

        @Override public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer c && c.getMimic() != null) {
                VoxelShape shape = MimicSystem.getMimicVisualShape(c.getMimic(), world, pos, context);
                if (shape != null) return shape;
            }
            return super.getVisualShape(state, world, pos, context);
        }

        @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            if (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof Player p && p.isCreative()) return Shapes.empty();
            if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer c && c.getMimic() != null) {
                VoxelShape shape = MimicSystem.getMimicCollisionShape(c.getMimic(), world, pos, context);
                if (shape != null) return shape;
            }
            return Shapes.block();
        }

        @Override public FluidState getFluidState(BlockState state) { return Fluids.EMPTY.defaultFluidState(); }

        @Override public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
            super.appendHoverText(itemstack, world, list, flag);
            list.add(Component.translatable("block.zombierool.obstacle_door.tooltip.1").withStyle(ChatFormatting.BLUE));
            list.add(Component.translatable("block.zombierool.obstacle_door.tooltip.2").withStyle(ChatFormatting.GRAY));
            list.add(Component.translatable("block.zombierool.obstacle_door.tooltip.3").withStyle(ChatFormatting.GRAY));
        }

        @Override public RenderShape getRenderShape(BlockState state) { return state.getValue(HAS_COPIED_BLOCK) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL; }
        @Override public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) { return adjacent.getBlock() == this; }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(CONN_UP, CONN_DOWN, HAS_COPIED_BLOCK); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ObstacleDoorBlockEntity(pos, state); }

        private BlockState updateVerticalConnections(BlockState state, LevelAccessor level, BlockPos pos) {
            return state.setValue(CONN_UP, level.getBlockState(pos.above()).getBlock() instanceof ObstacleDoorBlock)
                        .setValue(CONN_DOWN, level.getBlockState(pos.below()).getBlock() instanceof ObstacleDoorBlock);
        }

        @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
            state = updateVerticalConnections(state, world, pos);
            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }

        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            return updateVerticalConnections(super.getStateForPlacement(context), context.getLevel(), context.getClickedPos()).setValue(HAS_COPIED_BLOCK, false);
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

        @Override public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, Mob mob) { return BlockPathTypes.BLOCKED; }
    }

    public static class ObstacleDoorBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer, MimicSystem.IMimicContainer {
        private net.minecraft.core.NonNullList<ItemStack> stacks = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
        private BlockState mimicBlockState = null;
        private int prix = 0;
        private String canal = "";

        public ObstacleDoorBlockEntity(BlockPos pos, BlockState state) {
            super(BE.get(), pos, state);
        }

        @Override 
        public void onLoad() {
            super.onLoad();
            if (this.level != null && !this.level.isClientSide) {
                OBSTACLES.add(this);
                BlockState cur = this.level.getBlockState(this.worldPosition);
                if (this.mimicBlockState != null && cur.hasProperty(ObstacleDoorBlock.HAS_COPIED_BLOCK) && !cur.getValue(ObstacleDoorBlock.HAS_COPIED_BLOCK)) {
                    this.level.setBlock(this.worldPosition, cur.setValue(ObstacleDoorBlock.HAS_COPIED_BLOCK, true), 3);
                }
            }
        }

        @Override
        public void setRemoved() {
            super.setRemoved();
            if (this.level != null && !this.level.isClientSide) {
                OBSTACLES.remove(this);
            }
        }

        public int getPrix() { return this.prix; }
        public void setPrix(int prix) { this.prix = prix; }
        public String getCanal() { return this.canal; }
        public void setCanal(String canal) { this.canal = canal; }

        @Override public BlockState getMimic() { return mimicBlockState; }
        @Override public void setMimic(@Nullable BlockState state) {
            this.mimicBlockState = state;
            if (this.level != null && this.worldPosition != null) {
                BlockState cur = this.level.getBlockState(this.worldPosition);
                if (cur.hasProperty(ObstacleDoorBlock.HAS_COPIED_BLOCK)) {
                    BlockState newState = cur.setValue(ObstacleDoorBlock.HAS_COPIED_BLOCK, state != null);
                    this.level.setBlock(this.worldPosition, newState, 3);
                    this.level.sendBlockUpdated(this.worldPosition, cur, newState, 3);
                }
            }
            setChanged();
        }

        public int getCanalAsInt() { try { return Integer.parseInt(this.canal); } catch(Exception e) { return 0; } }

        @Override public void load(CompoundTag compound) {
            super.load(compound);
            this.prix = compound.getInt("Prix");
            this.canal = compound.getString("Canal");
            this.mimicBlockState = MimicSystem.loadMimic(compound, this.level, "CopiedBlockId", true);
        }

        @Override protected void saveAdditional(CompoundTag compound) {
            super.saveAdditional(compound);
            compound.putInt("Prix", this.prix);
            compound.putString("Canal", this.canal);
            MimicSystem.saveMimic(compound, this.mimicBlockState);
        }

        @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
        @Override public CompoundTag getUpdateTag() { return this.saveWithFullMetadata(); }
        @Override public int getContainerSize() { return stacks.size(); }
        @Override public boolean isEmpty() { for (ItemStack s : this.stacks) if (!s.isEmpty()) return false; return true; }
        @Override public Component getDefaultName() { return Component.literal("obstacle_door"); }
        @Override public int getMaxStackSize() { return 64; }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inv) { return new ObstacleDoorManagerMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition)); }
        @Override protected net.minecraft.core.NonNullList<ItemStack> getItems() { return this.stacks; }
        @Override protected void setItems(net.minecraft.core.NonNullList<ItemStack> stacks) { this.stacks = stacks; }
        @Override public boolean canPlaceItem(int index, ItemStack stack) { return true; }
        @Override public int[] getSlotsForFace(Direction side) { return IntStream.range(0, this.getContainerSize()).toArray(); }
        @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return true; }
        @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return true; }
    }

    public static class ObstacleDoorManagerMenu extends AbstractContainerMenu {
        public final BlockPos pos;
        public ObstacleDoorManagerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
            super(MENU.get(), id);
            this.pos = extraData != null ? extraData.readBlockPos() : BlockPos.ZERO;
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ObstacleDoorManagerScreen extends UnifiedConfigScreen<ObstacleDoorManagerMenu> {
        private EditBox prix_input, canal_input;

        public ObstacleDoorManagerScreen(ObstacleDoorManagerMenu menu, Inventory inv, Component title) {
            super(menu, inv, Component.literal("Obstacle Door"));
        }

        @Override protected void init() {
            super.init();
            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;

            prix_input = new EditBox(this.font, startX + 125, startY + 30, 105, 20, Component.empty());
            canal_input = new EditBox(this.font, startX + 125, startY + 70, 105, 20, Component.empty());
            prix_input.setMaxLength(10);
            canal_input.setMaxLength(10);

            if (this.minecraft.level.getBlockEntity(this.menu.pos) instanceof ObstacleDoorBlockEntity be) {
                prix_input.setValue(String.valueOf(be.getPrix()));
                canal_input.setValue(be.getCanal());
            }

            this.addRenderableWidget(prix_input);
            this.addRenderableWidget(canal_input);
            this.addRenderableWidget(Button.builder(Component.literal("§aSave"), btn -> {
                try {
                    int p = Integer.parseInt(prix_input.getValue());
                    NetworkHandler.INSTANCE.sendToServer(new C2SObstacleDoorGUIPacket(this.menu.pos.getX(), this.menu.pos.getY(), this.menu.pos.getZ(), p, canal_input.getValue(), true));
                    this.minecraft.setScreen(null);
                } catch (Exception e){}
            }).bounds(startX + 80, startY + 110, 100, 20).build());
        }

        @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
            super.renderLabels(g, mx, my);
            g.drawString(this.font, "Price:", 30, 36, 0xAAAAAA, false);
            g.drawString(this.font, "Req. Channel:", 30, 76, 0xAAAAAA, false);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ObstacleDoorBlockRenderer implements BlockEntityRenderer<ObstacleDoorBlockEntity> {
        public ObstacleDoorBlockRenderer(BlockEntityRendererProvider.Context context) {}

        @Override public void render(ObstacleDoorBlockEntity entity, float pt, PoseStack ps, MultiBufferSource buf, int light, int ov) {
            if (entity.getMimic() != null) {
                ps.pushPose();
                MimicSystem.renderMimic(entity.getMimic(), entity.getBlockPos(), entity.getLevel(), ps, buf, LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos()), ov);
                ps.popPose();
            }
        }
    }
}
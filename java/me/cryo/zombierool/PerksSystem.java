package me.cryo.zombierool.block.system;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.netty.buffer.Unpooled;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.client.gui.UnifiedConfigScreen;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.C2SSavePerksConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PerksSystem {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register("perks_a_cola", PerksAColaBlock::new);
    public static final RegistryObject<Block> DUMMY_BLOCK = BLOCKS.register("perks_a_cola_dummy", PerksAColaDummyBlock::new);
    public static final RegistryObject<Item> ITEM = ITEMS.register("perks_a_cola", () -> new BlockItem(BLOCK.get(), new Item.Properties()) {
        @Override
        public InteractionResult place(BlockPlaceContext context) {
            BlockPlaceContext offsetContext = new BlockPlaceContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos().above(), context.isInside()));
            return super.place(offsetContext);
        }
    });

    public static final RegistryObject<BlockEntityType<PerksAColaBlockEntity>> BE = BLOCK_ENTITIES.register("perks_a_cola", () -> BlockEntityType.Builder.of(PerksAColaBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<PerksInterfaceMenu>> MENU = MENUS.register("sys_perks_interface", () -> IForgeMenuType.create(PerksInterfaceMenu::new));

    public static final RegistryObject<Block> LEGACY_PERKS_LOWER = BLOCKS.register("perks_lower", LegacyPerkBlock::new);
    public static final RegistryObject<Block> LEGACY_PERKS_UPPER = BLOCKS.register("perks_upper", LegacyUpperPerkBlock::new);
    public static final RegistryObject<BlockEntityType<LegacyPerkBE>> LEGACY_PERKS_BE = BLOCK_ENTITIES.register("perks_lower", () -> BlockEntityType.Builder.of(LegacyPerkBE::new, LEGACY_PERKS_LOWER.get()).build(null));

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

    public enum PerkType implements StringRepresentable {
        NONE("none"), JUGGERNOG("juggernog"), SPEED_COLA("speed_cola"),
        DOUBLE_TAP("double_tap"), ROYAL_BEER("royal_beer"), BLOOD_RAGE("blood_rage"),
        PHD_FLOPPER("phd_flopper"), CHERRY("cherry"), QUICK_REVIVE("quick_revive"),
        VULTURE("vulture"), MULE_KICK("mule_kick");
        private final String name;
        PerkType(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
        public static PerkType fromString(String name) {
            for (PerkType type : values()) if (type.name.equals(name)) return type;
            return NONE;
        }
    }

    public enum DummyPart implements StringRepresentable {
        LOWER("lower"), UPPER("upper");
        private final String name;
        DummyPart(String name) { this.name = name; }
        @Override public String getSerializedName() { return this.name; }
    }

    public static class PerksAColaDummyBlock extends Block {
        public static final EnumProperty<DummyPart> PART = EnumProperty.create("part", DummyPart.class);
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

        public PerksAColaDummyBlock() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(-1, 3600000).noOcclusion().pushReaction(PushReaction.BLOCK));
            this.registerDefaultState(this.stateDefinition.any().setValue(PART, DummyPart.LOWER).setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(PART, FACING);
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.INVISIBLE;
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            Direction facing = state.getValue(FACING);
            BlockState mainState = BLOCK.get().defaultBlockState().setValue(PerksAColaBlock.FACING, facing);
            VoxelShape mainShape = mainState.getShape(world, pos, context);
            
            if (state.getValue(PART) == DummyPart.LOWER) {
                return mainShape.move(0, 1, 0); 
            } else {
                return mainShape.move(0, -1, 0); 
            }
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return Shapes.empty(); 
        }

        @Override
        public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            BlockPos mainPos = getMainPos(pos, state);
            BlockState mainState = world.getBlockState(mainPos);
            if (mainState.is(BLOCK.get())) {
                return mainState.use(world, player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), mainPos, hit.isInside()));
            }
            return InteractionResult.PASS;
        }

        @Override
        public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
            BlockPos mainPos = getMainPos(pos, state);
            BlockState mainState = world.getBlockState(mainPos);
            if (mainState.is(BLOCK.get())) {
                mainState.neighborChanged(world, mainPos, block, fromPos, isMoving);
            }
        }

        @Override
        public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())) {
                BlockPos mainPos = getMainPos(pos, state);
                if (world.getBlockState(mainPos).is(BLOCK.get())) {
                    world.destroyBlock(mainPos, false);
                }
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }

        private BlockPos getMainPos(BlockPos pos, BlockState state) {
            return state.getValue(PART) == DummyPart.LOWER ? pos.above() : pos.below();
        }

        @Override
        public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
            return true;
        }
    }

    public static class PerksAColaBlock extends Block implements EntityBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final EnumProperty<PerkType> PERK_TYPE = EnumProperty.create("perk_type", PerkType.class);
        public static final BooleanProperty POWERED = BooleanProperty.create("powered");

        public static final Map<Player, Boolean> isTouching = new HashMap<>();

        public PerksAColaBlock() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE).sound(SoundType.METAL).strength(-1, 3600000).noOcclusion().pushReaction(PushReaction.BLOCK));
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PERK_TYPE, PerkType.NONE).setValue(POWERED, false));
        }

        @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PERK_TYPE, POWERED); }

        @Override 
        public BlockState getStateForPlacement(BlockPlaceContext context) { 
            BlockPos mainPos = context.getClickedPos();
            if (!context.getLevel().getBlockState(mainPos.below()).canBeReplaced() || 
                !context.getLevel().getBlockState(mainPos.above()).canBeReplaced()) {
                return null;
            }
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); 
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
            level.setBlock(pos.below(), DUMMY_BLOCK.get().defaultBlockState().setValue(PerksAColaDummyBlock.PART, DummyPart.LOWER).setValue(PerksAColaDummyBlock.FACING, state.getValue(FACING)), 3);
            level.setBlock(pos.above(), DUMMY_BLOCK.get().defaultBlockState().setValue(PerksAColaDummyBlock.PART, DummyPart.UPPER).setValue(PerksAColaDummyBlock.FACING, state.getValue(FACING)), 3);
        }

        @Override
        public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())) {
                if (world.getBlockState(pos.below()).is(DUMMY_BLOCK.get())) world.destroyBlock(pos.below(), false);
                if (world.getBlockState(pos.above()).is(DUMMY_BLOCK.get())) world.destroyBlock(pos.above(), false);
                super.onRemove(state, world, pos, newState, isMoving);
            }
        }

        public BlockState rotate(BlockState state, Rotation rot) { return state.setValue(FACING, rot.rotate(state.getValue(FACING))); }
        public BlockState mirror(BlockState state, Mirror mirrorIn) { return state.rotate(mirrorIn.getRotation(state.getValue(FACING))); }

        @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return switch (state.getValue(FACING)) {
                case NORTH -> Shapes.or(
                    Block.box(0, -16, 0, 16, 10.75, 16), 
                    Block.box(1.5, 10.75, 1.5, 14.5, 23, 14.5), 
                    Block.box(3.25, -2.75, -1, 12.75, 9, 0)
                );
                case EAST -> Shapes.or(
                    Block.box(0, -16, 0, 16, 10.75, 16), 
                    Block.box(1.5, 10.75, 1.5, 14.5, 23, 14.5), 
                    Block.box(16, -2.75, 3.25, 17, 9, 12.75)
                );
                case SOUTH -> Shapes.or(
                    Block.box(0, -16, 0, 16, 10.75, 16), 
                    Block.box(1.5, 10.75, 1.5, 14.5, 23, 14.5), 
                    Block.box(3.25, -2.75, 16, 12.75, 9, 17)
                );
                case WEST -> Shapes.or(
                    Block.box(0, -16, 0, 16, 10.75, 16), 
                    Block.box(1.5, 10.75, 1.5, 14.5, 23, 14.5), 
                    Block.box(-1, -2.75, 3.25, 0, 9, 12.75)
                );
                default -> Shapes.or(
                    Block.box(0, -16, 0, 16, 10.75, 16), 
                    Block.box(1.5, 10.75, 1.5, 14.5, 23, 14.5)
                );
            };
        }

        @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return getShape(state, world, pos, context); }
        @Override public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) { return false; }

        @Override
        public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
            return true;
        }

        @Override
        public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (world.isClientSide) return InteractionResult.SUCCESS;
            if (player.isCreative()) {
                NetworkHooks.openScreen((ServerPlayer) player, (MenuProvider) world.getBlockEntity(pos), pos);
                return InteractionResult.CONSUME;
            } else {
                CompoundTag tag = player.getPersistentData();
                long lastUsed = tag.getLong("perk_cd");
                long now = world.getGameTime();
                if (now - lastUsed < 40) return InteractionResult.CONSUME;
                tag.putLong("perk_cd", now);
                world.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_deny")), SoundSource.BLOCKS, 1f, 1f);
                return InteractionResult.CONSUME;
            }
        }

        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PerksAColaBlockEntity(pos, state); }

        @Override public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
            if (!world.isClientSide) {
                boolean hasPower = world.hasNeighborSignal(pos) || world.hasNeighborSignal(pos.below()) || world.hasNeighborSignal(pos.above());
                if (state.getValue(POWERED) != hasPower) {
                    world.setBlock(pos, state.setValue(POWERED, hasPower), 3);
                    if (world.getBlockEntity(pos) instanceof PerksAColaBlockEntity be) {
                        be.setPowered(hasPower);
                    }
                }
            }
        }

        @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide) return (lvl, pos, st, be) -> { if (be instanceof PerksAColaBlockEntity b) PerksAColaBlockEntity.clientTick(lvl, pos, st, b); };
            return (lvl, pos, st, be) -> { if (be instanceof PerksAColaBlockEntity b) PerksAColaBlockEntity.serverTick(lvl, pos, st, b); };
        }
    }

    public static class PerksAColaBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
        private net.minecraft.core.NonNullList<ItemStack> stacks = net.minecraft.core.NonNullList.withSize(0, ItemStack.EMPTY);
        private int savedPrice = 0;
        private String savedPerkId = "";
        private boolean isPowered = false;
        public final Map<Player, Boolean> localTouchMap = new HashMap<>();
        private final Set<UUID> claimedCoins = new HashSet<>();

        public PerksAColaBlockEntity(BlockPos pos, BlockState state) { super(BE.get(), pos, state); }

        @Override
        public void onLoad() {
            super.onLoad();
            if (this.level != null && !this.level.isClientSide) {
                BlockState state = this.getBlockState();
                PerkType expectedType = PerkType.fromString(this.savedPerkId);
                if (state.hasProperty(PerksAColaBlock.PERK_TYPE) && state.getValue(PerksAColaBlock.PERK_TYPE) != expectedType) {
                    this.level.setBlock(this.worldPosition, state.setValue(PerksAColaBlock.PERK_TYPE, expectedType), 3);
                }
            }
        }

        public boolean claimCoin(Player player) {
            if (claimedCoins.add(player.getUUID())) {
                setChanged();
                return true;
            }
            return false;
        }

        @Override public void saveAdditional(CompoundTag tag) { 
            super.saveAdditional(tag); 
            tag.putInt("savedPrice", savedPrice); 
            tag.putString("savedPerkId", savedPerkId); 
            tag.putBoolean("isPowered", isPowered);

            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (UUID uuid : claimedCoins) {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", uuid);
                list.add(c);
            }
            tag.put("ClaimedCoins", list);
        }

        @Override public void load(CompoundTag tag) {
            super.load(tag); 
            this.savedPrice = tag.getInt("savedPrice"); 
            this.savedPerkId = tag.getString("savedPerkId");
            if ("mastodonte".equals(this.savedPerkId)) this.savedPerkId = "juggernog";
            if ("double_tape".equals(this.savedPerkId)) this.savedPerkId = "double_tap";
            this.isPowered = tag.getBoolean("isPowered");

            if (tag.contains("ClaimedCoins", 9)) {
                claimedCoins.clear();
                net.minecraft.nbt.ListTag list = tag.getList("ClaimedCoins", 10);
                for (int i = 0; i < list.size(); i++) {
                    claimedCoins.add(list.getCompound(i).getUUID("id"));
                }
            }
        }

        public void setSavedPrice(int p) { this.savedPrice = p; setChanged(); if (this.level != null) this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3); }

        public void setSavedPerkId(String id) { 
            this.savedPerkId = id; 
            if (this.level != null && !this.level.isClientSide) {
                BlockState state = this.getBlockState();
                PerkType newType = PerkType.fromString(id);
                if (state.hasProperty(PerksAColaBlock.PERK_TYPE) && state.getValue(PerksAColaBlock.PERK_TYPE) != newType) {
                    this.level.setBlock(this.worldPosition, state.setValue(PerksAColaBlock.PERK_TYPE, newType), 3);
                } else {
                    this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
                }
            }
            setChanged(); 
        }

        public int getSavedPrice() { return savedPrice; }
        public String getSavedPerkId() { return savedPerkId; }
        public boolean isPowered() { return isPowered; }
        public void setPowered(boolean p) { this.isPowered = p; setChanged(); }

        @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
        @Override public CompoundTag getUpdateTag() { return this.saveWithFullMetadata(); }
        @Override public int getContainerSize() { return stacks.size(); }
        @Override public boolean isEmpty() { return true; }
        @Override protected net.minecraft.core.NonNullList<ItemStack> getItems() { return this.stacks; }
        @Override protected void setItems(net.minecraft.core.NonNullList<ItemStack> stacks) { this.stacks = stacks; }
        @Override public Component getDefaultName() { return Component.literal("perks_a_cola"); }
        @Override public AbstractContainerMenu createMenu(int id, Inventory inv) { return new PerksInterfaceMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition)); }
        @Override public int[] getSlotsForFace(Direction side) { return IntStream.range(0, this.getContainerSize()).toArray(); }
        @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction dir) { return true; }
        @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction dir) { return true; }

        @OnlyIn(Dist.CLIENT)
        public static void clientTick(Level world, BlockPos pos, BlockState state, PerksAColaBlockEntity be) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            if (state.hasProperty(PerksAColaBlock.POWERED) && state.getValue(PerksAColaBlock.POWERED)) {
                if (player.position().distanceToSqr(pos.getCenter()) < 16) {
                    world.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_ambiant")), SoundSource.BLOCKS, 0.1f, 1f, false);
                }
            }
            AABB box = state.getShape(world, pos).bounds().move(pos);
            boolean wasTouching = be.localTouchMap.getOrDefault(player, false);
            boolean touching = box.inflate(0.1).intersects(player.getBoundingBox());
            
            if (touching && !wasTouching) {
                be.localTouchMap.put(player, true);
                world.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_collision")), SoundSource.BLOCKS, 1f, 1f, false);
            } else if (!touching && wasTouching) {
                be.localTouchMap.put(player, false);
            }
        }

        public static void serverTick(Level world, BlockPos pos, BlockState state, PerksAColaBlockEntity be) {
            if (world.getGameTime() % 10 != 0) return;
            AABB box = new AABB(pos).inflate(2.0);
            for (Player player : world.getEntitiesOfClass(Player.class, box)) {
                if (player.getPose() == net.minecraft.world.entity.Pose.SWIMMING || me.cryo.zombierool.player.PlayerCrawlManager.isCrawling(player.getUUID())) {
                    if (be.claimCoin(player)) {
                        me.cryo.zombierool.PointManager.modifyScore(player, 100);
                        world.playSound(null, player.blockPosition(), ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:buy")), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    public static class PerksInterfaceMenu extends AbstractContainerMenu {
        public final BlockPos pos;
        public PerksInterfaceMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
            super(MENU.get(), id);
            this.pos = extraData != null ? extraData.readBlockPos() : BlockPos.ZERO;
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    @OnlyIn(Dist.CLIENT)
    public static class PerksInterfaceScreen extends UnifiedConfigScreen<PerksInterfaceMenu> {
        private EditBox prix_input;
        private final List<PerksManager.Perk> perksList = new ArrayList<>(PerksManager.ALL_PERKS.values());
        private int currentPerkIndex = 0;

        public PerksInterfaceScreen(PerksInterfaceMenu menu, Inventory inv, Component title) {
            super(menu, inv, Component.literal("Perks Machine"));
        }

        @Override protected void init() {
            super.init();
            int startX = (this.width - this.imageWidth) / 2;
            int startY = (this.height - this.imageHeight) / 2;

            prix_input = new EditBox(this.font, startX + 100, startY + 120, 60, 20, Component.empty());
            prix_input.setMaxLength(10);

            if (this.minecraft.level.getBlockEntity(this.menu.pos) instanceof PerksAColaBlockEntity be) {
                prix_input.setValue(String.valueOf(be.getSavedPrice()));
                String savedId = be.getSavedPerkId();
                if (savedId != null && !savedId.isEmpty()) {
                    for (int i = 0; i < perksList.size(); i++) {
                        if (perksList.get(i).getId().equals(savedId)) { currentPerkIndex = i; break; }
                    }
                }
            }
            this.addRenderableWidget(prix_input);

            this.addRenderableWidget(Button.builder(Component.literal("<- Prev"), btn -> {
                if (currentPerkIndex > 0) currentPerkIndex--;
            }).bounds(startX + 20, startY + 60, 50, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Next ->"), btn -> {
                if (currentPerkIndex < perksList.size() - 1) currentPerkIndex++;
            }).bounds(startX + 190, startY + 60, 50, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§aSave"), btn -> {
                try {
                    int p = Integer.parseInt(prix_input.getValue());
                    NetworkHandler.INSTANCE.sendToServer(new C2SSavePerksConfigPacket(this.menu.pos, p, perksList.get(currentPerkIndex).getId()));
                    this.minecraft.setScreen(null);
                } catch (Exception e){}
            }).bounds(startX + 80, startY + 150, 100, 20).build());
        }

        @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
            super.renderLabels(g, mx, my);
            if (!perksList.isEmpty()) {
                g.drawCenteredString(this.font, "§d" + perksList.get(currentPerkIndex).getName(), this.imageWidth / 2, 65, 0xFFFFFF);
                g.drawString(this.font, "Price:", 60, 126, 0xAAAAAA, false);
            }
        }
    }

    public static class LegacyUpperPerkBlock extends Block {
        public LegacyUpperPerkBlock() { super(BlockBehaviour.Properties.of().noOcclusion().noCollission()); }
        @Override
        public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
            if (level.getBlockState(pos).getBlock() == this) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        @Override
        public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
            level.scheduleTick(pos, this, 1);
        }
    }

    public static class LegacyPerkBE extends BlockEntity {
        private String perkId = "";
        private int price = 0;
        public LegacyPerkBE(BlockPos pos, BlockState state) { super(LEGACY_PERKS_BE.get(), pos, state); }
        @Override public void load(CompoundTag tag) {
            super.load(tag);
            if (tag.contains("perk_id")) perkId = tag.getString("perk_id");
            else if (tag.contains("PerkType")) perkId = tag.getString("PerkType");
            else if (tag.contains("SavedPerkId")) perkId = tag.getString("SavedPerkId");
            else if (tag.contains("savedPerkId")) perkId = tag.getString("savedPerkId");
            if ("mastodonte".equals(perkId)) perkId = "juggernog";
            if ("double_tape".equals(perkId)) perkId = "double_tap";
            if (tag.contains("savedPrice")) price = tag.getInt("savedPrice");
            else if (tag.contains("SavedPrice")) price = tag.getInt("SavedPrice");
            else if (tag.contains("prix")) price = tag.getInt("prix");
        }
        public String getPerkId() { return perkId; }
        public int getPrice() { return price; }
    }

    public static class LegacyPerkBlock extends Block implements EntityBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public LegacyPerkBlock() {
            super(BlockBehaviour.Properties.of().noOcclusion().noCollission());
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
        @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LegacyPerkBE(pos, state); }

        @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return !level.isClientSide ? (lvl, pos, st, be) -> {
                if (be instanceof LegacyPerkBE oldBe) {
                    String pId = oldBe.getPerkId();
                    if ("mastodonte".equals(pId)) pId = "juggernog";
                    if ("double_tape".equals(pId)) pId = "double_tap";
                    int oldPrice = oldBe.getPrice();
                    Direction facing = st.hasProperty(FACING) ? st.getValue(FACING) : Direction.NORTH;
                    BlockPos mainPos = pos.above();

                    BlockState newState = BLOCK.get().defaultBlockState().setValue(PerksAColaBlock.FACING, facing).setValue(PerksAColaBlock.PERK_TYPE, PerkType.fromString(pId));
                    lvl.setBlock(mainPos, newState, 3);
                    lvl.setBlock(pos, DUMMY_BLOCK.get().defaultBlockState().setValue(PerksAColaDummyBlock.PART, DummyPart.LOWER).setValue(PerksAColaDummyBlock.FACING, facing), 3);
                    lvl.setBlock(mainPos.above(), DUMMY_BLOCK.get().defaultBlockState().setValue(PerksAColaDummyBlock.PART, DummyPart.UPPER).setValue(PerksAColaDummyBlock.FACING, facing), 3);

                    if (lvl.getBlockEntity(mainPos) instanceof PerksAColaBlockEntity newBe) {
                        if (!pId.isEmpty()) {
                            newBe.setSavedPerkId(pId);
                            newBe.setSavedPrice(oldPrice > 0 ? oldPrice : getDefaultPrice(pId));
                        }
                    }
                }
            } : null;
        }
        private int getDefaultPrice(String perkId) {
            return switch(perkId) {
                case "juggernog" -> 2500;
                case "speed_cola" -> 3000;
                case "double_tap" -> 2000;
                case "quick_revive" -> 1500;
                case "mule_kick" -> 4000;
                case "vulture" -> 3000;
                case "cherry" -> 2000;
                case "phd_flopper" -> 2000;
                case "blood_rage" -> 2500;
                case "royal_beer" -> 2500;
                default -> 2000;
            };
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class PerksPlacementPreview {
        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

            Minecraft mc = Minecraft.getInstance(); 
            Player player = mc.player; 
            Level level = mc.level;
            if (player == null || level == null || player.getMainHandItem().getItem() != ITEM.get()) return;

            if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
                BlockPlaceContext ctx = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), bhr);
                BlockPos mainPos = ctx.getClickedPos().above(); 
                Direction facing = player.getDirection().getOpposite();

                if (level.getBlockState(mainPos).canBeReplaced() 
                    && level.getBlockState(mainPos.above()).canBeReplaced() 
                    && level.getBlockState(mainPos.below()).canBeReplaced()) {

                    PoseStack ps = event.getPoseStack(); 
                    ps.pushPose();
                    net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.getMainCamera().getPosition(); 
                    ps.translate(-cam.x, -cam.y, -cam.z);

                    RenderSystem.enableBlend(); 
                    RenderSystem.defaultBlendFunc(); 
                    RenderSystem.disableCull();
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    Tesselator tess = Tesselator.getInstance(); 
                    BufferBuilder buf = tess.getBuilder();
                    buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

                    VoxelShape shape = BLOCK.get().defaultBlockState().setValue(PerksAColaBlock.FACING, facing).getShape(level, mainPos);
                    Matrix4f mat = ps.last().pose();
                    int r = 255, g = 255, b = 255, a = 100;

                    shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                        float fX1 = (float) (x1 + mainPos.getX());
                        float fY1 = (float) (y1 + mainPos.getY());
                        float fZ1 = (float) (z1 + mainPos.getZ());
                        float fX2 = (float) (x2 + mainPos.getX());
                        float fY2 = (float) (y2 + mainPos.getY());
                        float fZ2 = (float) (z2 + mainPos.getZ());
                        
                        buf.vertex(mat, fX1, fY1, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX1, fY2, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY2, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY1, fZ1).color(r, g, b, a).endVertex();
                        
                        buf.vertex(mat, fX1, fY1, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY1, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY2, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX1, fY2, fZ2).color(r, g, b, a).endVertex();
                        
                        buf.vertex(mat, fX1, fY1, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX1, fY1, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX1, fY2, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX1, fY2, fZ1).color(r, g, b, a).endVertex();
                        
                        buf.vertex(mat, fX2, fY1, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY2, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY2, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY1, fZ2).color(r, g, b, a).endVertex();
                        
                        buf.vertex(mat, fX1, fY1, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY1, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY1, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX1, fY1, fZ2).color(r, g, b, a).endVertex();
                        
                        buf.vertex(mat, fX1, fY2, fZ1).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX1, fY2, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY2, fZ2).color(r, g, b, a).endVertex();
                        buf.vertex(mat, fX2, fY2, fZ1).color(r, g, b, a).endVertex();
                    });

                    tess.end(); 
                    RenderSystem.enableCull(); 
                    RenderSystem.disableBlend(); 
                    ps.popPose();
                }
            }
        }
    }
}
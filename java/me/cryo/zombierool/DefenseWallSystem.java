package me.cryo.zombierool.block.system;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.block.system.DefenseDoorSystem.RepairTracker;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DefenseWallSystem {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);

    public static final RegistryObject<Block> MAIN_BLOCK = BLOCKS.register("defense_wall", DefenseWallBlock::new);
    public static final RegistryObject<Block> DUMMY_BLOCK = BLOCKS.register("defense_wall_dummy", DefenseWallDummyBlock::new);

    public static final RegistryObject<Item> ITEM = ITEMS.register("defense_wall", () -> new BlockItem(MAIN_BLOCK.get(), new Item.Properties()) {
        @Override
        public InteractionResult place(BlockPlaceContext context) {
            Direction facing = context.getHorizontalDirection().getOpposite();
            Direction right = facing.getClockWise();
            BlockPos clicked = context.getClickedPos();
            BlockPos center = clicked.above();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos target = center.offset(dx * right.getStepX(), dy, dx * right.getStepZ());
                    if (!context.getLevel().getBlockState(target).canBeReplaced(context)) {
                        return InteractionResult.FAIL;
                    }
                }
            }

            BlockPlaceContext centerContext = BlockPlaceContext.at(context, center, facing);
            InteractionResult result = super.place(centerContext);

            if (result.consumesAction()) {
                Level level = context.getLevel();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;

                        BlockPos target = center.offset(dx * right.getStepX(), dy, dx * right.getStepZ());
                        WallPart part = WallPart.fromOffset(dx, dy);

                        level.setBlock(target, DUMMY_BLOCK.get().defaultBlockState()
                                .setValue(DefenseWallDummyBlock.FACING, facing)
                                .setValue(DefenseWallDummyBlock.PART, part)
                                .setValue(DefenseWallDummyBlock.STAGE, 7), 3);
                    }
                }
            }
            return result;
        }
    });

    public static final RegistryObject<BlockEntityType<DefenseWallBlockEntity>> BE = BLOCK_ENTITIES.register("defense_wall",
            () -> BlockEntityType.Builder.of(DefenseWallBlockEntity::new, MAIN_BLOCK.get()).build(null));

    static {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(new ResourceLocation(ZombieroolMod.MODID, "zb_rct"))) {
            event.accept(ITEM.get());
        }
    }

    public enum WallPart implements StringRepresentable {
        BOTTOM_LEFT("bottom_left", -1, -1), BOTTOM_CENTER("bottom_center", 0, -1), BOTTOM_RIGHT("bottom_right", 1, -1),
        MIDDLE_LEFT("middle_left", -1, 0), MIDDLE_RIGHT("middle_right", 1, 0),
        TOP_LEFT("top_left", -1, 1), TOP_CENTER("top_center", 0, 1), TOP_RIGHT("top_right", 1, 1);

        private final String name;
        public final int dx;
        public final int dy;

        WallPart(String name, int dx, int dy) {
            this.name = name;
            this.dx = dx;
            this.dy = dy;
        }

        @Override
        public String getSerializedName() { return this.name; }

        public static WallPart fromOffset(int dx, int dy) {
            for (WallPart part : values()) {
                if (part.dx == dx && part.dy == dy) return part;
            }
            return BOTTOM_CENTER;
        }
    }

    public static class DefenseWallBlockEntity extends BlockEntity implements MimicSystem.IMimicContainer {
        private BlockState mimicBlockState = null;

        public DefenseWallBlockEntity(BlockPos pos, BlockState state) {
            super(BE.get(), pos, state);
        }

        @Override
        public AABB getRenderBoundingBox() {
            return new AABB(worldPosition).inflate(2.0);
        }

        @Nullable
        @Override
        public BlockState getMimic() {
            return mimicBlockState;
        }

        @Override
        public void setMimic(@Nullable BlockState state) {
            this.mimicBlockState = state;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        public static void tick(Level level, BlockPos pos, BlockState state, DefenseWallBlockEntity be) {
            if (level.isClientSide) return;
            int currentStage = state.getValue(DefenseWallBlock.STAGE);
            if (currentStage <= 0) return;

            AABB detectionBox = new AABB(pos).inflate(1.5);
            List<ZombieEntity> zombies = level.getEntitiesOfClass(ZombieEntity.class, detectionBox);

            for (ZombieEntity zombie : zombies) {
                if (!zombie.isCrawler()) {
                    if (handleMobAttack(zombie, level, pos, state, currentStage)) {
                        break;
                    }
                }
            }
        }

        private static boolean handleMobAttack(ZombieEntity mob, Level level, BlockPos pos, BlockState state, int currentStage) {
            mob.resetStuckTimer();
            CompoundTag data = mob.getPersistentData();
            String key = "zombie_attack_time_wall_" + pos.asLong();

            if (!data.contains(key)) {
                data.putLong(key, level.getGameTime());
            } else {
                long startTime = data.getLong(key);
                long elapsed = level.getGameTime() - startTime;
                if (elapsed >= 60) {
                    if (state.getBlock() instanceof DefenseWallBlock wall) {
                        wall.updateStage(level, pos, currentStage - 1);
                        level.playSound(null, pos, ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "wall_snap")), SoundSource.HOSTILE, 1.0f, 1.0f);
                    }
                    data.remove(key);
                    mob.level().broadcastEntityEvent(mob, (byte) 4); 
                    return true;
                }
            }
            return false;
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            this.mimicBlockState = MimicSystem.loadMimic(tag, this.level, "MimicBlock", false);
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            MimicSystem.saveMimic(tag, this.mimicBlockState);
        }

        @Override
        public CompoundTag getUpdateTag() { return this.saveWithoutMetadata(); }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }

    public static class DefenseWallBlock extends HorizontalDirectionalBlock implements EntityBlock, MimicSystem.IMimicBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 7);
        public static final BooleanProperty HAS_MIMIC = BooleanProperty.create("has_mimic");
        public static final BooleanProperty PERMANENTLY_OPEN = BooleanProperty.create("permanently_open");

        public DefenseWallBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.STONE)
                    .strength(-1, 3600000)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK));
            this.registerDefaultState(this.stateDefinition.any()
                    .setValue(FACING, Direction.NORTH)
                    .setValue(STAGE, 7)
                    .setValue(HAS_MIMIC, false)
                    .setValue(PERMANENTLY_OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, STAGE, HAS_MIMIC, PERMANENTLY_OPEN);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rot) {
            return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirrorIn) {
            return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new DefenseWallBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide ? null : (lvl, pos, st, be) -> {
                if (be instanceof DefenseWallBlockEntity defBe) {
                    DefenseWallBlockEntity.tick(lvl, pos, st, defBe);
                }
            };
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (player.isCreative()) {
                ItemStack heldItem = player.getItemInHand(hand);
                if (!heldItem.isEmpty() && heldItem.getItem() instanceof BlockItem blockItem) {
                    Block blockToCopy = blockItem.getBlock();
                    BlockState placementState = MimicSystem.getStateForMimic(player, hand, heldItem, hit, blockToCopy);

                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof DefenseWallBlockEntity defBe) {
                        defBe.setMimic(placementState);
                        setHasMimic(level, pos, state, true);
                        player.displayClientMessage(Component.translatable("message.zombierool.texture_copied").withStyle(ChatFormatting.GREEN), true);
                        return InteractionResult.SUCCESS;
                    }
                }
                
                if (player.isShiftKeyDown() && heldItem.isEmpty()) {
                    boolean isPermOpen = !state.getValue(PERMANENTLY_OPEN);
                    int newStage = isPermOpen ? 0 : 7;
                    updateStage(level, pos, newStage);
                    level.setBlock(pos, level.getBlockState(pos).setValue(PERMANENTLY_OPEN, isPermOpen), 3);
                    player.displayClientMessage(Component.translatable(isPermOpen ? "message.zombierool.defense_door.locked" : "message.zombierool.defense_door.restored").withStyle(isPermOpen ? ChatFormatting.RED : ChatFormatting.GREEN), true);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }

        private void setHasMimic(Level level, BlockPos pos, BlockState state, boolean hasMimic) {
            level.setBlock(pos, state.setValue(HAS_MIMIC, hasMimic), 3);
            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    BlockPos dummyPos = pos.offset(dx * right.getStepX(), dy, dx * right.getStepZ());
                    BlockState dummyState = level.getBlockState(dummyPos);
                    if (dummyState.getBlock() instanceof DefenseWallDummyBlock) {
                        level.setBlock(dummyPos, dummyState.setValue(HAS_MIMIC, hasMimic), 3);
                    }
                }
            }
        }

        public void updateStage(Level level, BlockPos pos, int newStage) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DefenseWallBlock)) return;

            if (state.getValue(PERMANENTLY_OPEN)) return;

            int currentStage = state.getValue(STAGE);
            if (newStage > currentStage) {
                Player player = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 4, false);
                if (player != null && RepairTracker.tryAddRepair(player)) {
                    PointManager.modifyScore(player, 10);
                }
            }

            level.setBlock(pos, state.setValue(STAGE, newStage), 3);

            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    BlockPos dummyPos = pos.offset(dx * right.getStepX(), dy, dx * right.getStepZ());
                    BlockState dummyState = level.getBlockState(dummyPos);
                    if (dummyState.getBlock() instanceof DefenseWallDummyBlock) {
                        level.setBlock(dummyPos, dummyState.setValue(STAGE, newStage), 3);
                    }
                }
            }
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return state.getValue(HAS_MIMIC) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
        }

        protected VoxelShape createWallShape(BlockState state) {
            Direction facing = state.getValue(FACING);
            switch (facing) {
                case NORTH: return Shapes.box(0, 0, 0, 1, 1, 0.125);
                case SOUTH: return Shapes.box(0, 0, 0.875, 1, 1, 1);
                case WEST:  return Shapes.box(0, 0, 0, 0.125, 1, 1);
                case EAST:  return Shapes.box(0.875, 0, 0, 1, 1, 1);
                default: return Shapes.box(0, 0, 0, 1, 1, 0.125);
            }
        }

        protected VoxelShape createCollisionShape(BlockState state) {
            Direction facing = state.getValue(FACING);
            switch (facing) {
                case NORTH: return Shapes.box(0, 0, 0, 1, 1.5, 0.25);
                case SOUTH: return Shapes.box(0, 0, 0.75, 1, 1.5, 1);
                case WEST:  return Shapes.box(0, 0, 0, 0.25, 1.5, 1);
                case EAST:  return Shapes.box(0.75, 0, 0, 1, 1.5, 1);
                default: return Shapes.box(0, 0, 0, 1, 1.5, 0.25);
            }
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return createWallShape(state);
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            if (context instanceof EntityCollisionContext ecc) {
                Entity entity = ecc.getEntity();
                if (entity instanceof net.minecraft.world.entity.projectile.Projectile) {
                    return Shapes.empty();
                }
                if (state.getValue(STAGE) <= 0) {
                    if (entity instanceof ZombieEntity || entity instanceof CrawlerEntity || entity instanceof HellhoundEntity || entity instanceof Player) {
                        return Shapes.empty(); 
                    }
                }
                if (entity instanceof Player p && p.isCreative()) {
                    return Shapes.empty();
                }
                if (entity instanceof ZombieEntity || entity instanceof CrawlerEntity || entity instanceof HellhoundEntity) {
                    return Shapes.box(0, 0, 0, 1, 1.5, 1);
                }
            }
            return createCollisionShape(state);
        }

        @Override
        public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob mob) {
            return BlockPathTypes.OPEN;
        }

        @Override
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
            return true;
        }

        @Override
        public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())) {
                Direction facing = state.getValue(FACING);
                Direction right = facing.getClockWise();

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        BlockPos dummyPos = pos.offset(dx * right.getStepX(), dy, dx * right.getStepZ());
                        if (world.getBlockState(dummyPos).getBlock() instanceof DefenseWallDummyBlock) {
                            world.setBlock(dummyPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
                super.onRemove(state, world, pos, newState, isMoving);
            }
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip.add(Component.translatable("block.zombierool.defense_wall.tooltip.1").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("block.zombierool.defense_wall.tooltip.2").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("block.zombierool.defense_wall.tooltip.3").withStyle(ChatFormatting.GRAY));
        }
    }

    public static class DefenseWallDummyBlock extends Block implements MimicSystem.IMimicBlock {
        public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
        public static final EnumProperty<WallPart> PART = EnumProperty.create("part", WallPart.class);
        public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 7);
        public static final BooleanProperty HAS_MIMIC = BooleanProperty.create("has_mimic");

        public DefenseWallDummyBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.STONE)
                    .strength(-1, 3600000)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK));
            this.registerDefaultState(this.stateDefinition.any()
                    .setValue(FACING, Direction.NORTH)
                    .setValue(PART, WallPart.BOTTOM_CENTER)
                    .setValue(STAGE, 7)
                    .setValue(HAS_MIMIC, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, PART, STAGE, HAS_MIMIC);
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rot) {
            return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirrorIn) {
            return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
        }

        public BlockPos getMainPos(BlockPos pos, BlockState state) {
            Direction facing = state.getValue(FACING);
            WallPart part = state.getValue(PART);
            Direction right = facing.getClockWise();
            return pos.offset(-part.dx * right.getStepX(), -part.dy, -part.dx * right.getStepZ());
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            BlockPos mainPos = getMainPos(pos, state);
            BlockState mainState = level.getBlockState(mainPos);
            if (mainState.is(MAIN_BLOCK.get())) {
                return mainState.use(level, player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), mainPos, hit.isInside()));
            }
            return InteractionResult.PASS;
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.INVISIBLE;
        }

        protected VoxelShape createWallShape(BlockState state) {
            Direction facing = state.getValue(FACING);
            switch (facing) {
                case NORTH: return Shapes.box(0, 0, 0, 1, 1, 0.125);
                case SOUTH: return Shapes.box(0, 0, 0.875, 1, 1, 1);
                case WEST:  return Shapes.box(0, 0, 0, 0.125, 1, 1);
                case EAST:  return Shapes.box(0.875, 0, 0, 1, 1, 1);
                default: return Shapes.box(0, 0, 0, 1, 1, 0.125);
            }
        }

        protected VoxelShape createCollisionShape(BlockState state) {
            Direction facing = state.getValue(FACING);
            switch (facing) {
                case NORTH: return Shapes.box(0, 0, 0, 1, 1.5, 0.25);
                case SOUTH: return Shapes.box(0, 0, 0.75, 1, 1.5, 1);
                case WEST:  return Shapes.box(0, 0, 0, 0.25, 1.5, 1);
                case EAST:  return Shapes.box(0.75, 0, 0, 1, 1.5, 1);
                default: return Shapes.box(0, 0, 0, 1, 1.5, 0.25);
            }
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return createWallShape(state);
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            if (context instanceof EntityCollisionContext ecc) {
                Entity entity = ecc.getEntity();
                if (entity instanceof net.minecraft.world.entity.projectile.Projectile) {
                    return Shapes.empty();
                }
                if (state.getValue(STAGE) <= 0) {
                    if (entity instanceof ZombieEntity || entity instanceof CrawlerEntity || entity instanceof HellhoundEntity || entity instanceof Player) {
                        return Shapes.empty(); 
                    }
                }
                if (entity instanceof Player p && p.isCreative()) {
                    return Shapes.empty();
                }
                if (entity instanceof ZombieEntity || entity instanceof CrawlerEntity || entity instanceof HellhoundEntity) {
                    return Shapes.box(0, 0, 0, 1, 1.5, 1);
                }
            }
            return createCollisionShape(state);
        }

        @Override
        public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob mob) {
            return BlockPathTypes.OPEN;
        }

        @Override
        public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
            return true;
        }

        @Override
        public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())) {
                BlockPos mainPos = getMainPos(pos, state);
                if (world.getBlockState(mainPos).is(MAIN_BLOCK.get())) {
                    world.destroyBlock(mainPos, true);
                }
                super.onRemove(state, world, pos, newState, isMoving);
            }
        }
    }

    public static BlockPos getWallInRepairZone(Level level, BlockPos playerPos) {
        BlockPos[] offsets = new BlockPos[]{playerPos.north(), playerPos.south(), playerPos.east(), playerPos.west()};

        for (BlockPos pos : offsets) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DefenseWallBlock) {
                if (state.getValue(DefenseWallBlock.PERMANENTLY_OPEN)) return null;
                return pos;
            } else if (state.getBlock() instanceof DefenseWallDummyBlock dummy) {
                BlockPos mainPos = dummy.getMainPos(pos, state);
                BlockState mainState = level.getBlockState(mainPos);
                if (mainState.getBlock() instanceof DefenseWallBlock && !mainState.getValue(DefenseWallBlock.PERMANENTLY_OPEN)) {
                    return mainPos;
                }
            }
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    public static class DefenseWallRenderer implements BlockEntityRenderer<DefenseWallBlockEntity> {
        public DefenseWallRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(DefenseWallBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
            BlockState wallState = entity.getBlockState();
            if (!(wallState.getBlock() instanceof DefenseWallBlock)) return;

            BlockState mimicState = entity.getMimic();
            if (mimicState == null) return;

            BlockState renderState = wallState.setValue(DefenseWallBlock.HAS_MIMIC, false);

            BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
            BakedModel baseModel = dispatcher.getBlockModel(renderState);
            BakedModel mimicModel = dispatcher.getBlockModel(mimicState);
            net.minecraft.client.renderer.texture.TextureAtlasSprite newSprite = mimicModel.getParticleIcon();
            RenderType renderType = ItemBlockRenderTypes.getRenderType(mimicState, false);
            VertexConsumer vertexConsumer = buffer.getBuffer(renderType);

            poseStack.pushPose();
            renderRemappedModel(baseModel, newSprite, renderState, poseStack, vertexConsumer, combinedLight, combinedOverlay);
            poseStack.popPose();
        }

        private void renderRemappedModel(BakedModel baseModel, net.minecraft.client.renderer.texture.TextureAtlasSprite newSprite, BlockState state, PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();

            for (Direction dir : Direction.values()) {
                random.setSeed(42L);
                renderQuads(baseModel.getQuads(state, dir, random), newSprite, poseStack, consumer, light, overlay);
            }
            random.setSeed(42L);
            renderQuads(baseModel.getQuads(state, null, random), newSprite, poseStack, consumer, light, overlay);
        }

        private void renderQuads(List<net.minecraft.client.renderer.block.model.BakedQuad> quads, net.minecraft.client.renderer.texture.TextureAtlasSprite newSprite, PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
            PoseStack.Pose pose = poseStack.last();
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : quads) {
                net.minecraft.client.renderer.block.model.BakedQuad remapped = remapQuad(quad, newSprite);
                consumer.putBulkData(pose, remapped, 1.0f, 1.0f, 1.0f, light, overlay);
            }
        }

        private net.minecraft.client.renderer.block.model.BakedQuad remapQuad(net.minecraft.client.renderer.block.model.BakedQuad quad, net.minecraft.client.renderer.texture.TextureAtlasSprite newSprite) {
            net.minecraft.client.renderer.texture.TextureAtlasSprite oldSprite = quad.getSprite();
            int[] vertexData = Arrays.copyOf(quad.getVertices(), quad.getVertices().length);

            for (int i = 0; i < 4; i++) {
                int offset = i * 8;
                float u = Float.intBitsToFloat(vertexData[offset + 4]);
                float v = Float.intBitsToFloat(vertexData[offset + 5]);

                float normU = (u - oldSprite.getU0()) / (oldSprite.getU1() - oldSprite.getU0());
                float normV = (v - oldSprite.getV0()) / (oldSprite.getV1() - oldSprite.getV0());

                float newU = newSprite.getU0() + normU * (newSprite.getU1() - newSprite.getU0());
                float newV = newSprite.getV0() + normV * (newSprite.getV1() - newSprite.getV0());

                vertexData[offset + 4] = Float.floatToRawIntBits(newU);
                vertexData[offset + 5] = Float.floatToRawIntBits(newV);
            }

            return new net.minecraft.client.renderer.block.model.BakedQuad(vertexData, quad.getTintIndex(), quad.getDirection(), newSprite, quad.isShade());
        }
    }
}
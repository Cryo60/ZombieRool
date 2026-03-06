package me.cryo.zombierool.block.system;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.init.ZombieroolModExtraBlockEntities;
import me.cryo.zombierool.core.registry.ZRBlocks;
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
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefenseDoorSystem {
	public static class RepairTracker {
	    private static final Map<String, Integer> repairCount = new HashMap<>();
	    private static final Map<String, Long> repairTimestamps = new HashMap<>();
	    private static final int MAX_REPAIRS = 6;
	    private static final long RESET_TIME_MS = 2 * 60 * 1000 + 20 * 1000;
	
	    public static boolean tryAddRepair(Player player) {
	        String uuid = player.getStringUUID();
	        long now = System.currentTimeMillis();
	        if (!repairTimestamps.containsKey(uuid) || now - repairTimestamps.get(uuid) > RESET_TIME_MS) {
	            repairCount.put(uuid, 0);
	            repairTimestamps.put(uuid, now);
	        }
	        int current = repairCount.getOrDefault(uuid, 0);
	        if (current >= MAX_REPAIRS) {
	            return false;
	        }
	        repairCount.put(uuid, current + 1);
	        SoundEvent sound = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "buy"));
	        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.5f, 1.0f);
	        return true;
	    }
	}
	
	public static class DefenseDoorBlockEntity extends BlockEntity {
	    private BlockState mimicBlockState = null;
	
	    public DefenseDoorBlockEntity(BlockPos pos, BlockState state) {
	        super(ZombieroolModExtraBlockEntities.DEFENSE_DOOR.get(), pos, state);
	    }
	
	    public static void tick(Level level, BlockPos pos, BlockState state, DefenseDoorBlockEntity be) {
	        if (level.isClientSide) return;
	        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER && state.getBlock() instanceof DefenseDoorBlock) {
	            int currentStage = state.getValue(DefenseDoorBlock.STAGE);
	            if (currentStage <= 0) return;
	
	            // Hitbox agrandie pour attraper les zombies sur les bords du bloc
	            AABB detectionBox = new AABB(pos).inflate(0.75);
	            
	            List<ZombieEntity> zombies = level.getEntitiesOfClass(ZombieEntity.class, detectionBox);
	            for (ZombieEntity zombie : zombies) {
	                if (handleMobAttack(zombie, level, pos, state, currentStage)) {
	                    break;
	                }
	            }
	        }
	    }
	    
	    private static boolean handleMobAttack(Mob mob, Level level, BlockPos pos, BlockState state, int currentStage) {
	        CompoundTag data = mob.getPersistentData();
	        String key = "zombie_attack_time_" + pos.asLong();
	        if (!data.contains(key)) {
	            data.putLong(key, level.getGameTime());
	        } else {
	            long startTime = data.getLong(key);
	            if (level.getGameTime() - startTime >= 60) {
	                ((DefenseDoorBlock) state.getBlock()).updateStage(level, pos, currentStage - 1);
	                data.remove(key);
	                mob.level().broadcastEntityEvent(mob, (byte) 4); // Animation de frappe
	                return true;
	            }
	        }
	        return false;
	    }
	
	    public void setMimicBlock(BlockState state) {
	        this.mimicBlockState = state;
	        setChanged();
	        if (level != null) {
	            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
	        }
	    }
	
	    @Nullable
	    public BlockState getMimicBlock() {
	        return mimicBlockState;
	    }
	
	    @Override
	    public void load(CompoundTag tag) {
	        super.load(tag);
	        if (tag.contains("MimicBlock")) {
	            this.mimicBlockState = NbtUtils.readBlockState(this.level != null ? this.level.holderLookup(net.minecraft.core.registries.Registries.BLOCK) : net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("MimicBlock"));
	        }
	    }
	
	    @Override
	    protected void saveAdditional(CompoundTag tag) {
	        super.saveAdditional(tag);
	        if (this.mimicBlockState != null) {
	            tag.put("MimicBlock", NbtUtils.writeBlockState(this.mimicBlockState));
	        }
	    }
	
	    @Override
	    public CompoundTag getUpdateTag() {
	        return this.saveWithoutMetadata();
	    }
	
	    @Override
	    public Packet<ClientGamePacketListener> getUpdatePacket() {
	        return ClientboundBlockEntityDataPacket.create(this);
	    }
	}
	
	public abstract static class BaseDefenseDoor extends DoorBlock implements EntityBlock {
	    public static final BooleanProperty CENTERED = BooleanProperty.create("centered");
	    public static final BooleanProperty HAS_MIMIC = BooleanProperty.create("has_mimic");
	    protected static final VoxelShape CENTERED_X_AABB = Block.box(6.5D, 0.0D, 0.0D, 9.5D, 16.0D, 16.0D);
	    protected static final VoxelShape CENTERED_Z_AABB = Block.box(0.0D, 0.0D, 6.5D, 16.0D, 16.0D, 9.5D);
	    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 3.0D);
	    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D);
	    protected static final VoxelShape WEST_AABB = Block.box(13.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
	    protected static final VoxelShape EAST_AABB = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 16.0D, 16.0D);
	
	    public BaseDefenseDoor(Properties properties) {
	        super(properties, BlockSetType.IRON);
	    }
	
	    @Override
	    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
	        return new DefenseDoorBlockEntity(pos, state);
	    }
	
	    @Override
	    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
	        return level.isClientSide ? null : (lvl, pos, st, blockEntity) -> {
	            if (blockEntity instanceof DefenseDoorBlockEntity be) {
	                DefenseDoorBlockEntity.tick(lvl, pos, st, be);
	            }
	        };
	    }
	
	    @Override
	    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
	        if (player.isCreative()) {
	            ItemStack heldItem = player.getItemInHand(hand);
	            if (!heldItem.isEmpty() && heldItem.getItem() instanceof BlockItem blockItem) {
	                BlockState mimicState = blockItem.getBlock().defaultBlockState();
	                setMimic(level, pos, state, mimicState);
	                return InteractionResult.SUCCESS;
	            }
	            if (player.isShiftKeyDown() && heldItem.isEmpty() && this instanceof DefenseDoorBlock) {
	                return handleCreativePermOpen(state, level, pos, player);
	            }
	        }
	        return InteractionResult.PASS;
	    }
	
	    protected InteractionResult handleCreativePermOpen(BlockState state, Level level, BlockPos pos, Player player) {
	        return InteractionResult.PASS;
	    }
	
	    private void setMimic(Level level, BlockPos pos, BlockState state, BlockState mimicState) {
	        DoubleBlockHalf half = state.getValue(HALF);
	        BlockPos lowerPos = (half == DoubleBlockHalf.LOWER) ? pos : pos.below();
	        BlockPos upperPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos;
	        BlockEntity be = level.getBlockEntity(lowerPos);
	        if (be instanceof DefenseDoorBlockEntity defenseBe) {
	            defenseBe.setMimicBlock(mimicState);
	        }
	        BlockState lowerState = level.getBlockState(lowerPos);
	        if (lowerState.getBlock() instanceof BaseDefenseDoor) {
	            level.setBlock(lowerPos, lowerState.setValue(HAS_MIMIC, true), 3);
	        }
	        BlockState upperState = level.getBlockState(upperPos);
	        if (upperState.getBlock() instanceof BaseDefenseDoor) {
	            level.setBlock(upperPos, upperState.setValue(HAS_MIMIC, true), 3);
	        }
	    }
	
	    @Override
	    public RenderShape getRenderShape(BlockState state) {
	        if (state.getValue(HAS_MIMIC) && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
	            return RenderShape.ENTITYBLOCK_ANIMATED;
	        }
	        return RenderShape.MODEL;
	    }
	
	    @Override
	    public BlockState getStateForPlacement(BlockPlaceContext context) {
	        BlockState state = super.getStateForPlacement(context);
	        if (state != null) {
	            boolean centered = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
	            return state.setValue(CENTERED, centered).setValue(HAS_MIMIC, false);
	        }
	        return null;
	    }
	
	    @Override
	    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
	        super.setPlacedBy(level, pos, state, placer, stack);
	        BlockPos upperPos = pos.above();
	        BlockState upperState = level.getBlockState(upperPos);
	        if (upperState.is(this) && upperState.getValue(HALF) == DoubleBlockHalf.UPPER) {
	            level.setBlock(upperPos, upperState.setValue(CENTERED, state.getValue(CENTERED)).setValue(HAS_MIMIC, false), 3);
	        }
	    }
	
	    @Override
	    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
	        super.createBlockStateDefinition(builder);
	        builder.add(HAS_MIMIC);
	    }
	
	    protected VoxelShape createDoorShape(BlockState state) {
	        Direction facing = state.getValue(FACING);
	        boolean open = state.getValue(OPEN);
	        boolean hingeRight = state.getValue(HINGE) == DoorHingeSide.RIGHT;
	        boolean centered = state.getValue(CENTERED);
	        if (centered) {
	            if (open) {
	                if (facing == Direction.NORTH)
	                    return hingeRight ? Shapes.box(0, 0, 0, 0.1875, 1, 1) : Shapes.box(0.8125, 0, 0, 1, 1, 1);
	                if (facing == Direction.SOUTH)
	                    return hingeRight ? Shapes.box(0.8125, 0, 0, 1, 1, 1) : Shapes.box(0, 0, 0, 0.1875, 1, 1);
	                if (facing == Direction.EAST)
	                    return hingeRight ? Shapes.box(0, 0, 0, 1, 1, 0.1875) : Shapes.box(0, 0, 0.8125, 1, 1, 1);
	                if (facing == Direction.WEST)
	                    return hingeRight ? Shapes.box(0, 0, 0.8125, 1, 1, 1) : Shapes.box(0, 0, 0, 1, 1, 0.1875);
	            }
	            if (facing.getAxis() == Direction.Axis.X) return CENTERED_X_AABB;
	            else return CENTERED_Z_AABB;
	        }
	        if (open) {
	            switch (facing) {
	                case NORTH: return hingeRight ? WEST_AABB : EAST_AABB;
	                case SOUTH: return hingeRight ? EAST_AABB : WEST_AABB;
	                case WEST: return hingeRight ? SOUTH_AABB : NORTH_AABB;
	                case EAST: return hingeRight ? NORTH_AABB : SOUTH_AABB;
	                default: return Shapes.block();
	            }
	        } else {
	            switch (facing) {
	                case NORTH: return SOUTH_AABB;
	                case SOUTH: return NORTH_AABB;
	                case WEST: return EAST_AABB;
	                case EAST: return WEST_AABB;
	                default: return Shapes.block();
	            }
	        }
	    }
	
	    @Override
	    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	        return createDoorShape(state);
	    }
	
	    protected static boolean isEnglishClient() {
	        if (Minecraft.getInstance() == null) return false;
	        return Minecraft.getInstance().options.languageCode.startsWith("en");
	    }
	
	    protected static String getTranslatedMessage(String frenchMessage, String englishMessage) {
	        return isEnglishClient() ? englishMessage : frenchMessage;
	    }
	
	    @Override
	    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
	        return 0;
	    }
	
	    @Override
	    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
	        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return Collections.emptyList();
	        return Collections.singletonList(new ItemStack(this, 1));
	    }
	
	    @Override
	    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
	        return type == PathComputationType.LAND;
	    }
	}
	
	public static class DefenseDoorBlock extends BaseDefenseDoor {
	    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 5);
	    public static final BooleanProperty PERMANENTLY_OPEN = BooleanProperty.create("permanently_open");
	    public static final int MAX_STAGE = 5;
	
	    public DefenseDoorBlock() {
	        super(BlockBehaviour.Properties.of().sound(SoundType.LADDER).strength(-1, 3600000).noOcclusion().isRedstoneConductor((bs, br, bp) -> false).dynamicShape());
	        this.registerDefaultState(this.stateDefinition.any()
	                .setValue(FACING, Direction.SOUTH)
	                .setValue(OPEN, false)
	                .setValue(HINGE, DoorHingeSide.LEFT)
	                .setValue(POWERED, false)
	                .setValue(HALF, DoubleBlockHalf.LOWER)
	                .setValue(STAGE, 5)
	                .setValue(CENTERED, false)
	                .setValue(HAS_MIMIC, false)
	                .setValue(PERMANENTLY_OPEN, false));
	    }
	
	    @Override
	    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
	        super.createBlockStateDefinition(builder);
	        builder.add(STAGE, CENTERED, PERMANENTLY_OPEN);
	    }
	
	    @Override
	    protected InteractionResult handleCreativePermOpen(BlockState state, Level level, BlockPos pos, Player player) {
	        boolean currentState = state.getValue(PERMANENTLY_OPEN);
	        boolean newState = !currentState;
	        int newStage = newState ? 0 : MAX_STAGE;
	        BlockState updatedState = state.setValue(PERMANENTLY_OPEN, newState).setValue(STAGE, newStage);
	        level.setBlock(pos, updatedState, 3);
	        updateUpperBlock(level, pos, newStage, newState);
	        BlockPos linkedPos = findLinkedDoor(level, pos);
	        if (linkedPos != null) {
	            BlockState linkedState = level.getBlockState(linkedPos);
	            if (linkedState.getBlock() instanceof DefenseDoorBlock) {
	                level.setBlock(linkedPos, linkedState.setValue(PERMANENTLY_OPEN, newState).setValue(STAGE, newStage), 3);
	                updateUpperBlock(level, linkedPos, newStage, newState);
	            }
	        }
	        String msg = newState ? "§cBarricade verrouillée en position ouverte (Stage 0, irréparable)." : "§aBarricade restaurée (mode normal).";
	        if (isEnglishClient()) {
	            msg = newState ? "§cBarricade locked open (Stage 0, unrepairable)." : "§aBarricade restored (normal mode).";
	        }
	        player.displayClientMessage(Component.literal(msg), true);
	        return InteractionResult.SUCCESS;
	    }
	
	    @Override
	    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
	        super.appendHoverText(itemstack, world, list, flag);
	        list.add(Component.literal(getTranslatedMessage("§9Porte de défense renforcée", "§9Reinforced Defense Door")));
	        list.add(Component.literal(getTranslatedMessage("§7Résiste aux attaques de zombies.", "§7Resists zombie attacks.")));
	        list.add(Component.literal(getTranslatedMessage("§7Possède §a6 niveaux §7d'intégrité (0-5).", "§7Has §a6 stages §7of integrity (0-5).")));
	        list.add(Component.literal(getTranslatedMessage("§7Peut être réparée pour gagner des points.", "§7Can be repaired to earn points.")));
	        list.add(Component.literal(getTranslatedMessage("§7Les zombies la détruisent progressivement.", "§7Zombies gradually destroy it.")));
	        list.add(Component.literal(getTranslatedMessage("§7Une fois détruite (niveau 0), les zombies peuvent passer.", "§7Once destroyed (stage 0), zombies can pass.")));
	        list.add(Component.literal(getTranslatedMessage("§7Indestructible pour les joueurs.", "§7Indestructible for players.")));
	        list.add(Component.literal(getTranslatedMessage("§7Accroupissez-vous en plaçant pour centrer la porte.", "§7Sneak while placing to center the door.")));
	        list.add(Component.literal(getTranslatedMessage("§cCREATIF + SNEAK + CLIC DROIT (main vide) : Verrouiller ouverte/Fermée.", "§cCREATIVE + SNEAK + RIGHT CLICK (empty hand): Toggle permanently open.")));
	    }
	
	    public void updateStage(Level world, BlockPos pos, int newStage) {
	        BlockState state = world.getBlockState(pos);
	        if (state.getBlock() instanceof DefenseDoorBlock) {
	            if (state.getValue(PERMANENTLY_OPEN)) return;
	            int currentStage = state.getValue(STAGE);
	            if (newStage > currentStage) {
	                Player player = world.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 3, false);
	                if (player != null && RepairTracker.tryAddRepair(player)) {
	                    PointManager.modifyScore(player, 10);
	                }
	            }
	            if (newStage < currentStage) {
	                String soundNum = String.format("%02d", world.random.nextInt(6));
	                ResourceLocation soundId = new ResourceLocation("zombierool", "wood_snap_" + soundNum);
	                world.playSound(null, pos, SoundEvent.createVariableRangeEvent(soundId), SoundSource.BLOCKS, 1.0f, 1.0f);
	            }
	            boolean permOpen = state.getValue(PERMANENTLY_OPEN);
	            world.setBlock(pos, state.setValue(STAGE, newStage), 3);
	            updateUpperBlock(world, pos, newStage, permOpen);
	            BlockPos linkedPos = findLinkedDoor(world, pos);
	            if (linkedPos != null) {
	                BlockState linkedState = world.getBlockState(linkedPos);
	                if (linkedState.getBlock() instanceof DefenseDoorBlock) {
	                    world.setBlock(linkedPos, linkedState.setValue(STAGE, newStage), 3);
	                    updateUpperBlock(world, linkedPos, newStage, permOpen);
	                }
	            }
	        }
	    }
	
	    private BlockPos findLinkedDoor(Level level, BlockPos pos) {
	        BlockState state = level.getBlockState(pos);
	        if (!(state.getBlock() instanceof DefenseDoorBlock)) return null;
	        Direction facing = state.getValue(FACING);
	        Direction offsetDir = facing.getClockWise();
	        for (Direction side : new Direction[]{offsetDir, offsetDir.getOpposite()}) {
	            BlockPos neighborPos = pos.relative(side);
	            BlockState neighborState = level.getBlockState(neighborPos);
	            if (neighborState.getBlock() instanceof DefenseDoorBlock && neighborState.getValue(FACING) == facing) {
	                return neighborPos;
	            }
	        }
	        return null;
	    }
	
	    private void updateUpperBlock(Level world, BlockPos pos, int newStage, boolean permanentlyOpen) {
	        BlockState currentState = world.getBlockState(pos);
	        if (currentState.getValue(HALF) == DoubleBlockHalf.LOWER) {
	            BlockPos up = pos.above();
	            BlockState upState = world.getBlockState(up);
	            if (upState.getBlock() instanceof DefenseDoorBlock) {
	                world.setBlock(up, upState.setValue(STAGE, newStage).setValue(PERMANENTLY_OPEN, permanentlyOpen), 3);
	            }
	        }
	    }
	
	    public static BlockPos getDoorInRepairZone(Level level, BlockPos playerPos) {
	        BlockPos[] offsets = new BlockPos[]{playerPos.north(), playerPos.south(), playerPos.east(), playerPos.west()};
	        for (BlockPos pos : offsets) {
	            BlockState state = level.getBlockState(pos);
	            if (state.getBlock() instanceof DefenseDoorBlock) {
	                if (state.getValue(PERMANENTLY_OPEN)) return null;
	                return pos;
	            }
	        }
	        return null;
	    }
	
	    @Override
	    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
	            BlockPos belowPos = pos.below();
	            BlockState belowState = world.getBlockState(belowPos);
	            if (belowState.getBlock() instanceof DefenseDoorBlock) {
	                return getCollisionShape(belowState, world, belowPos, context);
	            }
	            return Shapes.empty();
	        }
	        int stage = state.getValue(STAGE);
	        Entity entity = (context instanceof EntityCollisionContext ecc) ? ecc.getEntity() : null;
	        if (entity == null || entity instanceof Projectile) return Shapes.empty();
	        if (entity instanceof Player player && player.isCreative()) return Shapes.empty();
	        VoxelShape shape = createDoorShape(state);
	        
	        // On retire la vérification des Crawlers pour ne pas qu'ils ignorent la collision
	        if (entity instanceof ZombieEntity) {
	            return (stage <= 0) ? Shapes.empty() : shape;
	        }
	        return shape;
	    }
	
	    @Override
	    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob mob) {
	        return BlockPathTypes.OPEN;
	    }
	
	    @Override
	    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
	    }
	}
	
	public static class DefenseDoorOpenedBlock extends Block {
	    public DefenseDoorOpenedBlock() {
	        super(BlockBehaviour.Properties.of().noCollission().noOcclusion().randomTicks());
	    }
	
	    @Override
	    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
	        migrate(level, pos, oldState);
	    }
	
	    @Override
	    public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
	        migrate(level, pos, state);
	    }
	
	    @Override
	    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
	        migrate(level, pos, state);
	    }
	
	    @Override
	    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
	        if (!level.isClientSide) migrate(level, pos, state);
	        return InteractionResult.SUCCESS;
	    }
	
	    @Override
	    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
	        if (!level.isClientSide) migrate(level, pos, state);
	    }
	
	    @Override
	    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
	        if (!level.isClientSide) migrate(level, pos, state);
	    }
	
	    private void migrate(Level level, BlockPos pos, BlockState oldState) {
	        Direction facing = Direction.NORTH;
	        DoubleBlockHalf half = DoubleBlockHalf.LOWER;
	        DoorHingeSide hinge = DoorHingeSide.LEFT;
	        boolean open = false;
	        if (oldState.hasProperty(DoorBlock.FACING)) facing = oldState.getValue(DoorBlock.FACING);
	        if (oldState.hasProperty(DoorBlock.HALF)) half = oldState.getValue(DoorBlock.HALF);
	        if (oldState.hasProperty(DoorBlock.HINGE)) hinge = oldState.getValue(DoorBlock.HINGE);
	        if (oldState.hasProperty(DoorBlock.OPEN)) open = oldState.getValue(DoorBlock.OPEN);
	        BlockState newState = ZRBlocks.DEFENSE_DOOR.get().defaultBlockState()
	                .setValue(DefenseDoorBlock.FACING, facing)
	                .setValue(DefenseDoorBlock.HALF, half)
	                .setValue(DefenseDoorBlock.HINGE, hinge)
	                .setValue(DefenseDoorBlock.OPEN, open)
	                .setValue(DefenseDoorBlock.STAGE, 0)
	                .setValue(DefenseDoorBlock.PERMANENTLY_OPEN, true)
	                .setValue(DefenseDoorBlock.CENTERED, false);
	        level.setBlock(pos, newState, 3);
	    }
	}
	
	public static class DefenseDoorRenderer implements BlockEntityRenderer<DefenseDoorBlockEntity> {
	    public DefenseDoorRenderer(BlockEntityRendererProvider.Context context) {
	    }
	
	    @Override
	    public void render(DefenseDoorBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
	        BlockState doorState = entity.getBlockState();
	        if (!(doorState.getBlock() instanceof BaseDefenseDoor)) return;
	        if (doorState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
	            return;
	        }
	        BlockState mimicState = entity.getMimicBlock();
	        if (mimicState == null) return;
	        mimicState = getConnectedStateFixed(mimicState);
	        RenderType renderType = ItemBlockRenderTypes.getRenderType(mimicState, false);
	        renderMimicFace(mimicState, doorState, poseStack, buffer, combinedLight, combinedOverlay, renderType);
	    }
	
	    private BlockState getConnectedStateFixed(BlockState mimicState) {
	        Block block = mimicState.getBlock();
	        boolean isFenceOrPane = block instanceof net.minecraft.world.level.block.CrossCollisionBlock;
	        boolean isWall = block instanceof net.minecraft.world.level.block.WallBlock;
	        if (!isFenceOrPane && !isWall) {
	            return mimicState;
	        }
	        if (isFenceOrPane) {
	            try {
	                mimicState = mimicState.setValue(net.minecraft.world.level.block.CrossCollisionBlock.EAST, true)
	                        .setValue(net.minecraft.world.level.block.CrossCollisionBlock.WEST, true)
	                        .setValue(net.minecraft.world.level.block.CrossCollisionBlock.NORTH, false)
	                        .setValue(net.minecraft.world.level.block.CrossCollisionBlock.SOUTH, false);
	            } catch (Exception ignored) {
	            }
	        } else {
	            WallSide sideHeight = WallSide.TALL;
	            WallSide sideNone = WallSide.NONE;
	            try {
	                mimicState = mimicState.setValue(net.minecraft.world.level.block.WallBlock.UP, false);
	            } catch (Exception ignored) {
	            }
	            try {
	                mimicState = mimicState.setValue(net.minecraft.world.level.block.WallBlock.EAST_WALL, sideHeight)
	                        .setValue(net.minecraft.world.level.block.WallBlock.WEST_WALL, sideHeight)
	                        .setValue(net.minecraft.world.level.block.WallBlock.NORTH_WALL, sideNone)
	                        .setValue(net.minecraft.world.level.block.WallBlock.SOUTH_WALL, sideNone);
	            } catch (Exception ignored) {
	            }
	        }
	        return mimicState;
	    }
	
	    private void renderMimicFace(BlockState mimicState, BlockState doorState, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, RenderType renderType) {
	        Direction facing = doorState.getValue(DoorBlock.FACING);
	        boolean open = doorState.getValue(DoorBlock.OPEN);
	        DoorHingeSide hinge = doorState.getValue(DoorBlock.HINGE);
	        boolean centered = doorState.hasProperty(DefenseDoorBlock.CENTERED) && doorState.getValue(DefenseDoorBlock.CENTERED);
	        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
	        poseStack.pushPose();
	        poseStack.translate(0.5, 0, 0.5);
	        float yRot = -facing.toYRot();
	        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
	        float baseThickness = 3.0f / 16.0f;
	        float scaleThickness = baseThickness + 0.005f;
	        float scaleFace = 1.005f;
	        if (centered) {
	            boolean isStructural = mimicState.getBlock() instanceof net.minecraft.world.level.block.WallBlock
	                    || mimicState.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock;
	            if (isStructural) {
	                scaleThickness = 1.0f;
	            } else {
	                scaleThickness = 8.0f / 16.0f;
	            }
	            scaleThickness += 0.002f;
	            scaleFace = 1.005f;
	            if (open) {
	                float hingeRot = (hinge == DoorHingeSide.RIGHT) ? -90f : 90f;
	                poseStack.mulPose(Axis.YP.rotationDegrees(hingeRot));
	            }
	            org.joml.Matrix3f normalMatrix = new org.joml.Matrix3f(poseStack.last().normal());
	            poseStack.scale(scaleFace, 1.0f, scaleThickness);
	            poseStack.last().normal().set(normalMatrix);
	            poseStack.translate(-0.5, 0, -0.5);
	        } else {
	            float zOffset = 0.40625f;
	            if (open) {
	                float pivotX = (hinge == DoorHingeSide.RIGHT) ? -0.5f : 0.5f;
	                float pivotZ = 0.5f;
	                float hingeRot = (hinge == DoorHingeSide.RIGHT) ? -90f : 90f;
	                poseStack.translate(pivotX, 0, pivotZ);
	                poseStack.mulPose(Axis.YP.rotationDegrees(hingeRot));
	                poseStack.translate(-pivotX, 0, -pivotZ);
	            }
	            poseStack.translate(0, 0, zOffset);
	            org.joml.Matrix3f normalMatrix = new org.joml.Matrix3f(poseStack.last().normal());
	            poseStack.scale(scaleFace, 1.0f, scaleThickness);
	            poseStack.last().normal().set(normalMatrix);
	            poseStack.translate(-0.5, 0, -0.5);
	        }
	        BakedModel bakedModel = dispatcher.getBlockModel(mimicState);
	        VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
	        dispatcher.getModelRenderer().renderModel(
	                poseStack.last(),
	                vertexConsumer,
	                mimicState,
	                bakedModel,
	                1.0F, 1.0F, 1.0F,
	                light,
	                overlay
	        );
	        poseStack.popPose();
	    }
	}
}

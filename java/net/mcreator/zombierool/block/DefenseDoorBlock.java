package net.mcreator.zombierool.block;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.entity.Mob;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.mcreator.zombierool.ZombieroolMod;
import net.mcreator.zombierool.init.KeyBindings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.projectile.Projectile;

import net.mcreator.zombierool.RepairTracker;
import net.mcreator.zombierool.PointManager;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.events.ClientEvents;
import net.mcreator.zombierool.network.RepairBarricadeMessage;
import net.mcreator.zombierool.entity.ZombieEntity;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;

import net.minecraft.network.chat.Component; // Added missing import for Component
import net.minecraft.world.item.TooltipFlag; // Added missing import for TooltipFlag
import net.minecraft.client.Minecraft; // Added missing import for Minecraft client


public class DefenseDoorBlock extends DoorBlock {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 5);
    public static final int MAX_STAGE = 5;
    private static final BlockPos[] NEIGHBOR_OFFSETS = new BlockPos[]{
        new BlockPos(1, 0, 0),
        new BlockPos(-1, 0, 0),
        new BlockPos(0, 1, 0),
        new BlockPos(0, -1, 0),
        new BlockPos(0, 0, 1),
        new BlockPos(0, 0, -1)
    };

    public DefenseDoorBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.LADDER)
                .strength(-1, 3600000)
                .noOcclusion()
                .isRedstoneConductor((bs, br, bp) -> false)
                .dynamicShape(), BlockSetType.IRON);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(STAGE, 5));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STAGE);
    }

    /**
     * Helper method to check if the client's language is English.
     * This is crucial for dynamic translation of item names and tooltips.
     * @return true if the client's language code starts with "en", false otherwise.
     */
    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    /**
     * Helper method for dynamic translation based on the client's language.
     * @param frenchMessage The message to display if the client's language is French or not English.
     * @param englishMessage The message to display if the client's language is English.
     * @return The appropriate translated message.
     */
    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        // Add description for the DefenseDoorBlock
        list.add(Component.literal(getTranslatedMessage("§9Porte de défense renforcée", "§9Reinforced Defense Door")));
        list.add(Component.literal(getTranslatedMessage("§7Résiste aux attaques de zombies.", "§7Resists zombie attacks.")));
        list.add(Component.literal(getTranslatedMessage("§7Possède §a6 niveaux §7d'intégrité (0-5).", "§7Has §a6 stages §7of integrity (0-5).")));
        list.add(Component.literal(getTranslatedMessage("§7Peut être réparée pour gagner des points.", "§7Can be repaired to earn points.")));
        list.add(Component.literal(getTranslatedMessage("§7Les zombies la détruisent progressivement.", "§7Zombies gradually destroy it."))); // Corrected: Removed extra argument
        list.add(Component.literal(getTranslatedMessage("§7Une fois détruite (niveau 0), les zombies peuvent passer.", "§7Once destroyed (stage 0), zombies can pass.")));
        list.add(Component.literal(getTranslatedMessage("§7Indestructible pour les joueurs.", "§7Indestructible for players.")));
    }

    private BlockPos findLinkedDoor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DefenseDoorBlock)) return null;
    
        Direction facing = state.getValue(FACING);
        Direction offsetDir = facing.getClockWise(); // La direction latérale (vers la porte jumelle potentielle)
        
        // Vérifie les deux côtés latéraux (droite et gauche)
        for (Direction side : new Direction[]{offsetDir, offsetDir.getOpposite()}) {
            BlockPos neighborPos = pos.relative(side);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof DefenseDoorBlock &&
                neighborState.getValue(FACING) == facing) {
                return neighborPos;
            }
        }
        return null;
    }


   public void updateStage(Level world, BlockPos pos, int newStage) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof DefenseDoorBlock) {
            int currentStage = state.getValue(STAGE);
            
             // Donne pts pour chaque réparation jusqu'à 6 tout les 5 minutes
            if (newStage > currentStage) {
                Player player = world.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 3, false);
                if (player != null && RepairTracker.tryAddRepair(player)) {
                    PointManager.modifyScore(player, 10);
                }
            }
                        
            // Jouer le son de dégâts seulement si c'est une diminution
            if(newStage < currentStage) {
                String soundNum = String.format("%02d", world.random.nextInt(6));
                ResourceLocation soundId = new ResourceLocation("zombierool", "wood_snap_" + soundNum);
                world.playSound(null, pos, SoundEvent.createVariableRangeEvent(soundId), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            
            // Mise à jour de la porte actuelle
            world.setBlock(pos, state.setValue(STAGE, newStage), 3);
            updateUpperBlock(world, pos, newStage);
            
            // Mise à jour des portes liées
            BlockPos linkedPos = findLinkedDoor(world, pos);
            if (linkedPos != null) {
                BlockState linkedState = world.getBlockState(linkedPos);
                if (linkedState.getBlock() instanceof DefenseDoorBlock) {
                    world.setBlock(linkedPos, linkedState.setValue(STAGE, newStage), 3);
                    updateUpperBlock(world, linkedPos, newStage);
                }
            }
        }
    }

    private void updateUpperBlock(Level world, BlockPos pos, int newStage) {
        BlockPos upperPos = pos.above();
        BlockState upperState = world.getBlockState(upperPos);
        if (upperState.getBlock() instanceof DefenseDoorBlock) {
            world.setBlock(upperPos, upperState
                .setValue(STAGE, newStage)
                .setValue(FACING, upperState.getValue(FACING))
                .setValue(OPEN, upperState.getValue(OPEN)), 
                3
            );
        }
    }

    public static BlockPos getDoorInRepairZone(Level level, BlockPos playerPos) {
    // Vérifie les blocs adjacents (nord, sud, est, ouest)
        BlockPos[] offsets = new BlockPos[]{
            playerPos.north(),
            playerPos.south(),
            playerPos.east(),
            playerPos.west()
        };
        for (BlockPos pos : offsets) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DefenseDoorBlock) {
                // Optionnel : affiner la vérification en comparant le FACING si nécessaire.
                return pos;
            }
        }
        return null;
    }

    public static BlockPos findNearbyDefenseDoor(Level level, BlockPos pos) {
        for (BlockPos offset : NEIGHBOR_OFFSETS) {
            BlockState neighborState = level.getBlockState(pos.offset(offset));
            if (neighborState.getBlock() instanceof DefenseDoorBlock) {
                return pos.offset(offset);
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
    
        // Projectiles et contexte vide : pas de collision
        if (entity == null || entity instanceof Projectile) {
            return Shapes.empty();
        }
    
        // Créatif : passe à travers
        if (entity instanceof Player player && player.isCreative()) {
            return Shapes.empty();
        }
    
        // Zombie : passe si stage <= 0
        if (entity instanceof ZombieEntity) {
            return (stage <= 0) ? Shapes.empty() : createDoorShape(state);
        }
    
        // Tout le reste : bloqué
        return createDoorShape(state);
    }

    private VoxelShape createDoorShape(BlockState state) {
        Direction facing = state.getValue(FACING);
    
        if (facing.getAxis() == Direction.Axis.Z) {
            return Shapes.box(0.0D, 0.0D, 0.3125D, 1.0D, 1.0D, 0.6875D);
        } else {
            return Shapes.box(0.3125D, 0.0D, 0.0D, 0.6875D, 1.0D, 1.0D);
        }
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER)
            return Collections.emptyList();
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? 
            Shapes.empty() : 
            super.getShape(state, world, pos, context);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.LAND;
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob mob) {
        return BlockPathTypes.OPEN;
    }
    private final Map<BlockPos, Long> repairTimers = new HashMap<>();

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide) return;
    
        if (entity instanceof ZombieEntity && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            ZombieEntity zombie = (ZombieEntity) entity;
            int currentStage = state.getValue(STAGE);
            
            if (currentStage > 0) {
                CompoundTag data = zombie.getPersistentData();
                String key = "zombie_attack_time_" + pos.asLong();
                
                if (!data.contains(key)) {
                    data.putLong(key, world.getGameTime());
                } else {
                    long startTime = data.getLong(key);
                    if (world.getGameTime() - startTime >= 60) {
                        updateStage(world, pos, currentStage - 1);
                        data.remove(key);
                    }
                }
            }
        }
    }
}

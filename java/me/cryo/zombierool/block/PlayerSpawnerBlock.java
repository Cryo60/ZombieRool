package me.cryo.zombierool.block;

import me.cryo.zombierool.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.List;

public class PlayerSpawnerBlock extends AbstractTechnicalBlock {
    
    public PlayerSpawnerBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.EMPTY)
                .strength(-1, 3600000)
                .noCollission()
                .noOcclusion()
                .randomTicks()
        );
    }

    @Override
    protected void addTechnicalTooltip(List<Component> list) {
        list.add(Component.literal(getTranslatedMessage("§9Point d'Apparition du Joueur", "§9Player Spawn Point")));
        list.add(Component.literal(getTranslatedMessage("§7Définit l'endroit où les joueurs apparaîtront.", "§7Defines where players will spawn.")));
    }

    @Override
    protected VoxelShape getTechnicalCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // Pas de collision
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty()) return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) { 
        world.sendBlockUpdated(pos, state, state, 3);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        world.scheduleTick(pos, this, 20); 
        if (!world.isClientSide() && world instanceof ServerLevel serverWorld) {
		    WorldConfig.get(serverWorld).addPlayerSpawnerPosition(pos); // Changed serverLevel to serverWorld
		}
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!worldIn.isClientSide() && worldIn instanceof ServerLevel serverWorld && newState.getBlock() != state.getBlock()) {
            WorldConfig.get(serverWorld).removePlayerSpawnerPosition(pos); 
        }
        super.onRemove(state, worldIn, pos, newState, isMoving); 
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.LAND;
    }
}
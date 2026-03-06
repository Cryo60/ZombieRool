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
import java.util.Random;

public class PathBlock extends AbstractTechnicalBlock {
    public PathBlock() {
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
        list.add(Component.literal(getTranslatedMessage("§9Chemin des Zombies", "§9Zombie Path")));
        list.add(Component.literal(getTranslatedMessage("§7Définit les zones où les zombies peuvent se déplacer.", "§7Defines areas where zombies can move.")));
        list.add(Component.literal(getTranslatedMessage("§7Si un joueur peut atteindre un endroit, les zombies le peuvent aussi.", "§7If a player can reach a location, zombies can follow.")));
        list.add(Component.literal(getTranslatedMessage("§7Agit comme une 'zone jouable' pour les entités hostiles.", "§7Acts as a 'playable zone' for hostile entities.")));
        list.add(Component.literal(getTranslatedMessage("§7Invisible et non-collidable pour les joueurs en mode Survie.", "§7Invisible and non-collidable for players in Survival mode.")));
    }

    @Override
    protected VoxelShape getTechnicalCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty()) return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random random) {
        world.sendBlockUpdated(pos, state, state, 3);
        world.scheduleTick(pos, this, 20);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        world.scheduleTick(pos, this, 20);
        if (!world.isClientSide() && world instanceof ServerLevel serverLevel) {
            WorldConfig.get(serverLevel).addPathPosition(pos.immutable(), serverLevel);
        }
        super.onPlace(state, world, pos, oldState, isMoving);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide() && world instanceof ServerLevel serverLevel && state.getBlock() != newState.getBlock()) {
            WorldConfig.get(serverLevel).removePathPosition(pos.immutable(), serverLevel);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.LAND;
    }
}
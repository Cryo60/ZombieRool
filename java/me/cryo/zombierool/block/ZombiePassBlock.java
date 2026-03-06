package me.cryo.zombierool.block;

import me.cryo.zombierool.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.List;

public class ZombiePassBlock extends AbstractTechnicalBlock {
    public ZombiePassBlock() {
        super(BlockBehaviour.Properties.of()
            .instrument(NoteBlockInstrument.BASEDRUM)
            .sound(SoundType.EMPTY)
            .strength(-1, 3600000)
            .noOcclusion()
            .isSuffocating((state, world, pos) -> false)
            .isViewBlocking((state, world, pos) -> false)
        );
    }

    @Override
    protected void addTechnicalTooltip(List<Component> list) {
        list.add(Component.literal(getTranslatedMessage("§9Bloc de Passage Zombie", "§9Zombie Pass Block")));
        list.add(Component.literal(getTranslatedMessage("§7Bloque les joueurs en mode Survie.", "§7Blocks players in Survival mode.")));
        list.add(Component.literal(getTranslatedMessage("§7Laisse passer les entités hostiles (zombies, etc.).", "§7Allows hostile entities (zombies, etc.) to pass through.")));
        list.add(Component.literal(getTranslatedMessage("§7Invisible en mode Survie.", "§7Invisible in Survival mode.")));
    }

    @Override
    protected VoxelShape getTechnicalCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc) {
            Entity entity = ecc.getEntity();
            if (entity instanceof Player) {
                return Shapes.block();
            }
        }
        return Shapes.empty();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty()) return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
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
}
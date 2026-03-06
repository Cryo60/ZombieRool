package me.cryo.zombierool.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component; 
import net.minecraft.world.item.TooltipFlag; 
import net.minecraft.client.Minecraft; 

import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.client.LinkRenderer;

public class RestrictBlock extends Block {

    public RestrictBlock() {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.EMPTY)
            .strength(-1, 3600000)
            .noOcclusion() 
            .isSuffocating((state, world, pos) -> false)
            .isViewBlocking((state, world, pos) -> false)
            .lightLevel(state -> 0)
            .noLootTable()
        );
    }

    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        list.add(Component.literal(getTranslatedMessage("§9Bloc de Restriction", "§9Restriction Block")));
        list.add(Component.literal(getTranslatedMessage("§7Empêche toutes les entités de passer à travers.", "§7Prevents all entities from passing through.")));
        list.add(Component.literal(getTranslatedMessage("§7Invisible et non-collidable pour les joueurs en mode Survie (sauf projectiles).", "§7Invisible and non-collidable for players in Survival mode (except projectiles).")));
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return 0; 
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.getBlock() == this;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && (!player.isCreative() || LinkRenderer.isSurvivalViewEnabled)) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && (!player.isCreative() || LinkRenderer.isSurvivalViewEnabled)) {
                return Shapes.empty();
            }
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Entity entity = null;
        if (context instanceof EntityCollisionContext ec) {
            entity = ec.getEntity();
        }
        if (entity == null || entity instanceof Projectile || (entity instanceof Player p && p.isCreative())) {
            return Shapes.empty();
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, Mob mob) {
        return BlockPathTypes.BLOCKED;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
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
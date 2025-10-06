package net.mcreator.zombierool.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class ZombieroolWalkNodeEvaluatorMixin {

    // Initialisation des TagKeys une seule fois pour éviter les calculs répétitifs
    private static final TagKey<Block> ALLOWED_BLOCKS = TagKey.create(Registries.BLOCK, new ResourceLocation("zombierool", "allowed_blocks"));
    private static final TagKey<Block> LIMIT_BLOCKS = TagKey.create(Registries.BLOCK, new ResourceLocation("zombierool", "limit_blocks"));

    @Inject(method = "getBlockPathType", at = @At("RETURN"), cancellable = true)
    private void zombierool_getBlockPathType(BlockGetter world, int x, int y, int z, Mob entity, CallbackInfoReturnable<BlockPathTypes> cir) {
        BlockPathTypes originalType = cir.getReturnValue();

        // Si le type de chemin est déjà BLOCKED, pas besoin de vérifier davantage
        if (originalType == BlockPathTypes.BLOCKED) {
            return;
        }

        BlockPos currentPos = new BlockPos(x, y, z);

        // Vérifier si la position actuelle est autorisée en se basant sur les blocs au-dessus ou en dessous
        if (!isYAllowed(world, currentPos)) {
            cir.setReturnValue(BlockPathTypes.BLOCKED);
        }
    }

    /**
     * Vérifie si la position Y actuelle est considérée comme "autorisée" pour le cheminement,
     * en cherchant un bloc autorisé au-dessus ou en dessous sans être bloqué par un bloc limite.
     */
    private boolean isYAllowed(BlockGetter world, BlockPos currentPos) {
        // Tentative de trouver un chemin valide en descendant
        if (canReachAllowedBlockDownward(world, currentPos)) {
            return true;
        }

        // Tentative de trouver un chemin valide en montant
        if (canReachAllowedBlockUpward(world, currentPos)) {
            return true;
        }

        return false; // Aucun chemin autorisé trouvé
    }

    /**
     * Vérifie s'il existe un 'allowed_block' en dessous de la position actuelle,
     * sans qu'un 'limit_block' ne se trouve entre eux.
     */
    private boolean canReachAllowedBlockDownward(BlockGetter world, BlockPos startPos) {
        int yCurrent = startPos.getY();
        Integer lastAllowedY = null;

        // Recherche du 'allowed_block' le plus proche en descendant
        for (int yCheck = yCurrent; yCheck >= world.getMinBuildHeight(); yCheck--) {
            BlockPos checkPos = new BlockPos(startPos.getX(), yCheck, startPos.getZ());
            if (world.getBlockState(checkPos).is(ALLOWED_BLOCKS)) {
                lastAllowedY = yCheck;
                break;
            }
        }

        if (lastAllowedY != null) {
            // Vérifier la présence de 'limit_blocks' entre le 'allowed_block' trouvé et la position de départ
            for (int yCheck = lastAllowedY + 1; yCheck <= yCurrent; yCheck++) {
                BlockPos checkPos = new BlockPos(startPos.getX(), yCheck, startPos.getZ());
                if (world.getBlockState(checkPos).is(LIMIT_BLOCKS)) {
                    return false; // Un 'limit_block' bloque le chemin
                }
            }
            return true; // Aucun 'limit_block', le chemin est valide
        }
        return false; // Pas de 'allowed_block' trouvé en dessous
    }

    /**
     * Vérifie s'il existe un 'allowed_block' au-dessus de la position actuelle,
     * sans qu'un 'limit_block' ne se trouve entre eux.
     */
    private boolean canReachAllowedBlockUpward(BlockGetter world, BlockPos startPos) {
        int yCurrent = startPos.getY();
        Integer nextAllowedY = null;

        // Recherche du 'allowed_block' le plus proche en montant
        for (int yCheck = yCurrent + 1; yCheck <= world.getMaxBuildHeight(); yCheck++) {
            BlockPos checkPos = new BlockPos(startPos.getX(), yCheck, startPos.getZ());
            if (world.getBlockState(checkPos).is(ALLOWED_BLOCKS)) {
                nextAllowedY = yCheck;
                break;
            }
        }

        if (nextAllowedY != null) {
            // Vérifier la présence de 'limit_blocks' entre la position de départ et le 'allowed_block' trouvé
            for (int yCheck = yCurrent; yCheck <= nextAllowedY; yCheck++) {
                BlockPos checkPos = new BlockPos(startPos.getX(), yCheck, startPos.getZ());
                if (world.getBlockState(checkPos).is(LIMIT_BLOCKS)) {
                    return false; // Un 'limit_block' bloque le chemin
                }
            }
            return true; // Aucun 'limit_block', le chemin est valide
        }
        return false; // Pas de 'allowed_block' trouvé au-dessus
    }
}
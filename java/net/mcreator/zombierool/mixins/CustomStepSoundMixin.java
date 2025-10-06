package net.mcreator.zombierool.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.block.Blocks; // Importe Blocks pour vérifier des types de blocs spécifiques
// Removed direct import for ZombieroolModBlocks as we will use ForgeRegistries
import net.minecraftforge.registries.ForgeRegistries; // Required for accessing blocks by ResourceLocation
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class CustomStepSoundMixin extends LivingEntity {

    protected CustomStepSoundMixin() {
        super(null, null); // Constructeur factice requis pour le Mixin
    }

    @Inject(
        method = "playStepSound",
        at = @At("HEAD"),
        cancellable = true
    )
    private void zombierool_playCustomStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        SoundType soundType = state.getSoundType();
        boolean isSprinting = this.isSprinting();
        Level level = this.level();

        SoundEvent customSound = null;
        float pitch = soundType.getPitch(); // Hauteur de son par défaut, sera ajustée si nécessaire

        BlockPos headPos = new BlockPos((int)this.getX(), (int)(this.getY() + this.getEyeHeight()), (int)this.getZ());
        boolean isExposedToSky = level.canSeeSky(headPos);

        // Vérifie si le joueur est dans l'Overworld et s'il pleut dehors
        boolean isRainingOutdoors = level.isRainingAt(pos)
                                   && level.dimensionTypeId().location().equals(BuiltinDimensionTypes.OVERWORLD.location())
                                   && isExposedToSky;

        // Logique pour déterminer le son de pas personnalisé en fonction du SoundType et des conditions
        // Les vérifications de blocs spécifiques (brique, béton, glace) sont placées avant les SoundType génériques
        if (state.is(Blocks.BRICKS)) { // Vérifie spécifiquement les blocs de brique
            // Utilise brick_runs en sprintant sur la brique, brick_steps sinon
            customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "brick_runs") : new ResourceLocation("zombierool", "brick_steps"));
        } else if (state.is(Blocks.WHITE_CONCRETE) || state.is(Blocks.ORANGE_CONCRETE) || state.is(Blocks.MAGENTA_CONCRETE) ||
                   state.is(Blocks.LIGHT_BLUE_CONCRETE) || state.is(Blocks.YELLOW_CONCRETE) || state.is(Blocks.LIME_CONCRETE) ||
                   state.is(Blocks.PINK_CONCRETE) || state.is(Blocks.GRAY_CONCRETE) || state.is(Blocks.LIGHT_GRAY_CONCRETE) ||
                   state.is(Blocks.CYAN_CONCRETE) || state.is(Blocks.PURPLE_CONCRETE) || state.is(Blocks.BLUE_CONCRETE) ||
                   state.is(Blocks.BROWN_CONCRETE) || state.is(Blocks.GREEN_CONCRETE) || state.is(Blocks.RED_CONCRETE) ||
                   state.is(Blocks.BLACK_CONCRETE)) { // Vérifie tous les blocs de béton
            // Utilise concrete_runs en sprintant sur le béton, concrete_steps sinon
            customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "concrete_runs") : new ResourceLocation("zombierool", "concrete_steps"));
        } else if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) ||
                   state.is(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool", "permafrost_grass_block"))) ||
                   state.is(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool", "permafrost_ice_block")))) {
            // Using ForgeRegistries to get Block instance by ResourceLocation (registry name)
            // Assuming the registry names are "permafrost_grass_block" and "permafrost_ice_block"
            customSound = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ice_steps"));
            if (isSprinting) {
                pitch *= 1.3f; // Accélère la hauteur pour le sprint sur la glace
            }
        } else if (soundType == SoundType.GRASS) {
            if (isRainingOutdoors) {
                // Utilise mud_runs en sprintant sur l'herbe sous la pluie, mud_steps sinon
                customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "mud_runs") : new ResourceLocation("zombierool", "mud_steps"));
            } else {
                // Sons d'herbe normaux quand il ne pleut pas
                customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "grass_runs") : new ResourceLocation("zombierool", "grass_steps"));
            }
        } else if (soundType == SoundType.STONE || soundType == SoundType.DEEPSLATE || soundType == SoundType.BASALT || soundType == SoundType.NETHER_WOOD) {
            // Utilise stone_runs en sprintant sur les blocs de type pierre, stone_steps sinon
            customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "stone_runs") : new ResourceLocation("zombierool", "stone_steps"));
        } else if (soundType == SoundType.METAL || soundType == SoundType.ANVIL || soundType == SoundType.CHAIN) {
            // Métal : Utilise metal_steps et ajuste le pitch pour simuler la course
            customSound = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "metal_steps"));
            if (isSprinting) {
                pitch *= 1.3f; // Accélère la hauteur pour le sprint sur le métal
            }
            // Pas de modification de pitch pour metal_steps en marche (pitch par défaut)
        } else if (soundType == SoundType.ROOTS || soundType == SoundType.MOSS || soundType == SoundType.MUD) {
            // Sons de terre
            customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "dirt_runs") : new ResourceLocation("zombierool", "dirt_steps"));
        } else if (soundType == SoundType.GRAVEL || soundType == SoundType.SAND || soundType == SoundType.TUFF) {
            // Sons de gravier/sable
            customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "gravel_runs") : new ResourceLocation("zombierool", "gravel_steps"));
        } else if (soundType == SoundType.SNOW || soundType == SoundType.POWDER_SNOW) {
            // Sons de neige
            customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "snow_runs") : new ResourceLocation("zombierool", "snow_steps"));
        } else if (soundType == SoundType.WOOD || soundType == SoundType.BAMBOO_WOOD || soundType == SoundType.BAMBOO || soundType == SoundType.SCAFFOLDING || soundType == SoundType.LADDER) {
            // Sons de bois
            customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "wood_runs") : new ResourceLocation("zombierool", "wood_steps"));
        } else if (soundType == SoundType.WOOL || soundType == SoundType.SLIME_BLOCK) {
            // Doux : Utilise soft_steps et ajuste le pitch pour simuler la course
            customSound = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "soft_steps"));
            if (isSprinting) {
                pitch *= 1.3f; // Accélère la hauteur pour le sprint sur les blocs doux
            }
            // Pas de modification de pitch pour soft_steps en marche (pitch par défaut)
        } else if (soundType == SoundType.GLASS || soundType == SoundType.AMETHYST) {
            // Sons de verre/cristal
            customSound = SoundEvent.createVariableRangeEvent(isSprinting ? new ResourceLocation("zombierool", "glass_runs") : new ResourceLocation("zombierool", "glass_steps"));
        }
        // Les autres SoundTypes non explicitement traités ici utiliseront le comportement vanilla.

        if (customSound != null) {
            // Joue le son personnalisé
            this.level().playSound(null, pos, customSound, SoundSource.PLAYERS, soundType.getVolume() * 0.9f, pitch);
            ci.cancel(); // Annule l'exécution de la méthode originale (son vanilla)
        } else {
            // Si aucun son personnalisé n'est défini, le son vanilla sera joué
            // C'est le comportement par défaut si aucune condition ci-dessus n'est remplie.
        }
    }
}

package me.cryo.zombierool.mixins;

import me.cryo.zombierool.block.WaterloggedElement;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Old palettes omit waterlogged, so the registered default must always be dry. */
@Mixin(Block.class)
public abstract class WaterloggedDefaultStateMixin {
    @ModifyVariable(method={"registerDefaultState", "m_49959_"}, at=@At("HEAD"), argsOnly=true, remap=false)
    private BlockState zr$dryDefault(BlockState state) {
        if ((Object)this instanceof WaterloggedElement && state.hasProperty(BlockStateProperties.WATERLOGGED))
            return state.setValue(BlockStateProperties.WATERLOGGED, false);
        return state;
    }
}

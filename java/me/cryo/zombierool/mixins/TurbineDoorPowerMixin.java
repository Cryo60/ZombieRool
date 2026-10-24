package me.cryo.zombierool.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DoorBlock.class)
public abstract class TurbineDoorPowerMixin {
    @Redirect(method = "neighborChanged", at = @At(value="INVOKE", target="Lnet/minecraft/world/level/Level;hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean zr$turbineDoorPower(Level level,BlockPos pos){
        if(level instanceof ServerLevel server && me.cryo.zombierool.gameplay.WardenLocks.closedTargets(server).contains(pos))return false;
        return level.hasNeighborSignal(pos)||me.cryo.zombierool.gameplay.PortableEquipment.localPower(level,pos);
    }
}

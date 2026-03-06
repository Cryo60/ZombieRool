package me.cryo.zombierool.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import me.cryo.zombierool.client.ClientSniperHandler;

@Mixin(Entity.class)
public abstract class EntityTurnMixin {

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double modifyYaw(double yRot) {
        if ((Object)this == Minecraft.getInstance().player && ClientSniperHandler.isScoping()) {
            return yRot * 0.3;
        }
        return yRot;
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double modifyPitch(double xRot) {
        if ((Object)this == Minecraft.getInstance().player && ClientSniperHandler.isScoping()) {
            return xRot * 0.3;
        }
        return xRot;
    }
}
package me.cryo.zombierool.mixins;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class TeleporterInputMixin {
    @Inject(method="handleKeybinds",at=@At("HEAD"),cancellable=true)
    private void zr$blockTeleportKeys(CallbackInfo ci){if(me.cryo.zombierool.client.TeleporterInputLock.active()){me.cryo.zombierool.client.TeleporterInputLock.clearKeys();ci.cancel();}}
}

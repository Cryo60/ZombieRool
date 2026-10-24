package me.cryo.zombierool.mixins;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class TeleporterKeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void zr$blockTeleportHotkeys(long window,int key,int scanCode,int action,int modifiers,CallbackInfo ci){
        if(me.cryo.zombierool.client.TeleporterInputLock.active()&&Minecraft.getInstance().screen==null&&key!=org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){me.cryo.zombierool.client.TeleporterInputLock.clearKeys();ci.cancel();}
    }
}

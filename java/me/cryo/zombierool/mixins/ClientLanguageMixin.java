package me.cryo.zombierool.mixins;

import me.cryo.zombierool.client.CustomLanguageLoader;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
    
    // Le jeu attend bien deux Strings (id et defaultValue)
    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void zombierool_getOrDefault(String id, String fallback, CallbackInfoReturnable<String> cir) {
        String custom = CustomLanguageLoader.getCustomTranslation(id);
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "has", at = @At("HEAD"), cancellable = true)
    private void zombierool_has(String id, CallbackInfoReturnable<Boolean> cir) {
        if (CustomLanguageLoader.hasCustomTranslation(id)) {
            cir.setReturnValue(true);
        }
    }
}
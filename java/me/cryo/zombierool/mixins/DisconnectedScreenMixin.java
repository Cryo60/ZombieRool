package me.cryo.zombierool.mixins;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin {
    @Shadow @Final private Component reason;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void zr$explainVersionMismatch(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        String text = reason.getString().toLowerCase();
        boolean versionish = text.contains("incompatible") || text.contains("incompat")
                || text.contains("outdated") || text.contains("version") || text.contains("forge");
        boolean alreadyNamed = text.contains("missing") && (text.contains("mod") || text.contains(":"));
        if (!versionish || alreadyNamed) return;
        DisconnectedScreen screen = (DisconnectedScreen) (Object) this;
        Component extra = Component.translatable("gui.zombierool.join.mismatch");
        MultiLineLabel label = MultiLineLabel.create(screen.getMinecraft().font, extra, screen.width - 40);
        label.renderCentered(graphics, screen.width / 2, screen.height - 48);
    }
}

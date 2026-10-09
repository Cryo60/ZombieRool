package me.cryo.zombierool.client.gui;

import me.cryo.zombierool.client.career.LocalCareerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderGuiEvent;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class GuiOverlay {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.options.hideGui||!me.cryo.zombierool.configuration.ZRClientConfig.showEnemiesRemaining()||!me.cryo.zombierool.gameplay.WaveManager.isGameRunning())return;
        var text=net.minecraft.network.chat.Component.translatable("hud.zombierool.enemies_left",me.cryo.zombierool.client.MatchAtmosphere.remaining());
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight(),textWidth=mc.font.width(text);
        int x=me.cryo.zombierool.client.ClientHUDHandler.hotbarRight()+7,y=h-20;
        if(x+textWidth>w-4){x=Math.max(4,w-textWidth-4);y=h-43;}
        event.getGuiGraphics().drawString(mc.font,text,x,y,0xFF4444,true);

    }

    public static void renderNotifications(GuiGraphics graphics, int screenWidth, int screenHeight, Font font) {
        List<LocalCareerManager.Notification> notifs = LocalCareerManager.activeNotifications;
        if (notifs.isEmpty()) return;

        long now = System.currentTimeMillis();
        int yOffset = screenHeight / 2 - 50; 

        for (int i = 0; i < notifs.size(); i++) {
            LocalCareerManager.Notification notif = notifs.get(i);
            long age = now - notif.spawnTime;
            
            if (age > 4000) {
                notifs.remove(i);
                i--;
                continue;
            }

            float alpha = 1.0f;
            if (age > 3000) {
                alpha = 1.0f - ((age - 3000) / 1000.0f);
            }
            
            int alphaInt = (int) (alpha * 255);
            int color = (alphaInt << 24) | (notif.color & 0xFFFFFF);
            
            int textWidth = font.width(notif.text);
            int xOffset = screenWidth - textWidth - 10; 
            
            graphics.fill(xOffset - 4, yOffset - 2, xOffset + textWidth + 4, yOffset + font.lineHeight + 2, (alphaInt / 2) << 24);
            graphics.drawString(font, notif.text, xOffset, yOffset, color, true);

            yOffset += 16;
        }
    }
}
package me.cryo.zombierool.client;

import me.cryo.zombierool.gameplay.PointManager;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.item.BulletVestTier1Item;
import me.cryo.zombierool.api.IReloadable;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientHUDHandler {

    private static int lastHotbarRight=-1;
    public static int hotbarRight(){
        Minecraft mc=Minecraft.getInstance();
        boolean custom=mc.player!=null&&WaveManager.isGameRunning()&&!mc.player.isCreative()&&!mc.player.isSpectator()&&!ClientSniperHandler.isScoping();
        return custom&&lastHotbarRight>=0?lastHotbarRight:mc.getWindow().getGuiScaledWidth()/2+91;
    }

    private static long startGameAnimationStartTime = -1;
    private static int startGameAnimationWave = 0;
    private static final long START_ANIM_FADE_DURATION_TICKS = 40;
    private static final long START_ANIM_TRANSLATION_DURATION_TICKS = 30;
    private static final long START_ANIM_TOTAL_DURATION_TICKS = START_ANIM_FADE_DURATION_TICKS + START_ANIM_TRANSLATION_DURATION_TICKS;
    private static final float START_ANIM_TEXT_SCALE = 2.5f;

    private static long waveChangeAnimationStartTime = -1;
    private static int waveChangeFromWave = 0;
    private static int waveChangeToWave = 0;
    private static final long WAVE_BLINK_DURATION = 60;

    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("textures/gui/widgets.png");

    @SubscribeEvent
    public static void onClientLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        lastHotbarRight=-1;lastSelected=-1;selectionUntil=0;
        ClassicZombieHUD.reset();
        startGameAnimationStartTime = -1;
        waveChangeAnimationStartTime = -1;
        startGameAnimationWave = 0;
        waveChangeFromWave = 0;
        waveChangeToWave = 0;
    }

    public static void resetRoundAnimations(){startGameAnimationStartTime=-1;waveChangeAnimationStartTime=-1;startGameAnimationWave=0;waveChangeFromWave=0;waveChangeToWave=0;}

    public static void triggerStartGameAnimation(int wave) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            startGameAnimationStartTime = mc.level.getGameTime();
            startGameAnimationWave = wave;
            waveChangeAnimationStartTime = -1;
            WaveManager.setClientWave(wave);
        }
    }

    public static void triggerWaveChangeAnimation(int fromWave, int toWave) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            waveChangeAnimationStartTime = mc.level.getGameTime();
            waveChangeFromWave = fromWave;
            waveChangeToWave = toWave;
            startGameAnimationStartTime = -1;
            WaveManager.setClientWave(toWave);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.options.hideGui) return;

        if(event.getOverlay().id().equals(VanillaGuiOverlay.AIR_LEVEL.id())){event.setCanceled(true);return;}
        if(me.cryo.zombierool.client.ClassicZombieHUD.enabled()){
            String id=event.getOverlay().id().toString();
            if(id.startsWith("tacz:")&&(id.contains("gun_hud")||id.contains("heat_bar"))){event.setCanceled(true);return;}
            if(event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())||event.getOverlay().id().equals(VanillaGuiOverlay.ARMOR_LEVEL.id())||event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())||event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())){event.setCanceled(true);return;}
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            if (WaveManager.isGameRunning() && !player.isCreative() && !player.isSpectator()) {
                event.setCanceled(true);
                renderCustomHotbar(event.getGuiGraphics(), mc, player);
            }
        }
    }

    private static int lastSelected=-1;
    private static long selectionUntil;
    private static void renderCustomHotbar(GuiGraphics gui,Minecraft mc,LocalPlayer player){
        long now=mc.level.getGameTime();
        int selected=player.getInventory().selected;
        if(lastSelected!=selected){lastSelected=selected;selectionUntil=now+35;}
        lastHotbarRight=mc.getWindow().getGuiScaledWidth()-180;
        if(now>=selectionUntil&&WeaponFacade.isWeapon(player.getMainHandItem()))return;
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        java.util.List<Integer> slots=new java.util.ArrayList<>();
        for(int i=0;i<9;i++)if(!player.getInventory().getItem(i).isEmpty()||i==selected)slots.add(i);
        int x=(w-slots.size()*22)/2;
        for(int slot:slots){
            gui.fill(x,h-23,x+20,h-3,slot==selected?0xA0371518:0x80202022);
            if(slot==selected)gui.fill(x,h-4,x+20,h-3,0xFFB74640);
            var stack=player.getInventory().getItem(slot);gui.renderItem(stack,x+2,h-21);gui.renderItemDecorations(mc.font,stack,x+2,h-21);x+=22;
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.options.hideGui) return;

        GuiGraphics gui = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        if(ClassicZombieHUD.enabled())ClassicZombieHUD.render(gui,mc,player,width,height);
        else {renderTopLeftHUD(gui,mc,player);renderBottomRightHUD(gui,mc,player,width,height);}
        renderWave(gui, mc, width, height);

        // Removed renderWallWeaponHint. Messages are now managed by KeyInputHandler in activeHUDMessages.
        me.cryo.zombierool.client.gui.GuiOverlay.renderNotifications(gui, width, height, mc.font);
    }

    private static void renderTopLeftHUD(GuiGraphics gui, Minecraft mc, LocalPlayer player) {
        int yPosTopLeft = 10;

        int score = PointManager.getScore(player);
        String scoreText = Component.translatable("zombierool.hud.score").getString();
        if (scoreText.equals("zombierool.hud.score")) scoreText = "Score";
        scoreText = scoreText + ": " + score;

        gui.drawString(mc.font, scoreText, 10, yPosTopLeft, 0xFFFFFF, true);

        PointManager.PointGainInfo gainInfo = PointManager.getLastPointGain(player);
        if (gainInfo != null) {
            long timeElapsed = mc.level.getGameTime() - gainInfo.timestamp;
            long displayDuration = 30;

            if (timeElapsed < displayDuration) {
                float progress = (float) timeElapsed / displayDuration;
                float alpha = 1.0f - (float)Math.pow(progress, 2);
                float offsetY = progress * 10.0f;

                int baseColor = gainInfo.amount >= 0 ? 0x00FF00 : 0xFF0000;
                int color = ((int) (alpha * 255) << 24) | baseColor;

                String gainText = (gainInfo.amount >= 0 ? "+" : "") + gainInfo.amount;

                int textX = 10;
                int textY = (int) (yPosTopLeft + 12 + offsetY);

                gui.drawString(mc.font, gainText, textX, textY, color, true);
            }
        }

        int otherY = yPosTopLeft + mc.font.lineHeight + 4;
        for (net.minecraft.client.multiplayer.PlayerInfo pInfo : mc.getConnection().getListedOnlinePlayers()) {
            if (pInfo.getProfile().getId().equals(player.getUUID())) continue;
            String otherName = pInfo.getProfile().getName();
            me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PlayerStatsManager.PlayerStats stats =
                me.cryo.zombierool.core.capability.ZombieCapabilitySystem.ClientTabListRenderer.clientStats.get(pInfo.getProfile().getId());
            int otherScore = stats != null ? stats.score : 0;
            gui.drawString(mc.font, otherName + ": " + otherScore, 10, otherY, 0xAAAAAA, true);
            otherY += mc.font.lineHeight + 2;
        }
    }

    private static void renderBottomRightHUD(GuiGraphics gui, Minecraft mc, LocalPlayer player, int width, int height) {
        int paddingX = 10;
        int bottomPadding = 20;
        int spacingBetweenElements = 5;

        int drawY = height - bottomPadding;
        int textHeight = mc.font.lineHeight;
        int barHeight = 8;
        int barWidth = 80;

        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestplate.getItem() instanceof BulletVestTier1Item.Chestplate) {
            int armorPoints = chestplate.getOrCreateTag().getInt("BulletVestArmorPoints");
            String bulletVestText = "🛡 " + armorPoints + "/4";
            int textWidthDisplay = mc.font.width(bulletVestText);
            int bulletVestX = width - textWidthDisplay - paddingX;
            drawY -= textHeight;
            gui.drawString(mc.font, Component.literal(bulletVestText), bulletVestX, drawY, 0xFFFFFF, true);
            drawY -= spacingBetweenElements;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !WeaponFacade.isWeapon(held)) {
            renderLethalGrenades(gui, mc, player, width, drawY, paddingX, textHeight);
            return;
        }

        if (WeaponFacade.isTaczWeapon(held)) {
            renderLethalGrenades(gui, mc, player, width, drawY, paddingX, textHeight);
            return;
        }

        boolean hasDurability = held.getItem() instanceof WeaponSystem.BaseGunItem gunD && gunD.hasDurability();
        boolean hasOverheat = held.getItem() instanceof WeaponSystem.BaseGunItem gunO && gunO.hasOverheat();

        if (hasDurability) {
            WeaponSystem.BaseGunItem gun = (WeaponSystem.BaseGunItem) held.getItem();
            int dur = gun.getDurability(held);
            int maxDur = gun.getMaxDurability(held);

            int barY = drawY - barHeight;
            int textY = barY - textHeight - 2;

            int x = width - barWidth - paddingX;
            float ratio = (float) dur / maxDur;
            int filledWidth = (int)(barWidth * ratio);

            int borderColor = 0xFF555555;
            int fillColor = ratio > 0.6f ? 0xFF00FF00 : (ratio > 0.3f ? 0xFFFFFF00 : 0xFFFF0000);

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String durTxt = Component.translatable(held.getItem() instanceof me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem?"zombierool.hud.durability":"zombierool.hud.energy").getString();
            if (durTxt.equals("zombierool.hud.energy")) durTxt = "Energy";
            String fullText = durTxt + ": " + dur + " / " + maxDur;
            int textWidthDisplay = mc.font.width(fullText);

            gui.drawString(mc.font, fullText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;
        }

        if (hasOverheat) {
            WeaponSystem.BaseGunItem gun = (WeaponSystem.BaseGunItem) held.getItem();
            int heat = gun.getOverheat(held);
            int maxHeat = gun.getMaxOverheat();

            int barY = drawY - barHeight;
            int textY = barY - textHeight - 2;

            int x = width - barWidth - paddingX;
            float ratio = (float) heat / maxHeat;
            int filledWidth = (int)(barWidth * ratio);

            int borderColor = 0xFF555555;
            int fillColor = ratio < 0.3f ? 0xFF00FF00 : (ratio < 0.6f ? 0xFFFFFF00 : 0xFFFF0000);

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String heatTxt = Component.translatable("zombierool.hud.overheat").getString();
            if (heatTxt.equals("zombierool.hud.overheat")) heatTxt = "Overheat";
            String fullText = heatTxt + ": " + (int)(ratio * 100) + "%";
            int textWidthDisplay = mc.font.width(fullText);

            gui.drawString(mc.font, fullText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;
        }

        if (!hasDurability && !hasOverheat) {
            int currentAmmo = WeaponFacade.getAmmo(held);
            int maxAmmo = WeaponFacade.getMaxAmmo(held);
            int reserveAmmo = WeaponFacade.getReserve(held);

            boolean isInfinite = false;
            if (held.getItem() instanceof IReloadable rel) isInfinite = rel.isInfinite(held);

            String currentAmmoStr;
            if (held.getItem() instanceof WeaponSystem.BaseGunItem gun && gun.isAkimbo(held)) {
                int leftAmmo = gun.getAmmoLeft(held);
                currentAmmoStr = leftAmmo + " | " + currentAmmo;
            } else {
                currentAmmoStr = String.valueOf(currentAmmo);
            }

            String maxAmmoStr = " / " + maxAmmo;
            String reserveAmmoText = isInfinite ? "∞" : String.valueOf(reserveAmmo);

            float scaleFactor = 0.7f;
            int mainAmmoTextY = drawY - textHeight;

            int currentAmmoWidth = mc.font.width(currentAmmoStr);
            int maxAmmoWidth = mc.font.width(maxAmmoStr);

            int targetRightX = width - paddingX;
            int maxAmmoTextX = targetRightX - maxAmmoWidth;
            int currentAmmoTextX = maxAmmoTextX - currentAmmoWidth;

            gui.drawString(mc.font, currentAmmoStr, currentAmmoTextX, mainAmmoTextY, 0xFFFFFF, false);
            gui.drawString(mc.font, maxAmmoStr, maxAmmoTextX, mainAmmoTextY, 0xFFFFFF, false);

            gui.pose().pushPose();
            int unscaledReserveTextWidth = mc.font.width(reserveAmmoText);
            int reserveAmmoTextScaledX = (int) ((targetRightX - (unscaledReserveTextWidth * scaleFactor)) / scaleFactor);
            int reserveAmmoTextScaledY = (int) ((mainAmmoTextY + textHeight + 2) / scaleFactor);

            gui.pose().scale(scaleFactor, scaleFactor, scaleFactor);
            gui.drawString(mc.font, reserveAmmoText, reserveAmmoTextScaledX, reserveAmmoTextScaledY, 0xFFFFFF, false);
            gui.pose().popPose();

            drawY = mainAmmoTextY - spacingBetweenElements;
        }

        renderLethalGrenades(gui, mc, player, width, drawY, paddingX, textHeight);
    }

    private static void renderLethalGrenades(GuiGraphics gui, Minecraft mc, LocalPlayer player, int width, int drawY, int paddingX, int textHeight) {
        LunarEquipmentClient.equipmentIcon(gui,mc,player,width-paddingX-90,drawY-textHeight-66,false);
        player.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            String[] types={cap.getEquipmentType(),cap.getTacticalType()};
            int[] counts={cap.getEquipmentCount(),cap.getTacticalCount()};
            var keys=new net.minecraft.client.KeyMapping[]{me.cryo.zombierool.init.KeyBindings.EQUIPMENT_KEY,me.cryo.zombierool.init.KeyBindings.TACTICAL_GRENADE_KEY};
            for(int i=0;i<types.length;i++){
                var id=net.minecraft.resources.ResourceLocation.tryParse(types[i]);var item=id==null?null:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
                if(item==null||item==net.minecraft.world.item.Items.AIR)continue;
                int x=width-paddingX-90,y=drawY-textHeight-22*(i+1);
                gui.renderItem(new net.minecraft.world.item.ItemStack(item),x,y);
                gui.drawString(mc.font,keys[i].getTranslatedKeyMessage().getString()+" x"+counts[i],x+18,y+8,counts[i]>0?0xFFFFFF:0x888888,false);
            }
            int lethals = cap.getLethalCount();
            String type = cap.getLethalType();

            if (lethals > 0 && type != null && !type.isEmpty()) {
                net.minecraft.world.item.Item lethalItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(type));
                if (lethalItem != null && lethalItem != net.minecraft.world.item.Items.AIR) {
                    int grenadeX = width - paddingX - 90;
                    int grenadeY = drawY - textHeight;

                    gui.renderItem(new net.minecraft.world.item.ItemStack(lethalItem), grenadeX, grenadeY);
                    gui.drawString(mc.font, "x" + lethals, grenadeX + 18, grenadeY + 8, 0xFFFFFF, false);
                }
            }
        });
    }

    private static void renderWave(GuiGraphics gui, Minecraft mc, int width, int height) {
        long time = mc.level.getGameTime();
        String mancheText = Component.translatable("zombierool.hud.round").getString();
        int waveToDisplay = WaveManager.getCurrentWave();
        if(WaveManager.isIntroPhase())return;
        int color = 0xA80000;

        if (startGameAnimationStartTime != -1) {
            long timeElapsed = time - startGameAnimationStartTime;

            if (timeElapsed <= START_ANIM_TOTAL_DURATION_TICKS + 1) {
                String waveChar = getWaveDisplayText(startGameAnimationWave);

                float scaledMancheWidth = mc.font.width(mancheText) * START_ANIM_TEXT_SCALE;
                float scaledWaveCharWidth = mc.font.width(waveChar) * START_ANIM_TEXT_SCALE;
                float scaledHeight = mc.font.lineHeight * START_ANIM_TEXT_SCALE;

                float centerMancheX = (width / 2f) - (scaledMancheWidth / 2f);
                float centerMancheY = (height / 2f) - scaledHeight;

                float centerWaveCharX = (width / 2f) - (scaledWaveCharWidth / 2f);
                float centerWaveCharY = (height / 2f);

                int whiteColor = 0xFFFFFFFF;
                int redColor = 0xFFFF0000;

                gui.pose().pushPose();

                if (timeElapsed < START_ANIM_FADE_DURATION_TICKS) {
                    float progress = (float) timeElapsed / START_ANIM_FADE_DURATION_TICKS;
                    int currentColor = interpolateColor(whiteColor, redColor, progress);

                    gui.pose().pushPose();
                    gui.pose().translate(centerMancheX, centerMancheY, 0);
                    gui.pose().scale(START_ANIM_TEXT_SCALE, START_ANIM_TEXT_SCALE, START_ANIM_TEXT_SCALE);
                    gui.drawString(mc.font, mancheText, 0, 0, currentColor, true);
                    gui.pose().popPose();

                    gui.pose().pushPose();
                    gui.pose().translate(centerWaveCharX, centerWaveCharY, 0);
                    gui.pose().scale(START_ANIM_TEXT_SCALE, START_ANIM_TEXT_SCALE, START_ANIM_TEXT_SCALE);
                    gui.drawString(mc.font, waveChar, 0, 0, currentColor, true);
                    gui.pose().popPose();

                } else {
                    long translationTime = timeElapsed - START_ANIM_FADE_DURATION_TICKS;
                    float progress = Math.min(1.0f, (float) translationTime / START_ANIM_TRANSLATION_DURATION_TICKS);

                    float ease = progress * progress * (3 - 2 * progress);

                    float startScale = START_ANIM_TEXT_SCALE;
                    float endScale = 1.5f;
                    float currentScale = startScale + (endScale - startScale) * ease;

                    float currentX = centerMancheX + (10f - centerMancheX) * ease;
                    float currentY = centerMancheY + ((height - 40f) - centerMancheY) * ease;

                    int currentAnimColor = interpolateColor(redColor, 0xFFA80000, ease);

                    gui.pose().pushPose();
                    gui.pose().translate(currentX, currentY, 0);
                    gui.pose().scale(currentScale, currentScale, currentScale);
                    gui.drawString(mc.font, mancheText + " " + waveChar, 0, 0, currentAnimColor, true);
                    gui.pose().popPose();
                }

                gui.pose().popPose();
                return;
            } else {
                startGameAnimationStartTime = -1;
            }
        }

        if (waveChangeAnimationStartTime != -1 && time - waveChangeAnimationStartTime < WAVE_BLINK_DURATION) {
            long elapsed = time - waveChangeAnimationStartTime;
            boolean blinkWhite = (elapsed / 10) % 2 == 0;
            if (blinkWhite) color = 0xFFFFFF;

            if (elapsed < WAVE_BLINK_DURATION - 5) {
                waveToDisplay = Math.max(1, waveChangeFromWave);
                if (waveChangeFromWave == 0) waveToDisplay = waveChangeToWave;
            } else {
                waveToDisplay = waveChangeToWave;
            }
        } else if (waveChangeAnimationStartTime != -1 && time - waveChangeAnimationStartTime >= WAVE_BLINK_DURATION) {
            waveChangeAnimationStartTime = -1;
        }

        if (waveToDisplay <= 0) return;

        String waveText = mancheText;
        String numText = String.valueOf(waveToDisplay);

        gui.pose().pushPose();
        float scale = 1.5f;
        gui.pose().translate(10, height - 40, 0);
        gui.pose().scale(scale, scale, scale);
        gui.drawString(mc.font, waveText + " " + numText, 0, 0, color, true);
        gui.pose().popPose();
    }

    private static String getWaveDisplayText(int wave) {
        if (wave <= 0) return "";
        if (wave <= 12) {
            StringBuilder sb = new StringBuilder();
            int fullFives = wave / 5;
            int remainder = wave % 5;
            for (int i = 0; i < fullFives; i++) sb.append("V");
            for (int i = 0; i < remainder; i++) sb.append("I");
            return sb.toString();
        } else {
            return String.valueOf(wave);
        }
    }

    private static int interpolateColor(int startColor, int endColor, float progress) {
        int startA = (startColor >> 24) & 0xFF;
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int endA = (endColor >> 24) & 0xFF;
        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;

        int a = (int) (startA + (endA - startA) * progress);
        int r = (int) (startR + (endR - startR) * progress);
        int g = (int) (startG + (endG - startG) * progress);
        int b = (int) (startB + (endB - startB) * progress);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

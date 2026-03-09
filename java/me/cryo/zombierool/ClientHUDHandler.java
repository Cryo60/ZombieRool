package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.api.IOverheatable;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.block.BuyWallWeaponBlock;
import me.cryo.zombierool.block.entity.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.api.IPackAPunchable;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.item.BulletVestTier1Item;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientHUDHandler {

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

    private static final double WALL_PURCHASE_MAX_DIST = 2.5;

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
        if (ClientSniperHandler.isScoping()) return; 

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.options.hideGui) return;

        ItemStack mainHand = player.getMainHandItem();
        if (WeaponFacade.isTaczWeapon(mainHand)) {
            ResourceLocation overlayId = event.getOverlay().id();
            
            if (overlayId.getNamespace().equals("tacz")) {
                event.setCanceled(true);
            }
            
            if (overlayId.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.PLAYER_HEALTH.id()) ||
                overlayId.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ARMOR_LEVEL.id()) ||
                overlayId.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.FOOD_LEVEL.id()) ||
                overlayId.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.AIR_LEVEL.id())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (ClientSniperHandler.isScoping()) return; 

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.options.hideGui) return;

        GuiGraphics gui = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        renderTopLeftHUD(gui, mc, player);
        renderBottomRightHUD(gui, mc, player, width, height);
        renderWave(gui, mc, width, height);
        renderWallWeaponHint(gui, mc, player, width, height);
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
            long displayDuration = 20; 

            if (timeElapsed < displayDuration) {
                float progress = (float) timeElapsed / displayDuration;
                float alpha = 1.0f - progress;
                float offsetY = progress * 15.0f; 

                int baseColor = gainInfo.amount >= 0 ? 0x00FF00 : 0xFF0000;
                int color = ((int) (alpha * 255) << 24) | baseColor;

                String gainText = (gainInfo.amount >= 0 ? "+" : "") + gainInfo.amount;
                int scoreTextWidth = mc.font.width(scoreText);
                int textX = 10 + scoreTextWidth + 5;
                int textY = (int) (yPosTopLeft - offsetY);

                gui.drawString(mc.font, gainText, textX, textY, color, true);
            }
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
        if (held.isEmpty() || !WeaponFacade.isWeapon(held)) return;

        if (WeaponFacade.isTaczWeapon(held)) return;

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

            String durTxt = Component.translatable("zombierool.hud.energy").getString();
            if (durTxt.equals("zombierool.hud.energy")) durTxt = "Énergie";
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
            if (heatTxt.equals("zombierool.hud.overheat")) heatTxt = "Surchauffe";
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
        }
    }

    private static void renderWave(GuiGraphics gui, Minecraft mc, int width, int height) {
        long time = mc.level.getGameTime();
        boolean english = Minecraft.getInstance().options.languageCode.startsWith("en");
        String mancheText = english ? "ROUND" : "MANCHE";
        int waveToDisplay = WaveManager.getCurrentWave();
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

    private static void renderWallWeaponHint(GuiGraphics gui, Minecraft mc, LocalPlayer player, int width, int height) {
        if (player.isCreative()) return;

        HitResult ray = mc.hitResult;
        if (!(ray instanceof BlockHitResult bhr)) return;

        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        var te = mc.level.getBlockEntity(pos);

        if (!(state.getBlock() instanceof BuyWallWeaponBlock) || !(te instanceof BuyWallWeaponBlockEntity be)) return;

        if (bhr.getDirection() != state.getValue(BuyWallWeaponBlock.FACING)) return;

        double dx = Math.abs(player.getX() - (pos.getX() + .5));
        double dy = Math.abs(player.getY() - (pos.getY() + .5));
        double dz = Math.abs(player.getZ() - (pos.getZ() + .5));

        if (dx > WALL_PURCHASE_MAX_DIST || dy > WALL_PURCHASE_MAX_DIST || dz > WALL_PURCHASE_MAX_DIST) return;

        int basePrice = be.getPrice();
        if (basePrice <= 0) return;

        ItemStack weaponOnWall = ItemStack.EMPTY;
        var opt = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).resolve();
        if (opt.isPresent()) {
            weaponOnWall = opt.get().getStackInSlot(0);
        } else if (be.getItemToSell() != null) {
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(be.getItemToSell());
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                weaponOnWall = new ItemStack(item);
            }
        }

        if (weaponOnWall.isEmpty()) return;

        boolean isTacz = WeaponFacade.isTaczWeapon(weaponOnWall);
        WeaponSystem.Definition def = WeaponFacade.getDefinition(weaponOnWall);

        if (!isTacz && def == null && !(weaponOnWall.getItem() instanceof IReloadable)) return;

        boolean playerHasBaseItem = false;
        boolean playerHasUpgradedItem = false;

        String wId = WeaponFacade.getWeaponId(weaponOnWall);

        for (ItemStack s : player.getInventory().items) {
            if (isTacz && WeaponFacade.isTaczWeapon(s)) {
                String wallId = weaponOnWall.getOrCreateTag().getString("GunId");
                String invId = s.getOrCreateTag().getString("GunId");
                if (wallId.equals(invId)) {
                    if (WeaponFacade.isPackAPunched(s)) playerHasUpgradedItem = true;
                    else playerHasBaseItem = true;
                }
            } else if (!isTacz && def != null && WeaponFacade.isWeapon(s)) {
                WeaponSystem.Definition d = WeaponFacade.getDefinition(s);
                if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                    if (WeaponFacade.isPackAPunched(s)) playerHasUpgradedItem = true;
                    else playerHasBaseItem = true;
                }
            } else if (!isTacz && def == null && s.getItem() == weaponOnWall.getItem()) {
                if (WeaponFacade.isPackAPunched(s)) playerHasUpgradedItem = true;
                else playerHasBaseItem = true;
            }
        }

        String wpnName;
        if (def != null) {
            wpnName = def.name;
        } else if (isTacz) {
            String gunId = weaponOnWall.getOrCreateTag().getString("GunId");
            if (!gunId.isEmpty()) {
                ResourceLocation loc = new ResourceLocation(gunId);
                String translatableKey = String.format("gun.%s.%s.name", loc.getNamespace(), loc.getPath());
                Component translated = Component.translatable(translatableKey);
                wpnName = translated.getString();
                if (wpnName.equals(translatableKey)) {
                    wpnName = loc.getPath().replace("_", " ").toUpperCase();
                }
            } else {
                wpnName = weaponOnWall.getHoverName().getString();
            }
        } else {
            wpnName = weaponOnWall.getHoverName().getString();
        }

        String msg;
        boolean english = Minecraft.getInstance().options.languageCode.startsWith("en");

        if (playerHasUpgradedItem) {
            int rechargePrice = (basePrice / 2) + 5000;
            msg = english ? 
                "Press F to reload " + wpnName + " (PAP) for " + rechargePrice + " pts" :
                "Appuyer sur F pour recharger " + wpnName + " (PAP) pour " + rechargePrice + " pts";
        } else if (playerHasBaseItem) {
            int rechargePrice = Math.max(1, basePrice / 2);
            msg = english ?
                "Press F to reload " + wpnName + " for " + rechargePrice + " pts" :
                "Appuyer sur F pour recharger " + wpnName + " pour " + rechargePrice + " pts";
        } else {
            msg = english ?
                "Press F to purchase " + wpnName + " for " + basePrice + " pts" :
                "Appuyer sur F pour acheter " + wpnName + " pour " + basePrice + " pts";
        }

        Font f = mc.font;
        gui.drawString(f, msg, (width - f.width(msg)) / 2, height / 2 + 10, 0xFFFFFF);
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
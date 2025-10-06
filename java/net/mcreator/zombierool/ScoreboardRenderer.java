package net.mcreator.zombierool;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.WaveManager;
import net.mcreator.zombierool.PointManager.PointGainInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.mcreator.zombierool.item.BulletVestTier1Item;

import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.item.EnergySwordItem;
import net.mcreator.zombierool.item.PlasmaPistolWeaponItem;
import net.mcreator.zombierool.api.IOverheatable;
import net.mcreator.zombierool.item.OldSwordWeaponItem;
import net.mcreator.zombierool.item.StormWeaponItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ScoreboardRenderer {

    // Duration for each "+points" animation in ticks
    private static final int DISPLAY_DURATION_TICKS = 20;
    // How much the text animates upwards
    private static final float ANIMATION_HEIGHT = 15.0f;

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    /**
     * Inner class to manage individual point gain animations.
     * Each instance represents a single "+points" or "-points" text that animates.
     */
    private static class AnimatedPointGain {
        public final int amount; // The point amount (+/-)
        public final long timestamp; // When this specific animation started (in game ticks)
        public final int initialColor; // The base color (e.g., green for positive, red for negative)

        /**
         * Constructor for AnimatedPointGain.
         * @param amount The points gained or lost.
         * @param timestamp The game time tick when this animation started.
         * @param initialColor The base color of the text (before alpha is applied).
         */
        public AnimatedPointGain(int amount, long timestamp, int initialColor) {
            this.amount = amount;
            this.timestamp = timestamp;
            this.initialColor = initialColor;
        }
    }

    // A list to hold all currently active point gain animations.
    // This list will be iterated through each tick to draw and update animations.
    private static final List<AnimatedPointGain> activePointGainAnimations = new ArrayList<>();
    
    // A map to track the last processed PointGainInfo timestamp for each player UUID.
    // This is crucial to prevent adding the same PointGainInfo to the animation list multiple times
    // on consecutive ticks, as PointManager.getLastPointGain() might return the same info for a while.
    private static final Map<UUID, Long> lastProcessedGainTimestamp = new HashMap<>();

    // NOUVEAU: Variables pour l'animation de début de partie "MANCHE I"
    private static long startGameAnimationStartTime = -1;
    private static int startGameAnimationWave = 0;
    // Augmenter la durée pour ralentir l'animation
    private static final long START_ANIM_FADE_DURATION_TICKS = 40; // ~2 secondes
    private static final long START_ANIM_TRANSLATION_DURATION_TICKS = 30; // ~1.5 secondes
    private static final long START_ANIM_TOTAL_DURATION_TICKS = START_ANIM_FADE_DURATION_TICKS + START_ANIM_TRANSLATION_DURATION_TICKS;
    // Facteur de grossissement pour l'animation de début
    private static final float START_ANIM_TEXT_SCALE = 2.5f; 

    // NOUVEAU: Variables pour l'animation de changement de vague (clignotement)
    private static long waveChangeAnimationStartTime = -1;
    private static int waveChangeFromWave = 0; // Non strictement utilisé pour le rendu, mais bon pour le contexte
    private static int waveChangeToWave = 0;   // Non strictement utilisé pour le rendu, mais bon pour le contexte
    private static final long WAVE_CHANGE_ANIM_DURATION_TICKS = 40; // 2 secondes pour le clignotement
    private static final int WAVE_CHANGE_FLASH_INTERVAL_TICKS = 5; // Clignote toutes les 0.25 secondes (5 ticks)
    // Facteur de grossissement pour l'indication de la manche actuelle
    private static final float CURRENT_WAVE_DISPLAY_SCALE = 1.8f;


    /**
     * Déclenche l'animation de début de partie "MANCHE I".
     * @param wave La vague à afficher (normalement 1 pour le début).
     */
    public static void triggerStartGameAnimation(int wave) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) { // Ajout d'une vérification pour s'assurer que le niveau est disponible
            startGameAnimationStartTime = mc.level.getGameTime();
            startGameAnimationWave = wave;
            waveChangeAnimationStartTime = -1; // S'assure que l'animation de changement de vague est arrêtée
        }
    }

    /**
     * Déclenche l'animation de changement de vague (clignotement).
     * @param fromWave L'ancienne vague.
     * @param toWave La nouvelle vague.
     */
    public static void triggerWaveChangeAnimation(int fromWave, int toWave) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) { // Ajout d'une vérification pour s'assurer que le niveau est disponible
            waveChangeAnimationStartTime = mc.level.getGameTime();
            waveChangeFromWave = fromWave;
            waveChangeToWave = toWave;
            startGameAnimationStartTime = -1; // S'assure que l'animation de début de partie est arrêtée
            WaveManager.setClientWave(toWave); // Met à jour la vague côté client immédiatement pour le rendu normal après l'animation
        }
    }

    /**
     * Convertit un nombre de vague en un affichage en bâtons (tally marks) jusqu'à 12.
     * Au-delà de 12, le nombre arabe est utilisé.
     * @param wave Le numéro de la vague.
     * @return La représentation textuelle de la vague.
     */
    private static String getWaveDisplayText(int wave) {
        if (wave <= 0) {
            return ""; // Ou "MANCHE 0" si vous le souhaitez
        }
        // Utilisation des tally marks jusqu'à la vague 12
        if (wave <= 12) {
            StringBuilder sb = new StringBuilder();
            int fullFives = wave / 5;
            int remainder = wave % 5;

            // Pour chaque groupe de 5, ajoute la représentation "V"
            for (int i = 0; i < fullFives; i++) {
                sb.append("V");
            }
            // Pour le reste, ajoute des "I"
            for (int i = 0; i < remainder; i++) {
                sb.append("I");
            }
            return sb.toString();
        } else {
            // Au-delà de la vague 12, utilise les chiffres arabes
            return String.valueOf(wave);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // Ensure that the game is running and player is available
        if (mc.level == null || player == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        long currentTime = mc.level.getGameTime(); // Current game time in ticks
        
        // Base Y position for the scoreboard elements (score, wave)
        int yPosTopLeft = 10; 

        // Flag to indicate if an animation is currently drawing the wave text
        boolean waveTextAnimated = false;

        // --- Animations de début de partie "MANCHE I" ---
        if (startGameAnimationStartTime != -1) {
            long timeElapsed = currentTime - startGameAnimationStartTime;

            // MODIFICATION: Ajout de +1 à la durée pour assurer un chevauchement d'un tick.
            // Cela garantit que l'animation dessine son état final pendant une image supplémentaire,
            // évitant ainsi le problème de disparition.
            if (timeElapsed <= START_ANIM_TOTAL_DURATION_TICKS + 1) { 
                waveTextAnimated = true; // L'animation va dessiner le texte de la vague
                
                String mancheText = getTranslatedMessage("MANCHE", "ROUND"); // Translated
                int unscaledMancheWidth = mc.font.width(mancheText);
                int unscaledMancheHeight = mc.font.lineHeight;

                String waveChar = getWaveDisplayText(startGameAnimationWave);
                int unscaledWaveCharWidth = mc.font.width(waveChar);
                int unscaledWaveCharHeight = mc.font.lineHeight;
                
                float scaledMancheWidth = unscaledMancheWidth * START_ANIM_TEXT_SCALE;
                float scaledWaveCharWidth = unscaledWaveCharWidth * START_ANIM_TEXT_SCALE;
                float scaledMancheHeight = unscaledMancheHeight * START_ANIM_TEXT_SCALE;
                float scaledWaveCharHeight = unscaledWaveCharHeight * START_ANIM_TEXT_SCALE;

                float centerMancheX = (screenWidth / 2f) - (scaledMancheWidth / 2f);
                float centerMancheY = (screenHeight / 2f) - (scaledMancheHeight / 2f) - (scaledWaveCharHeight / 2f);

                float centerWaveCharX = (screenWidth / 2f) - (scaledWaveCharWidth / 2f);
                float centerWaveCharY = (screenHeight / 2f) + (scaledMancheHeight / 2f) - (scaledWaveCharHeight / 2f);

                long fadeDuration = START_ANIM_FADE_DURATION_TICKS;
                long translateDuration = START_ANIM_TRANSLATION_DURATION_TICKS;
                
                int whiteColor = 0xFFFFFFFF;
                int redColor = 0xFFFF0000;

                gui.pose().pushPose();

                if (timeElapsed < fadeDuration) {
                    float progress = (float) timeElapsed / fadeDuration;
                    int currentColor = interpolateColor(whiteColor, redColor, progress);

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
                    long translationTime = timeElapsed - fadeDuration;
                    // Assurez-vous que translationProgress ne dépasse jamais 1.0f
                    float translationProgress = Math.min(1.0f, (float) translationTime / translateDuration);

                    float mancheAlpha = 1.0f - translationProgress;
                    int mancheFadingColor = ((int) (mancheAlpha * 255) << 24) | (redColor & 0x00FFFFFF);
                    
                    if (mancheAlpha > 0) {
                        gui.pose().pushPose();
                        gui.pose().translate(centerMancheX, centerMancheY, 0);
                        gui.pose().scale(START_ANIM_TEXT_SCALE, START_ANIM_TEXT_SCALE, START_ANIM_TEXT_SCALE);
                        gui.drawString(mc.font, mancheText, 0, 0, mancheFadingColor, true);
                        gui.pose().popPose();
                    }
                   
                    float targetDisplayScale = CURRENT_WAVE_DISPLAY_SCALE;
                    float targetX = 10;
                    float targetYForWaveChar = (float)yPosTopLeft + mc.font.lineHeight;

                    float unscaledStartWaveCharX = (screenWidth / 2f) - (unscaledWaveCharWidth / 2f);
                    float unscaledStartWaveCharY = (screenHeight / 2f) + (unscaledMancheHeight / 2f) - (unscaledWaveCharHeight / 2f);

                    float currentWaveCharUnscaledX = unscaledStartWaveCharX + (targetX - unscaledStartWaveCharX) * translationProgress;
                    float currentWaveCharUnscaledY = unscaledStartWaveCharY + (targetYForWaveChar - unscaledStartWaveCharY) * translationProgress;

                    gui.pose().pushPose();
                    gui.pose().translate(currentWaveCharUnscaledX, currentWaveCharUnscaledY, 0);
                    float currentScale = START_ANIM_TEXT_SCALE + (targetDisplayScale - START_ANIM_TEXT_SCALE) * translationProgress;
                    gui.pose().scale(currentScale, currentScale, currentScale);
                    gui.drawString(mc.font, waveChar, 0, 0, redColor, true);
                    gui.pose().popPose();
                }
            } else {
                // L'animation est terminée, réinitialise le temps de début pour que l'affichage statique prenne le relais.
                startGameAnimationStartTime = -1;
            }
        }

        // --- Animation de changement de vague (clignotement) ---
        int currentWave = WaveManager.getCurrentWave();
        if (waveChangeAnimationStartTime != -1) {
            long timeElapsed = currentTime - waveChangeAnimationStartTime;
            // MODIFICATION: Ajout de +1 à la durée pour assurer un chevauchement d'un tick.
            if (timeElapsed < WAVE_CHANGE_ANIM_DURATION_TICKS + 1) {
                waveTextAnimated = true; // Cette animation va dessiner le texte de la vague
                boolean isWhite = (timeElapsed / WAVE_CHANGE_FLASH_INTERVAL_TICKS) % 2 == 0;
                int waveColor = isWhite ? 0xFFFFFFFF : 0xFFFF0000;

                String waveText = getWaveDisplayText(currentWave);
                gui.pose().pushPose();
                gui.pose().translate(10, 10 + mc.font.lineHeight, 0);
                gui.pose().scale(CURRENT_WAVE_DISPLAY_SCALE, CURRENT_WAVE_DISPLAY_SCALE, CURRENT_WAVE_DISPLAY_SCALE);
                gui.drawString(mc.font, waveText, 0, 0, waveColor, true);
                gui.pose().popPose();
            } else {
                waveChangeAnimationStartTime = -1;
            }
        }
        
        // --- Scoreboard et Affichage de la vague (Haut Gauche) ---
        Objective objective = mc.level.getScoreboard().getObjective(ScoreboardHandler.OBJECTIVE_ID);
        if (objective == null) return;

        int score = PointManager.getScore(player);
        
        gui.drawString(mc.font, getTranslatedMessage("Score: ", "Score: ") + score, 10, yPosTopLeft, 0xFFFFFF, true); // Translated

        // Affiche la vague permanente uniquement si AUCUNE animation n'est active pour le texte de la vague.
        if (!waveTextAnimated) { // Utilise le flag ici
            String waveText = getWaveDisplayText(currentWave);
            gui.pose().pushPose();
            gui.pose().translate(10, yPosTopLeft + mc.font.lineHeight, 0);
            gui.pose().scale(CURRENT_WAVE_DISPLAY_SCALE, CURRENT_WAVE_DISPLAY_SCALE, CURRENT_WAVE_DISPLAY_SCALE);
            gui.drawString(mc.font, waveText, 0, 0, 0xFFFF0000, true);
            gui.pose().popPose();
        }

        // --- Point Gain Animation Logic ---
        PointManager.PointGainInfo currentLastGain = PointManager.getLastPointGain(player);
        
        UUID playerUUID = player.getUUID();

        if (currentLastGain != null) {
            long lastProcessedTimestamp = lastProcessedGainTimestamp.getOrDefault(playerUUID, -1L);

            if (currentLastGain.timestamp > lastProcessedTimestamp) {
                int gainColor = 0x00FF00;
                if (currentLastGain.amount < 0) {
                    gainColor = 0xFF0000;
                }
                activePointGainAnimations.add(new AnimatedPointGain(currentLastGain.amount, currentTime, gainColor));
                lastProcessedGainTimestamp.put(playerUUID, currentLastGain.timestamp);
            }
        }

        Iterator<AnimatedPointGain> iterator = activePointGainAnimations.iterator();
        while (iterator.hasNext()) {
            AnimatedPointGain anim = iterator.next();
            long timeSinceAnimStart = currentTime - anim.timestamp;

            if (timeSinceAnimStart < DISPLAY_DURATION_TICKS) {
                float progress = (float) timeSinceAnimStart / DISPLAY_DURATION_TICKS;
                float alpha = 1.0f - progress;
                int animatedColor = ((int) (alpha * 255) << 24) | (anim.initialColor & 0x00FFFFFF);

                float offsetY = progress * ANIMATION_HEIGHT;

                String gainText = (anim.amount >= 0 ? "+" : "") + anim.amount;

                int scoreTextWidth = mc.font.width(getTranslatedMessage("Score: ", "Score: ") + score); // Recalculated for translation
                int textX = 10 + scoreTextWidth + 5;

                int textY = (int) (yPosTopLeft - offsetY);

                gui.drawString(mc.font, gainText, textX, textY, animatedColor, true);
            } else {
                iterator.remove();
            }
        }

        // --- Bottom-Right Display: Durability AND/OR Overheat AND/OR Ammo AND Bullet Vest ---
        ItemStack heldItem = player.getMainHandItem();

        int paddingX = 10;
        int bottomPadding = 20;
        int spacingBetweenElements = 5;

        int drawY = screenHeight - bottomPadding;
        
        int textHeight = mc.font.lineHeight;
        int barHeight = 8;
        int barWidth = 80;

        int filledWidth;
        int textWidthDisplay;
        int x;
        int textY; // Declare textY here

        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestplate.getItem() instanceof BulletVestTier1Item.Chestplate) {
            int armorPoints = chestplate.getOrCreateTag().getInt("BulletVestArmorPoints");

            String bulletVestText = getTranslatedMessage("🛡 ", "🛡 ") + armorPoints + "/4"; // Translated
            textWidthDisplay = mc.font.width(bulletVestText);
            
            int bulletVestX = screenWidth - textWidthDisplay - paddingX;

            drawY -= textHeight;
            int bulletVestY = drawY;

            gui.drawString(mc.font, Component.literal(bulletVestText), bulletVestX, bulletVestY, 0xFFFFFF, true);

            drawY -= spacingBetweenElements; 
        }

        if (heldItem.getItem() instanceof StormWeaponItem stormWeapon) {
            int currentDurability = stormWeapon.getDurability(heldItem);
            int maxDurability = stormWeapon.getMaxDurability(heldItem);

            int barY = drawY - barHeight;
            textY = barY - textHeight - 2; // Assign value

            x = screenWidth - barWidth - paddingX;

            float durabilityPercentage = (float) currentDurability / maxDurability;
            filledWidth = (int)(barWidth * durabilityPercentage);

            int borderColor = 0xFF555555;
            int fillColor;

            if (durabilityPercentage > 0.6f) {
                fillColor = 0xFF00FF00;
            } else if (durabilityPercentage > 0.3f) {
                fillColor = 0xFFFFFF00;
            } else {
                fillColor = 0xFFFF0000;
            }

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String durabilityText = getTranslatedMessage("Énergie: ", "Energy: ") + currentDurability + " / " + maxDurability; // Translated
            textWidthDisplay = mc.font.width(durabilityText);
            gui.drawString(mc.font, durabilityText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;

            int currentOverheat = stormWeapon.getOverheat(heldItem);
            int maxOverheat = stormWeapon.getMaxOverheat();

            barY = drawY - barHeight;
            textY = barY - textHeight - 2; // Assign value

            x = screenWidth - barWidth - paddingX;

            float overheatPercentage = (float) currentOverheat / maxOverheat;
            filledWidth = (int)(barWidth * overheatPercentage);

            if (overheatPercentage < 0.3f) {
                fillColor = 0xFF00FF00;
            } else if (overheatPercentage < 0.6f) {
                fillColor = 0xFFFFFF00;
            } else {
                fillColor = 0xFFFF0000;
            }

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String overheatText = getTranslatedMessage("Surchauffe: ", "Overheat: ") + (int)(overheatPercentage * 100) + "%"; // Translated
            textWidthDisplay = mc.font.width(overheatText);
            gui.drawString(mc.font, overheatText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;
        }
        else if (heldItem.getItem() instanceof PlasmaPistolWeaponItem plasmaPistol) {
            int currentDurability = plasmaPistol.getDurability(heldItem);
            int maxDurability = plasmaPistol.getMaxDurability(heldItem);

            int barY = drawY - barHeight;
            textY = barY - textHeight - 2; // Assign value

            x = screenWidth - barWidth - paddingX;

            float durabilityPercentage = (float) currentDurability / maxDurability;
            filledWidth = (int)(barWidth * durabilityPercentage);

            int borderColor = 0xFF555555;
            int fillColor;

            if (durabilityPercentage > 0.6f) {
                fillColor = 0xFF00FF00;
            } else if (durabilityPercentage > 0.3f) {
                fillColor = 0xFFFFFF00;
            } else {
                fillColor = 0xFFFF0000;
            }

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String durabilityText = getTranslatedMessage("Durabilité: ", "Durability: ") + currentDurability + " / " + maxDurability; // Translated
            textWidthDisplay = mc.font.width(durabilityText);
            gui.drawString(mc.font, durabilityText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;

            int currentOverheat = plasmaPistol.getOverheat(heldItem);
            int maxOverheat = plasmaPistol.getMaxOverheat();

            barY = drawY - barHeight;
            textY = barY - textHeight - 2; // Assign value

            x = screenWidth - barWidth - paddingX;

            float overheatPercentage = (float) currentOverheat / maxOverheat;
            filledWidth = (int)(barWidth * overheatPercentage);

            if (overheatPercentage < 0.3f) {
                fillColor = 0xFF00FF00;
            } else if (overheatPercentage < 0.6f) {
                fillColor = 0xFFFFFF00;
            } else {
                fillColor = 0xFFFF0000;
            }

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String overheatText = getTranslatedMessage("Surchauffe: ", "Overheat: ") + (int)(overheatPercentage * 100) + "%"; // Translated
            textWidthDisplay = mc.font.width(overheatText);
            gui.drawString(mc.font, overheatText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;
        }
        else if (heldItem.getItem() instanceof IOverheatable overheatableItem) {
            int currentOverheat = overheatableItem.getOverheat(heldItem);
            int maxOverheat = overheatableItem.getMaxOverheat();

            int barY = drawY - barHeight;
            textY = barY - textHeight - 2; // Assign value

            x = screenWidth - barWidth - paddingX;

            float overheatPercentage = (float) currentOverheat / maxOverheat;
            filledWidth = (int)(barWidth * overheatPercentage);

            int borderColor = 0xFF555555;
            int fillColor;

            if (overheatPercentage < 0.3f) {
                fillColor = 0xFF00FF00;
            } else if (overheatPercentage < 0.6f) {
                fillColor = 0xFFFFFF00;
            } else {
                fillColor = 0xFFFF0000;
            }

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String overheatText = getTranslatedMessage("Surchauffe: ", "Overheat: ") + (int)(overheatPercentage * 100) + "%"; // Translated
            textWidthDisplay = mc.font.width(overheatText);
            gui.drawString(mc.font, overheatText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;
        }
        else if (heldItem.getItem() instanceof EnergySwordItem energySword) {
            int currentDurability = energySword.getDurability(heldItem);
            int maxDurability = energySword.getMaxDurability(heldItem);

            int barY = drawY - barHeight;
            textY = barY - textHeight - 2; // Assign value

            x = screenWidth - barWidth - paddingX;

            float durabilityPercentage = (float) currentDurability / maxDurability;
            filledWidth = (int)(barWidth * durabilityPercentage);

            int borderColor = 0xFF555555;
            int fillColor;

            if (durabilityPercentage > 0.6f) {
                fillColor = 0xFF00FF00;
            } else if (durabilityPercentage > 0.3f) {
                fillColor = 0xFFFFFF00;
            } else {
                fillColor = 0xFFFF0000;
            }

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String durabilityText = getTranslatedMessage("Durabilité: ", "Durability: ") + currentDurability + " / " + maxDurability; // Translated
            textWidthDisplay = mc.font.width(durabilityText);
            gui.drawString(mc.font, durabilityText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;
        }
        else if (heldItem.getItem() instanceof OldSwordWeaponItem oldSword) {
            int currentDurability = oldSword.getDurability(heldItem);
            int maxDurability = oldSword.getMaxDurability(heldItem);

            int barY = drawY - barHeight;
            textY = barY - textHeight - 2; // Assign value

            x = screenWidth - barWidth - paddingX;

            float durabilityPercentage = (float) currentDurability / maxDurability;
            filledWidth = (int)(barWidth * durabilityPercentage);

            int borderColor = 0xFF555555;
            int fillColor;

            if (durabilityPercentage > 0.6f) {
                fillColor = 0xFF00FF00;
            } else if (durabilityPercentage > 0.3f) {
                fillColor = 0xFFFFFF00;
            } else {
                fillColor = 0xFFFF0000;
            }

            gui.fill(x, barY, x + barWidth, barY + barHeight, borderColor);
            gui.fill(x, barY, x + filledWidth, barY + barHeight, fillColor);

            String durabilityText = getTranslatedMessage("Durabilité: ", "Durability: ") + currentDurability + " / " + maxDurability; // Translated
            textWidthDisplay = mc.font.width(durabilityText);
            gui.drawString(mc.font, durabilityText, x + (barWidth / 2) - (textWidthDisplay / 2), textY, 0xFFFFFFFF, true);

            drawY = textY - spacingBetweenElements;
        }
        else if (heldItem.getItem() instanceof IReloadable reloadableWeapon) {
            int currentAmmo = reloadableWeapon.getAmmo(heldItem);
            int maxAmmo = reloadableWeapon.getMaxAmmo();
            int reserveAmmo = reloadableWeapon.getReserve(heldItem);

            String currentAmmoStr = String.valueOf(currentAmmo);
            String maxAmmoStr = getTranslatedMessage(" / ", " / ") + String.valueOf(maxAmmo); // Translated
            String reserveAmmoText = String.valueOf(reserveAmmo);

            float scaleFactor = 0.7f;

            int mainAmmoTextY = drawY - textHeight;

            int currentAmmoWidth = mc.font.width(currentAmmoStr);
            int maxAmmoWidth = mc.font.width(maxAmmoStr);
            int targetRightX = screenWidth - paddingX;
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

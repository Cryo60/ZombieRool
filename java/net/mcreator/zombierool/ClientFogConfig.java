package net.mcreator.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
// REMOVE THIS IMPORT: import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent; // Keep if you have other @SubscribeEvent methods here
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.client.render.ZombieMoonRenderer;
// REMOVE THIS IMPORT: import net.minecraftforge.client.event.ClientChatReceivedEvent; // This one is also duplicated

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT)
public class ClientFogConfig {

    public static String currentFogPreset = "normal";
    public static boolean enableFog = true;
    public static boolean enableMoonRenderer = true;

    public static float fogNearPlane = 0.5F;
    public static float fogFarPlane = 18.0F;
    public static float fogColorRed = 0f;
    public static float fogColorGreen = 0f;
    public static float fogColorBlue = 0f;
    public static float fogColorAlpha = 0.9f;
    public static boolean enableFogPulse = true;

    // SUPPRIME TOUTE LA METHODE onClientChatReceived ICI !
    /*
    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        String rawText = message.getString();

        if (rawText.startsWith("ZOMBIEROOL_FOG_PRESET:")) {
            String preset = rawText.substring("ZOMBIEROOL_FOG_PRESET:".length());
            setFogPreset(preset);
            event.setCanceled(true);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Brouillard mis à jour : " + preset));
            }
        }

        if (rawText.startsWith("ZOMBIEROOL_FOG_PRESET:")) {
            String preset = rawText.substring("ZOMBIEROOL_FOG_PRESET:".length());
            setFogPreset(preset);
            event.setCanceled(true);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Brouillard mis à jour : " + preset));
            }
        }
    }
    */

    public static void setFogPreset(String preset) {
        currentFogPreset = preset;
        enableFog = true;
        enableMoonRenderer = true;
        enableFogPulse = false;

        switch (preset) {
            case "normal":
                fogNearPlane = 0.5F;
                fogFarPlane = 18.0F;
                fogColorRed = 0f;
                fogColorGreen = 0f;
                fogColorBlue = 0f;
                fogColorAlpha = 0.9f;
                enableFogPulse = true;
                break;
            case "dense":
                fogNearPlane = 0.1F;
                fogFarPlane = 8.0F;
                fogColorRed = 0.1f;
                fogColorGreen = 0.1f;
                fogColorBlue = 0.1f;
                fogColorAlpha = 0.95f;
                break;
            case "clear":
                fogNearPlane = 0.8F;
                fogFarPlane = 80.0F;
                fogColorRed = 0.85f;
                fogColorGreen = 0.9f;
                fogColorBlue = 1.0f;
                fogColorAlpha = 0.7f;
                break;
            case "dark":
                fogNearPlane = 0.2F;
                fogFarPlane = 10.0F;
                fogColorRed = 0.05f;
                fogColorGreen = 0.05f;
                fogColorBlue = 0.05f;
                fogColorAlpha = 1.0f;
                break;
            case "blood":
                fogNearPlane = 0.2F;
                fogFarPlane = 15.0F;
                fogColorRed = 0.5f;
                fogColorGreen = 0.05f;
                fogColorBlue = 0.05f;
                fogColorAlpha = 0.8f;
                enableFogPulse = true;
                break;
            case "nightmare":
                fogNearPlane = 0.05F;
                fogFarPlane = 5.0F;
                fogColorRed = 0.15f;
                fogColorGreen = 0.0f;
                fogColorBlue = 0.15f;
                fogColorAlpha = 1.0f;
                enableFogPulse = true;
                break;
            case "green_acid":
                fogNearPlane = 0.3F;
                fogFarPlane = 15.0F;
                fogColorRed = 0.1f;
                fogColorGreen = 0.4f;
                fogColorBlue = 0.1f;
                fogColorAlpha = 0.85f;
                break;
            case "sunrise":
                fogNearPlane = 0.6F;
                fogFarPlane = 40.0F;
                fogColorRed = 0.95f;
                fogColorGreen = 0.6f;
                fogColorBlue = 0.3f;
                fogColorAlpha = 0.8f;
                enableFogPulse = true;
                break;
            case "sunset":
                fogNearPlane = 0.6F;
                fogFarPlane = 40.0F;
                fogColorRed = 0.8f;
                fogColorGreen = 0.4f;
                fogColorBlue = 0.2f;
                fogColorAlpha = 0.85f;
                enableFogPulse = true;
                break;
            case "underwater":
                fogNearPlane = 0.1F;
                fogFarPlane = 12.0F;
                fogColorRed = 0.1f;
                fogColorGreen = 0.3f;
                fogColorBlue = 0.5f;
                fogColorAlpha = 0.9f;
                enableFogPulse = false;
                break;
            case "swamp":
                fogNearPlane = 0.3F;
                fogFarPlane = 20.0F;
                fogColorRed = 0.2f;
                fogColorGreen = 0.3f;
                fogColorBlue = 0.15f;
                fogColorAlpha = 0.88f;
                enableFogPulse = true;
                break;
            case "volcanic":
                fogNearPlane = 0.15F;
                fogFarPlane = 10.0F;
                fogColorRed = 0.3f;
                fogColorGreen = 0.1f;
                fogColorBlue = 0.05f;
                fogColorAlpha = 0.95f;
                enableFogPulse = true;
                break;
            case "mystic":
                fogNearPlane = 0.4F;
                fogFarPlane = 30.0F;
                fogColorRed = 0.4f;
                fogColorGreen = 0.2f;
                fogColorBlue = 0.6f;
                fogColorAlpha = 0.75f;
                enableFogPulse = true;
                break;
            case "toxic":
                fogNearPlane = 0.2F;
                fogFarPlane = 15.0F;
                fogColorRed = 0.1f;
                fogColorGreen = 0.6f;
                fogColorBlue = 0.2f;
                fogColorAlpha = 0.92f;
                enableFogPulse = true;
                break;
            case "dreamy":
                fogNearPlane = 0.7F;
                fogFarPlane = 60.0F;
                fogColorRed = 0.8f;
                fogColorGreen = 0.9f;
                fogColorBlue = 1.0f;
                fogColorAlpha = 0.6f;
                enableFogPulse = false;
                break;
            case "winter":
                fogNearPlane = 0.3F;
                fogFarPlane = 25.0F;
                fogColorRed = 0.7f;
                fogColorGreen = 0.75f;
                fogColorBlue = 0.85f;
                fogColorAlpha = 0.9f;
                enableFogPulse = true;
                break;
            case "haunted":
                fogNearPlane = 0.1F;
                fogFarPlane = 8.0F;
                fogColorRed = 0.1f;
                fogColorGreen = 0.05f;
                fogColorBlue = 0.1f;
                fogColorAlpha = 0.98f;
                enableFogPulse = true;
                 break;
            case "arctic":
                fogNearPlane = 0.4F;
                fogFarPlane = 35.0F;
                fogColorRed = 0.85f;
                fogColorGreen = 0.9f;
                fogColorBlue = 1.0f;
                fogColorAlpha = 0.85f;
                enableFogPulse = false;
                break;
            case "prehistoric":
                fogNearPlane = 0.2F;
                fogFarPlane = 18.0F;
                fogColorRed = 0.3f;
                fogColorGreen = 0.4f;
                fogColorBlue = 0.2f;
                fogColorAlpha = 0.95f;
                enableFogPulse = true;
                break;
            case "radioactive":
                fogNearPlane = 0.1F;
                fogFarPlane = 12.0F;
                fogColorRed = 0.4f;
                fogColorGreen = 0.8f;
                fogColorBlue = 0.2f;
                fogColorAlpha = 0.9f;
                enableFogPulse = true;
                break;
            case "desert":
                fogNearPlane = 0.5F;
                fogFarPlane = 50.0F;
                fogColorRed = 0.95f;
                fogColorGreen = 0.8f;
                fogColorBlue = 0.5f;
                fogColorAlpha = 0.7f;
                enableFogPulse = false;
                break;
            case "ashstorm":
                fogNearPlane = 0.1F;
                fogFarPlane = 6.0F;
                fogColorRed = 0.3f;
                fogColorGreen = 0.3f;
                fogColorBlue = 0.3f;
                fogColorAlpha = 0.98f;
                enableFogPulse = true;
                break;
            case "eldritch":
                fogNearPlane = 0.2F;
                fogFarPlane = 10.0F;
                fogColorRed = 0.2f;
                fogColorGreen = 0.0f;
                fogColorBlue = 0.4f;
                fogColorAlpha = 0.97f;
                enableFogPulse = true;
                break;
            case "space":
                fogNearPlane = 0.6F;
                fogFarPlane = 100.0F;
                fogColorRed = 0.05f;
                fogColorGreen = 0.05f;
                fogColorBlue = 0.15f;
                fogColorAlpha = 0.85f;
                enableFogPulse = true;
                break;
            case "corrupted":
                fogNearPlane = 0.2F;
                fogFarPlane = 12.0F;
                fogColorRed = 0.4f;
                fogColorGreen = 0.0f;
                fogColorBlue = 0.2f;
                fogColorAlpha = 0.95f;
                enableFogPulse = true;
                break;
            case "celestial":
                fogNearPlane = 0.7F;
                fogFarPlane = 70.0F;
                fogColorRed = 0.9f;
                fogColorGreen = 0.9f;
                fogColorBlue = 1.0f;
                fogColorAlpha = 0.6f;
                enableFogPulse = true;
                break;
            case "storm":
                fogNearPlane = 0.3F;
                fogFarPlane = 15.0F;
                fogColorRed = 0.3f;
                fogColorGreen = 0.3f;
                fogColorBlue = 0.4f;
                fogColorAlpha = 0.92f;
                enableFogPulse = true;
                break;
            case "abyssal":
                fogNearPlane = 0.05F;
                fogFarPlane = 4.0F;
                fogColorRed = 0.0f;
                fogColorGreen = 0.0f;
                fogColorBlue = 0.1f;
                fogColorAlpha = 1.0f;
                enableFogPulse = false;
                break;
            case "netherburn":
                fogNearPlane = 0.15F;
                fogFarPlane = 10.0F;
                fogColorRed = 0.7f;
                fogColorGreen = 0.2f;
                fogColorBlue = 0.05f;
                fogColorAlpha = 0.95f;
                enableFogPulse = true;
                break;
            case "elderswamp":
                fogNearPlane = 0.3F;
                fogFarPlane = 15.0F;
                fogColorRed = 0.15f;
                fogColorGreen = 0.2f;
                fogColorBlue = 0.1f;
                fogColorAlpha = 0.93f;
                enableFogPulse = true;
                break;
            case "nebula":
                fogNearPlane = 0.5F;
                fogFarPlane = 60.0F;
                fogColorRed = 0.6f;
                fogColorGreen = 0.3f;
                fogColorBlue = 0.8f;
                fogColorAlpha = 0.7f;
                enableFogPulse = true;
                break;
            case "wasteland":
                fogNearPlane = 0.4F;
                fogFarPlane = 25.0F;
                fogColorRed = 0.5f;
                fogColorGreen = 0.5f;
                fogColorBlue = 0.3f;
                fogColorAlpha = 0.88f;
                enableFogPulse = false;
                break;
            case "void":
                fogNearPlane = 0.01F;
                fogFarPlane = 2.0F;
                fogColorRed = 0.0f;
                fogColorGreen = 0.0f;
                fogColorBlue = 0.0f;
                fogColorAlpha = 1.0f;
                enableFogPulse = false;
                break;
            case "festival":
                fogNearPlane = 0.6F;
                fogFarPlane = 40.0F;
                fogColorRed = 1.0f;
                fogColorGreen = 0.5f;
                fogColorBlue = 0.8f;
                fogColorAlpha = 0.75f;
                enableFogPulse = true;
                break;
            case "temple":
                fogNearPlane = 0.5F;
                fogFarPlane = 35.0F;
                fogColorRed = 0.9f;
                fogColorGreen = 0.85f;
                fogColorBlue = 0.6f;
                fogColorAlpha = 0.8f;
                enableFogPulse = false;
                break;
            case "stormy_night":
                fogNearPlane = 0.2F;
                fogFarPlane = 12.0F;
                fogColorRed = 0.1f;
                fogColorGreen = 0.1f;
                fogColorBlue = 0.2f;
                fogColorAlpha = 0.97f;
                enableFogPulse = true;
                break;
            case "obsidian":
                fogNearPlane = 0.1F;
                fogFarPlane = 7.0F;
                fogColorRed = 0.05f;
                fogColorGreen = 0.0f;
                fogColorBlue = 0.1f;
                fogColorAlpha = 1.0f;
                enableFogPulse = false;
                break;
            case "none":
                enableFog = false;
                enableMoonRenderer = false;
                fogNearPlane = 1.0F;
                fogFarPlane = 512.0F;
                fogColorRed = 0.5f;
                fogColorGreen = 0.7f;
                fogColorBlue = 1.0f;
                fogColorAlpha = 1.0f;
                enableFogPulse = false;
                break;
            default:
                setFogPreset("normal");
                break;
        }
        ZombieMoonRenderer.setEnabled(enableMoonRenderer);
    }
}
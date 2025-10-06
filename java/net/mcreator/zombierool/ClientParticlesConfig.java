package net.mcreator.zombierool.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Cette classe gère les paramètres des particules côté client.
 * Elle est mise à jour par les messages envoyés du serveur.
 */
@OnlyIn(Dist.CLIENT)
public class ClientParticlesConfig {

    public static boolean enableParticles = false;
    public static ResourceLocation particleType = null; // Ex: new ResourceLocation("minecraft", "basalt_drip_fall")

    public static void setParticles(boolean enabled, ResourceLocation type) {
        enableParticles = enabled;
        particleType = type;
        // You might want to log this for debugging
        if (enabled && type != null) {
            //System.out.println("[ClientParticlesConfig] Particules activées: " + type.toString());
        } else {
            //System.out.println("[ClientParticlesConfig] Particules désactivées.");
        }
    }
}

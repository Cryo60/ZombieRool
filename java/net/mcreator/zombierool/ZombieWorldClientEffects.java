package net.mcreator.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.core.BlockPos; // Import pour BlockPos
import net.minecraft.core.particles.SimpleParticleType; // Import pour SimpleParticleType

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ZombieWorldClientEffects {

    private static float fogPulseValue = 0;
    private static boolean fogPulseDirection = true;
    private static boolean clientParticlesEnabled = false;
    private static ResourceLocation clientParticleTypeId = null;
    private static ParticleOptions activeParticleType = null;
    private static String clientParticleDensity = "normal";
    private static String clientParticleMode = "global"; // Mode des particules côté client

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || player.isSpectator()
                || player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.CONDUIT_POWER)) {
            return;
        }

        if (!ClientFogConfig.enableFog) {
            event.setCanceled(false);
            return;
        }

        if (ClientWaveState.isSpecialWave()) {
            float near = 1.0F;
            float far = 12.0F;
            RenderSystem.setShaderFogColor(0.6f, 0.6f, 0.6f, 1f);
            event.setNearPlaneDistance(near);
            event.setFarPlaneDistance(far); // Corrected method name
            event.setCanceled(true);
        } else {
            float currentFarPlane = ClientFogConfig.fogFarPlane;
            if (ClientFogConfig.enableFogPulse) {
                currentFarPlane = ClientFogConfig.fogFarPlane + (fogPulseValue * (ClientFogConfig.fogFarPlane * 0.25F));
            }
            RenderSystem.setShaderFogColor(ClientFogConfig.fogColorRed, ClientFogConfig.fogColorGreen, ClientFogConfig.fogColorBlue,
                    ClientFogConfig.fogColorAlpha);
            event.setNearPlaneDistance(ClientFogConfig.fogNearPlane);
            event.setFarPlaneDistance(currentFarPlane); // Corrected method name
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (ClientFogConfig.enableFogPulse) {
                if (fogPulseDirection) {
                    fogPulseValue = Math.min(1f, fogPulseValue + 0.005F);
                    if (fogPulseValue >= 1.0F)
                        fogPulseDirection = false;
                } else {
                    fogPulseValue = Math.max(0f, fogPulseValue - 0.005F);
                    if (fogPulseValue <= 0.0F)
                        fogPulseDirection = true;
                }
            } else {
                fogPulseValue = 0;
            }

            if (clientParticlesEnabled && activeParticleType != null) {
                Minecraft mc = Minecraft.getInstance();
                ClientLevel level = mc.level;
                Player player = mc.player;

                if (level == null || player == null) {
                    return;
                }
                RandomSource random = level.random;

                // MODIFICATION ICI: La logique pour le mode atmosphérique est réactivée.
                // Les particules en mode "atmospheric" n'apparaîtront que si le joueur peut voir le ciel.
                if (clientParticleMode.equals("atmospheric")) {
                    if (!level.canSeeSky(player.blockPosition())) {
                        return; // Ne pas générer de particules si le joueur est à l'intérieur en mode atmosphérique
                    }
                }

                int particlesToSpawn = 0;
                switch (clientParticleDensity) {
                    case "sparse":
                        particlesToSpawn = 1;
                        break;
                    case "normal":
                        particlesToSpawn = 2;
                        break;
                    case "dense":
                        particlesToSpawn = 4;
                        break;
                    case "very_dense":
                        particlesToSpawn = 8;
                        break;
                    default:
                        particlesToSpawn = 2;
                        break;
                }

                int spawnedParticlesThisTick = 0; // Compteur pour le débogage
                for (int i = 0; i < particlesToSpawn; ++i) {
                    double x, y, z;
                    double xSpeed, ySpeed, zSpeed;

                    // Génération des positions de particules autour du joueur.
                    // Rayon légèrement réduit pour concentrer les particules près du joueur,
                    // ce qui peut aider à leur visibilité malgré le culling de Minecraft.
                    x = player.getX() + (random.nextDouble() - 0.5) * 12.0; // Réduit de 20.0 à 12.0
                    y = player.getY() + (random.nextDouble() - 0.5) * 8.0;  // Réduit de 15.0 à 8.0
                    z = player.getZ() + (random.nextDouble() - 0.5) * 12.0; // Réduit de 20.0 à 12.0
                    
                    // La vérification de la position par rapport aux blocs est délibérément ignorée ici.
                    // Les particules seront générées même si elles se trouvent dans des blocs solides.

                    // Vitesses des particules
                    xSpeed = (random.nextDouble() * 2.0 - 1.0) * 0.15;
                    ySpeed = (random.nextDouble() * 2.0 - 1.0) * 0.15;
                    zSpeed = (random.nextDouble() * 2.0 - 1.0) * 0.15;
                    
                    level.addParticle(activeParticleType, x, y, z, xSpeed, ySpeed, zSpeed);
                    spawnedParticlesThisTick++;
                }

                // Pour le débogage: décommenter la ligne ci-dessous pour voir le nombre de particules spawnées
                // if (spawnedParticlesThisTick == 0 && particlesToSpawn > 0) {
                //     Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§cAucune particule spawnée ce tick ! (Demande: " + particlesToSpawn + ")", "§cNo particles spawned this tick! (Requested: " + particlesToSpawn + ")"));
                // }
            }
        }
    }

    @SubscribeEvent
    public static void onClientChatReceived(net.minecraftforge.client.event.ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (message.startsWith("ZOMBIEROOL_PARTICLES_ENABLE:")) {
            event.setCanceled(true);
            String data = message.substring("ZOMBIEROOL_PARTICLES_ENABLE:".length());
            // Format attendu: particleId:density:mode
            // Exemple: minecraft:snowflake:very_dense:atmospheric

            // Trouver le dernier ":" pour séparer le mode
            int lastColonIndex = data.lastIndexOf(":");
            if (lastColonIndex == -1) {
                Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§cErreur de format de particule: mode manquant. Message reçu: " + message, "§cParticle format error: mode missing. Message received: " + message));
                return;
            }
            String modeString = data.substring(lastColonIndex + 1); // Exemple: "atmospheric"

            // Trouver le ":" avant le dernier pour séparer la densité
            String dataBeforeMode = data.substring(0, lastColonIndex); // Exemple: "minecraft:snowflake:very_dense"
            int secondLastColonIndex = dataBeforeMode.lastIndexOf(":");
            if (secondLastColonIndex == -1) {
                Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§cErreur de format de particule: densité ou ID de particule manquant. Message reçu: " + message, "§cParticle format error: density or particle ID missing. Message received: " + message));
                return;
            }
            String densityString = dataBeforeMode.substring(secondLastColonIndex + 1); // Exemple: "very_dense"

            // Le reste est l'ID complet de la particule
            String particleIdString = dataBeforeMode.substring(0, secondLastColonIndex); // Exemple: "minecraft:snowflake"
            
            // --- DÉBOGAGE AJOUTÉ ---
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§dDEBUG: particleIdString reçu = '" + particleIdString + "' / data complète = '" + data + "'", "§dDEBUG: particleIdString received = '" + particleIdString + "' / full data = '" + data + "'"));
            }
            // --- FIN DÉBOGAGE AJOUTÉ ---

            ResourceLocation particleResLoc = ResourceLocation.tryParse(particleIdString);
            if (particleResLoc != null && BuiltInRegistries.PARTICLE_TYPE.containsKey(particleResLoc)) {
                // MODIFICATION ICI: Vérifie si c'est un SimpleParticleType pour un cast correct
                net.minecraft.core.particles.ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.get(particleResLoc);
                if (particleType instanceof SimpleParticleType simpleType) {
                    if (!clientParticlesEnabled || !particleResLoc.equals(clientParticleTypeId) || !densityString.equals(clientParticleDensity) || !modeString.equals(clientParticleMode)) {
                        clientParticlesEnabled = true;
                        clientParticleTypeId = particleResLoc;
                        activeParticleType = simpleType; // Assignation directe du SimpleParticleType
                        clientParticleDensity = densityString;
                        clientParticleMode = modeString; // Mise à jour du mode
                        Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§aParticules flottantes activées: §e" + particleIdString + " (Densité: " + densityString + ", Mode: " + modeString + ")", "§aFloating particles enabled: §e" + particleIdString + " (Density: " + densityString + ", Mode: " + modeString + ")"));
                    }
                } else {
                    Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§cErreur: Type de particule non simple ou incompatible pour l'activation: " + particleIdString, "§cError: Non-simple or incompatible particle type for activation: " + particleIdString));
                    // Optionnel: désactiver les particules si le type est incompatible
                    clientParticlesEnabled = false;
                }
            } else {
                Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§cErreur: Type de particule inconnu: " + particleIdString, "§cError: Unknown particle type: " + particleIdString));
            }
        } else if (message.equals("ZOMBIEROOL_PARTICLES_DISABLE")) {
            event.setCanceled(true);
            if (clientParticlesEnabled) {
                clientParticlesEnabled = false;
                clientParticleTypeId = null;
                activeParticleType = null;
                clientParticleDensity = "normal";
                clientParticleMode = "global"; // Réinitialisation du mode
                Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§aParticules flottantes désactivées.", "§aFloating particles disabled."));
            }
        }

        if (message.startsWith("ZOMBIEROOL_FOG_PRESET:")) {
            event.setCanceled(true);
            String preset = message.substring("ZOMBIEROOL_FOG_PRESET:".length());
            ClientFogConfig.setFogPreset(preset);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(getTranslatedComponent("§9Brouillard mis à jour: §b" + preset, "§9Fog updated: §b" + preset));
            }
        }
    }
}

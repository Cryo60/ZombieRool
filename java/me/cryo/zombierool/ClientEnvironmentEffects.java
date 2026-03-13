package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.client.render.ZombieMoonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEnvironmentEffects {

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

	public static float customFogR = 0f, customFogG = 0f, customFogB = 0f;
	public static float customFogNear = 0.5f, customFogFar = 18.0f;

	public static boolean clientParticlesEnabled = false;
	public static ResourceLocation clientParticleTypeId = null;
	public static ParticleOptions activeParticleType = null;
	public static String clientParticleDensity = "normal";
	public static String clientParticleMode = "global";

	private static float fogPulseValue = 0;
	private static boolean fogPulseDirection = true;

	public static void setFogPreset(String preset, float r, float g, float b, float near, float far) {
	    currentFogPreset = preset;
	    enableFog = true;
	    enableMoonRenderer = true;
	    enableFogPulse = false;
		customFogR = r; customFogG = g; customFogB = b;
		customFogNear = near; customFogFar = far;

	    switch (preset.toLowerCase()) {
	        case "normal" -> setupFog(0.5F, 18.0F, 0F, 0F, 0F, 0.9F, true);
	        case "dense" -> setupFog(0.1F, 8.0F, 0.1F, 0.1F, 0.1F, 0.95F, false);
	        case "clear" -> setupFog(0.1F, 60.0F, 0.7F, 0.8F, 1.0F, 0.5F, false);
	        case "dark" -> setupFog(0.1F, 12.0F, 0.0F, 0.0F, 0.0F, 1.0F, false);
	        case "blood" -> setupFog(0.1F, 16.0F, 0.6F, 0.0F, 0.0F, 0.8F, true);
	        case "nightmare" -> setupFog(0.1F, 10.0F, 0.1F, 0.0F, 0.0F, 0.95F, true);
	        case "green_acid" -> setupFog(0.1F, 15.0F, 0.1F, 0.4F, 0.1F, 0.8F, true);
	        case "sunrise" -> setupFog(0.1F, 30.0F, 0.8F, 0.4F, 0.2F, 0.6F, false);
	        case "sunset" -> setupFog(0.1F, 30.0F, 0.7F, 0.2F, 0.4F, 0.6F, false);
	        case "underwater" -> setupFog(0.0F, 20.0F, 0.0F, 0.2F, 0.6F, 0.9F, true);
	        case "swamp" -> setupFog(0.1F, 12.0F, 0.1F, 0.2F, 0.1F, 0.9F, true);
	        case "volcanic" -> setupFog(0.1F, 15.0F, 0.3F, 0.1F, 0.05F, 0.9F, true);
	        case "mystic" -> setupFog(0.1F, 25.0F, 0.4F, 0.0F, 0.6F, 0.7F, true);
	        case "toxic" -> setupFog(0.1F, 10.0F, 0.4F, 0.6F, 0.0F, 0.9F, true);
	        case "dreamy" -> setupFog(0.1F, 40.0F, 0.9F, 0.8F, 0.9F, 0.5F, false);
	        case "winter" -> setupFog(0.1F, 25.0F, 0.9F, 0.9F, 1.0F, 0.8F, false);
	        case "haunted" -> setupFog(0.1F, 14.0F, 0.2F, 0.0F, 0.25F, 0.9F, true);
	        case "arctic" -> setupFog(0.5F, 30.0F, 0.8F, 0.9F, 1.0F, 0.7F, false);
	        case "prehistoric" -> setupFog(0.1F, 20.0F, 0.3F, 0.4F, 0.2F, 0.8F, false);
	        case "radioactive" -> setupFog(0.1F, 15.0F, 0.2F, 0.8F, 0.0F, 0.9F, true);
	        case "desert" -> setupFog(0.1F, 20.0F, 0.8F, 0.7F, 0.4F, 0.8F, true);
	        case "ashstorm" -> setupFog(0.1F, 8.0F, 0.3F, 0.3F, 0.3F, 1.0F, false);
	        case "eldritch" -> setupFog(0.1F, 18.0F, 0.1F, 0.0F, 0.2F, 0.95F, true);
	        case "space" -> setupFog(0.1F, 128.0F, 0.0F, 0.0F, 0.05F, 0.3F, false);
	        case "corrupted" -> setupFog(0.1F, 14.0F, 0.3F, 0.0F, 0.3F, 0.9F, true);
	        case "celestial" -> setupFog(0.1F, 50.0F, 0.6F, 0.8F, 1.0F, 0.5F, false);
	        case "storm" -> setupFog(0.1F, 20.0F, 0.2F, 0.25F, 0.35F, 0.8F, true);
	        case "abyssal" -> setupFog(0.0F, 10.0F, 0.0F, 0.0F, 0.1F, 1.0F, false);
	        case "netherburn" -> setupFog(0.1F, 25.0F, 0.5F, 0.1F, 0.0F, 0.8F, true);
	        case "elderswamp" -> setupFog(0.1F, 10.0F, 0.05F, 0.15F, 0.05F, 0.95F, true);
	        case "nebula" -> setupFog(0.1F, 40.0F, 0.3F, 0.1F, 0.5F, 0.6F, true);
	        case "wasteland" -> setupFog(0.1F, 25.0F, 0.4F, 0.35F, 0.3F, 0.8F, false);
	        case "void" -> setupFog(0.1F, 10.0F, 0.0F, 0.0F, 0.0F, 1.0F, false);
	        case "festival" -> setupFog(0.1F, 30.0F, 1.0F, 0.5F, 0.8F, 0.6F, true);
	        case "temple" -> setupFog(0.1F, 35.0F, 0.9F, 0.8F, 0.4F, 0.6F, false);
	        case "stormy_night" -> setupFog(0.1F, 15.0F, 0.05F, 0.05F, 0.1F, 0.9F, true);
	        case "obsidian" -> setupFog(0.1F, 12.0F, 0.1F, 0.0F, 0.15F, 0.95F, false);
	        case "none" -> {
	            enableFog = false;
	            enableMoonRenderer = false;
	            setupFog(1.0F, 512.0F, 0.5F, 0.5F, 0.5F, 1.0F, false);
	        }
	        default -> {
				setupFog(near, far, r, g, b, 1.0f, false);
			}
	    }

	    ZombieMoonRenderer.setEnabled(enableMoonRenderer);
	}

	private static void setupFog(float near, float far, float r, float g, float b, float a, boolean pulse) {
	    fogNearPlane = near;
	    fogFarPlane = far;
	    fogColorRed = r;
	    fogColorGreen = g;
	    fogColorBlue = b;
	    fogColorAlpha = a;
	    enableFogPulse = pulse;
	}

	@SubscribeEvent
	public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
	    if (enableFog && !WaveManager.isClientSpecialWave()) {
	        event.setRed(fogColorRed);
	        event.setGreen(fogColorGreen);
	        event.setBlue(fogColorBlue);
	    } else if (WaveManager.isClientSpecialWave()) {
	         event.setRed(0.6f);
	         event.setGreen(0.6f);
	         event.setBlue(0.6f);
	    }
	}

	@SubscribeEvent
	public static void onRenderFog(ViewportEvent.RenderFog event) {
	    Player player = Minecraft.getInstance().player;

	    if (player == null || player.isCreative() || player.isSpectator()
	            || player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
	            || player.hasEffect(net.minecraft.world.effect.MobEffects.CONDUIT_POWER)) {
	        return;
	    }

	    if (!enableFog) {
	        event.setCanceled(false);
	        return;
	    }

	    if (WaveManager.isClientSpecialWave()) {
	        RenderSystem.setShaderFogColor(0.6f, 0.6f, 0.6f, 1f);
	        event.setNearPlaneDistance(1.0F);
	        event.setFarPlaneDistance(12.0F);
	        event.setCanceled(true);
	    } else {
	        float currentFarPlane = fogFarPlane;
	        if (enableFogPulse) {
	            currentFarPlane = fogFarPlane + (fogPulseValue * (fogFarPlane * 0.25F));
	        }
	        event.setNearPlaneDistance(fogNearPlane);
	        event.setFarPlaneDistance(currentFarPlane);
	        event.setCanceled(true);
	    }
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
	    if (event.phase != TickEvent.Phase.END) return;

	    if (enableFogPulse) {
	        if (fogPulseDirection) {
	            fogPulseValue = Math.min(1f, fogPulseValue + 0.005F);
	            if (fogPulseValue >= 1.0F) fogPulseDirection = false;
	        } else {
	            fogPulseValue = Math.max(0f, fogPulseValue - 0.005F);
	            if (fogPulseValue <= 0.0F) fogPulseDirection = true;
	        }
	    } else {
	        fogPulseValue = 0;
	    }

	    if (clientParticlesEnabled && activeParticleType != null) {
	        Minecraft mc = Minecraft.getInstance();
	        ClientLevel level = mc.level;
	        Player player = mc.player;
	        
	        if (level == null || player == null || mc.isPaused()) return;

	        int particlesToSpawn = switch (clientParticleDensity) {
	            case "sparse" -> 2;
	            case "normal" -> 6;
	            case "dense" -> 15;
	            case "very_dense" -> 35;
	            default -> 6;
	        };

	        RandomSource random = level.random;

	        for (int i = 0; i < particlesToSpawn; ++i) {
	            double angle = random.nextDouble() * Math.PI * 2.0;
	            double radius = Math.sqrt(random.nextDouble()) * 32.0;
	            double px = player.getX() + Math.cos(angle) * radius;
	            double pz = player.getZ() + Math.sin(angle) * radius;
	            double py;
	            
	            BlockPos pos = BlockPos.containing(px, player.getY(), pz);
	            if (clientParticleMode.equals("atmospheric")) {
	                int topY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY();
	                if (player.getY() < topY - 15) {
	                    continue; 
	                }
	                double baseSpawnY = Math.max(player.getY() + 8.0, topY);
	                py = baseSpawnY + random.nextDouble() * 20.0;
	            } else {
	                py = player.getY() + (random.nextDouble() - 0.5) * 32.0;
	                BlockPos checkPos = BlockPos.containing(px, py, pz);
	                if (level.getBlockState(checkPos).isSolidRender(level, checkPos)) {
	                    continue;
	                }
	            }

	            double vx = (random.nextDouble() - 0.5) * 0.1;
	            double vy = -0.05 - random.nextDouble() * 0.1; 
	            double vz = (random.nextDouble() - 0.5) * 0.1;

	            level.addParticle(activeParticleType, px, py, pz, vx, vy, vz);
	        }
	    }
	}

    public static void handleWeatherSync(boolean enabled, String particleId, String density, String mode) {
        clientParticlesEnabled = enabled;
        if (enabled && particleId != null && !particleId.isEmpty() && !particleId.equals("none")) {
            ResourceLocation loc = new ResourceLocation(particleId);
            clientParticleTypeId = loc;
            
            net.minecraft.core.particles.ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(loc);
            if (type instanceof SimpleParticleType simple) {
                activeParticleType = simple;
            } else if (type instanceof ParticleOptions opts) {
                activeParticleType = opts;
            } else {
                activeParticleType = null;
            }
            
            clientParticleDensity = density;
            clientParticleMode = mode;
        } else {
            activeParticleType = null;
        }
    }
}
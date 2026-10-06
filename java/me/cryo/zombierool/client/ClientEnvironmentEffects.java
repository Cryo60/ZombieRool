package me.cryo.zombierool.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.client.render.ZombieMoonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.io.IOException;

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
    public static boolean fogOutdoorsOnly = false;
    public static boolean clientParticlesEnabled = false;
    public static ResourceLocation clientParticleTypeId = null;
    public static ParticleOptions activeParticleType = null;
    public static String clientParticleDensity = "normal";
    public static String clientParticleMode = "global";
    private static float fogPulseValue = 0;
    private static boolean fogPulseDirection = true;
    private static long shaderCheckedAt = 0;
    private static boolean shaderPackInUse = false;
    private static final int FOG_HEIGHT_MAP = 128;
    private static DynamicTexture fogHeightTexture;
    private static int fogHeightOriginX;
    private static int fogHeightOriginZ;
    private static int fogHeightRefresh;

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        fogOutdoorsOnly = false;
        setFogPreset("none", 0,0,0,0.5f,512.0f);
        handleWeatherSync(false, "", "", "");
    }

    public static void setFogPreset(String preset, float r, float g, float b, float near, float far, boolean outdoorsOnly) {
        fogOutdoorsOnly = outdoorsOnly;
        setFogPreset(preset, r, g, b, near, far);
    }

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
        if (player == null || player.isSpectator()
                || player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.CONDUIT_POWER)) {
            return;
        }

        if (!enableFog) {
            return;
        }

        if (fogOutdoorsOnly) {
            event.setFogShape(FogShape.CYLINDER);
            event.setNearPlaneDistance(512.0F);
            event.setFarPlaneDistance(1024.0F);
            event.setCanceled(true);
            return;
        }

        event.setFogShape(shaderPackActive() ? FogShape.SPHERE : FogShape.CYLINDER);

        if (WaveManager.isClientSpecialWave()) {
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

    private static ShaderInstance fogShader;

    /**
     * Iris and Oculus replace Minecraft fog, and a world-space cylinder becomes the triangles in the sky.
     * The same distances are applied as a fullscreen pass that reads the depth buffer.
     */
    @SubscribeEvent
    public static void onRenderShaderFog(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (fogShader == null || !enableFog || (!shaderPackActive() && !fogOutdoorsOnly)) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || player.isSpectator()
                || player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.CONDUIT_POWER)) {
            return;
        }

        float far = fogFarPlane;
        float near = fogNearPlane;
        if (WaveManager.isClientSpecialWave()) {
            near = 1.0F;
            far = 12.0F;
        } else if (enableFogPulse) {
            far = fogFarPlane + (fogPulseValue * (fogFarPlane * 0.25F));
        }
        if (far <= near + 0.5F) far = near + 0.5F;

        ensureFogHeightTexture();
        var cam = minecraft.gameRenderer.getMainCamera();
        Matrix4f invViewRot = new Matrix4f(RenderSystem.getInverseViewRotationMatrix());
        var camera = cam.getPosition();

        float red = WaveManager.isClientSpecialWave() ? 0.6F : fogColorRed;
        float green = WaveManager.isClientSpecialWave() ? 0.6F : fogColorGreen;
        float blue = WaveManager.isClientSpecialWave() ? 0.6F : fogColorBlue;
        var target = minecraft.getMainRenderTarget();
        Matrix4f inverseProjection = new Matrix4f(event.getProjectionMatrix()).invert();

        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> fogShader);
        RenderSystem.setShaderTexture(0, target.getDepthTextureId());
        RenderSystem.setShaderTexture(1, fogHeightTexture.getId());
        fogShader.safeGetUniform("InvProjMat").set(inverseProjection);
        fogShader.safeGetUniform("InvViewRot").set(invViewRot);
        fogShader.safeGetUniform("FogColor").set(red, green, blue, fogColorAlpha);
        fogShader.safeGetUniform("FogNear").set(near);
        fogShader.safeGetUniform("FogFar").set(far);
        fogShader.safeGetUniform("ScreenSize").set((float) target.width, (float) target.height);
        fogShader.safeGetUniform("CameraPos").set((float) camera.x, (float) camera.y, (float) camera.z);
        fogShader.safeGetUniform("HeightOrigin").set((float) fogHeightOriginX, (float) fogHeightOriginZ);
        fogShader.safeGetUniform("MapSize").set((float) FOG_HEIGHT_MAP);
        fogShader.safeGetUniform("OutdoorsOnly").set(fogOutdoorsOnly ? 1.0F : 0.0F);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.vertex(-1.0, -1.0, 0.0).endVertex();
        buffer.vertex(1.0, -1.0, 0.0).endVertex();
        buffer.vertex(1.0, 1.0, 0.0).endVertex();
        buffer.vertex(-1.0, 1.0, 0.0).endVertex();
        tesselator.end();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class FogShaderInit {
        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            try {
                event.registerShader(
                        new ShaderInstance(event.getResourceProvider(), new ResourceLocation("zombierool", "zr_fog"), DefaultVertexFormat.POSITION),
                        shader -> fogShader = shader
                );
            } catch (IOException e) {
                fogShader = null;
                me.cryo.zombierool.ZombieroolMod.LOGGER.error("ZombieRool fog shader failed to load", e);
            }
        }
    }

    private static boolean shaderPackActive() {
        long now = System.currentTimeMillis();
        if (now - shaderCheckedAt < 1000L) return shaderPackInUse;
        shaderCheckedAt = now;
        shaderPackInUse = false;
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) return false;
        for (String className : new String[]{"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
            try {
                Class<?> api = Class.forName(className);
                Object instance = api.getMethod("getInstance").invoke(null);
                shaderPackInUse = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
                return shaderPackInUse;
            } catch (Throwable ignored) {}
        }
        shaderPackInUse = true;
        return true;
    }

    private static void ensureFogHeightTexture() {
        if (fogHeightTexture == null) {
            fogHeightTexture = new DynamicTexture(FOG_HEIGHT_MAP, FOG_HEIGHT_MAP, false);
            refreshFogHeightTexture();
        }
    }

    private static void refreshFogHeightTexture() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (fogHeightTexture == null || level == null || minecraft.player == null) return;
        NativeImage image = fogHeightTexture.getPixels();
        if (image == null) return;
        var origin = minecraft.player.blockPosition();
        fogHeightOriginX = origin.getX() - FOG_HEIGHT_MAP / 2;
        fogHeightOriginZ = origin.getZ() - FOG_HEIGHT_MAP / 2;
        for (int x = 0; x < FOG_HEIGHT_MAP; x++) {
            for (int z = 0; z < FOG_HEIGHT_MAP; z++) {
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fogHeightOriginX + x, fogHeightOriginZ + z);
                int stored = Mth.clamp(height + 64, 0, 65535);
                image.setPixelRGBA(x, z, 0xFF000000 | (((stored >> 8) & 0xFF) << 8) | (stored & 0xFF));
            }
        }
        fogHeightTexture.upload();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (fogOutdoorsOnly && enableFog && fogHeightRefresh-- <= 0) {
            fogHeightRefresh = 10;
            refreshFogHeightTexture();
        }

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
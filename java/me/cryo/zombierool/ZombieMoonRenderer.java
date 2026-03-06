package me.cryo.zombierool.client.render;

import me.cryo.zombierool.client.ClientEnvironmentEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ZombieMoonRenderer extends DimensionSpecialEffects {
    private static boolean enabled = true;

    public ZombieMoonRenderer() {
        super(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, false);
    }

    public static void setEnabled(boolean state) {
        enabled = state;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        if (!enabled) return color;
        return new Vec3(ClientEnvironmentEffects.fogColorRed, ClientEnvironmentEffects.fogColorGreen, ClientEnvironmentEffects.fogColorBlue);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return enabled;
    }

    // Pas d'Override ici car ces méthodes n'existent pas dans DimensionSpecialEffects (elles sont gérées différemment en 1.20.1)
    // Nous retournons juste des valeurs par défaut ou nous utilisons RenderSystem si nécessaire dans des events.
    // Cependant, DimensionSpecialEffects a getSkyColor et getSunriseColor.
    
    @Override
    @Nullable
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null; // Désactive le lever/coucher du soleil vanilla si on veut
    }
    
    // Cette méthode n'est pas une surcharge officielle de la classe, je retire @Override
    public boolean shouldRenderClouds(ClientLevel level, int ticks, float partialTick, double camX, double camY, double camZ) {
        return enabled;
    }

    // Cette méthode n'est pas une surcharge officielle de la classe, je retire @Override
    public boolean shouldRenderSky(ClientLevel level, int ticks, float partialTick, double camX, double camY, double camZ) {
        return enabled;
    }

    // Cette méthode n'est pas une surcharge officielle de la classe, je retire @Override (C'est géré par IClientFluidTypeExtensions ou events normalement)
    public float[] getSkyColor(float celestialAngle, float partialTicks) {
        if (!enabled) {
            float f = celestialAngle * 2.0F - 1.0F;
            f = net.minecraft.util.Mth.clamp(f, -1.0F, 1.0F);
            f = f * 0.85F + 0.15F;
            float f1 = 0.72F - f * 0.1F;
            float f2 = 0.8F - f * 0.1F;
            float f3 = 1.0F;
            return new float[]{f1, f2, f3, 1.0F};
        }
        return new float[]{0.0F, 0.0F, 0.0F, 1.0F};
    }

    // Cette méthode n'est pas une surcharge officielle de la classe, je retire @Override
    public Vec3 getCloudColor(float partialTicks) {
        if (!enabled) return new Vec3(1.0D, 1.0D, 1.0D);
        return new Vec3(0.1D, 0.1D, 0.1D);
    }
}
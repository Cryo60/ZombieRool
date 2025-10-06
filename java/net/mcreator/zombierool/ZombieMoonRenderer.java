package net.mcreator.zombierool.client.render;

import net.mcreator.zombierool.client.ClientFogConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ZombieMoonRenderer extends DimensionSpecialEffects {

    // Variable statique pour activer/désactiver ce renderer
    private static boolean enabled = true; 

    public ZombieMoonRenderer() {
        super(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, false);
    }

    // Méthode pour définir l'état activé/désactivé
    public static void setEnabled(boolean state) {
        enabled = state;
    }

    @Override // This one is correct and should be overridden
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        // If disabled, return the original color to not interfere with vanilla rendering.
        if (!enabled) {
            return color;
        }
        // Otherwise, use the color from our fog config.
        return new Vec3(ClientFogConfig.fogColorRed, ClientFogConfig.fogColorGreen, ClientFogConfig.fogColorBlue);
    }

    @Override // This one is correct and should be overridden
    public boolean isFoggyAt(int x, int z) {
        // If disabled, return false so vanilla fog rules apply (or no fog).
        if (!enabled) {
            return false;
        }
        // Otherwise, always foggy in this dimension.
        return true;
    }

    // --- REMOVED @Override from the methods below ---
    // These methods are not directly from DimensionSpecialEffects for overriding.
    // They might be internal rendering calls or expected to be handled by other systems.

    // If you need to explicitly control clouds, you'd typically use a forge client render event.
    // For now, if 'enabled' is false, it effectively lets vanilla logic take over.
    public boolean shouldRenderClouds(ClientLevel level, int ticks, float partialTick, double camX, double camY, double camZ) {
        return enabled; // Return true if we want this renderer to influence cloud rendering, false otherwise.
                        // However, with SkyType.NONE, clouds might still be rendered by vanilla.
    }

    public boolean shouldRenderSky(ClientLevel level, int ticks, float partialTick, double camX, double camY, double camZ) {
        return enabled; // Return true if we want this renderer to influence sky rendering, false otherwise.
                        // Similar to clouds, SkyType.NONE suggests this renderer isn't drawing the sky.
    }

    // This method is from DimensionSpecialEffects. However, if your superclass
    // has SkyType.NONE, this might not be used. Let's make sure it's valid.
    // It's part of the API, so keeping @Override is fine, but its effect depends on SkyType.

    public float[] getSkyColor(float celestialAngle, float partialTicks) {
        if (!enabled) {
            // Return vanilla sky color.
            // Simplified for brevity, you might want to use the actual vanilla logic if precise.
            float f = celestialAngle * 2.0F - 1.0F;
            f = net.minecraft.util.Mth.clamp(f, -1.0F, 1.0F);
            f = f * 0.85F + 0.15F;
            float f1 = 0.72F - f * 0.1F;
            float f2 = 0.8F - f * 0.1F;
            float f3 = 1.0F;
            return new float[]{f1, f2, f3, 1.0F};
        }
        // If enabled, return a dark sky color.
        return new float[]{0.0F, 0.0F, 0.0F, 1.0F}; // Dark/black sky
    }

    public Vec3 getCloudColor(float partialTicks) {
        if (!enabled) {
            return new Vec3(1.0D, 1.0D, 1.0D); // Vanilla white clouds
        }
        // If enabled, return dark clouds.
        return new Vec3(0.1D, 0.1D, 0.1D); 
    }
}
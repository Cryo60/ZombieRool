package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public class CutsweepAnimationHandler {

    private static final ResourceLocation KNIFE_SWING_SOUND = new ResourceLocation("zombierool", "knife_swing");

    public static void startCutsweepAnimation(Runnable whenDone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ThirdPersonAnimHandler.startAnim(mc.player.getUUID(), "melee", 0, whenDone);

        ResourceLocation soundToPlay = KNIFE_SWING_SOUND;
        if (mc.player.getPersistentData().getBoolean("zr_has_bowie_knife")) {
            soundToPlay = new ResourceLocation("zombierool", "bowie_swing");
        }
        if (mc.level != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(soundToPlay), SoundSource.PLAYERS, 1.0f, 1.0f, false);
        }
    }

    public static boolean isRunning() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return ThirdPersonAnimHandler.isAnimationPlaying(mc.player.getUUID());
    }
}

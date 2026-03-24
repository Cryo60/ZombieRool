package me.cryo.zombierool.client;

import me.cryo.zombierool.client.animation.ZRAnimationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public class CutsweepAnimationHandler {

    private static final ResourceLocation KNIFE_SWING_SOUND = new ResourceLocation("zombierool", "knife_swing");

    public static void startCutsweepAnimation(Runnable whenDone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ZRAnimationManager.ZRAnimation anim = ZRAnimationManager.getAnimation("knife_sweep");
            if (anim != null) {
                ZRAnimationManager.ZRAnimationState state = new ZRAnimationManager.ZRAnimationState(anim);
                state.start(whenDone);
                ThirdPersonAnimHandler.activeAnims.put(mc.player.getUUID(), state);
            } else if (whenDone != null) {
                whenDone.run();
            }

            ResourceLocation soundToPlay = KNIFE_SWING_SOUND;
            if (mc.player.getPersistentData().getBoolean("zr_has_bowie_knife")) {
                soundToPlay = new ResourceLocation("zombierool", "bowie_swing");
            }

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
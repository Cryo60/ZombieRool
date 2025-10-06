package net.mcreator.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ScreenShakeHandler {

    public enum ShakeType {
        MELEE, EXPLOSION, HIT
    }

    private static int shakeTimer = 0;
    private static float shakeIntensity = 0.0f;
    private static int shakeDuration = 0;
    private static ShakeType currentType = ShakeType.MELEE;
    private static final Random random = new Random();

    public static void startShake(int duration, float intensity, ShakeType type) {
        shakeDuration = duration;
        shakeIntensity = intensity;
        shakeTimer = duration;
        currentType = type;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.level() != null) {
            player.level().playLocalSound(
			        player.getX(), player.getY(), player.getZ(),
			        SoundEvents.PLAYER_ATTACK_CRIT,
			        SoundSource.PLAYERS,
			        0.7f, 1.2f,
			        false // Add this boolean argument for attenuation
			);
        }

        System.out.println("DEBUG: Screen shake started! Duration: " + duration + ", Intensity: " + intensity + ", Type: " + type);
    }

    public static boolean isShaking() {
        return shakeTimer > 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && shakeTimer > 0) {
            shakeTimer--;
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!isShaking()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float progress = 1.0f - ((float) shakeTimer / shakeDuration);
        float eased = (float)(1.0 - Math.pow(1.0 - progress, 3));
        float currentShakeAmount = shakeIntensity * (1.0f - eased);

        double yawOffset = (random.nextDouble() * 2 - 1) * currentShakeAmount * 0.75;
        double pitchOffset = (random.nextDouble() * 2 - 1) * currentShakeAmount * 0.5;
        double rollOffset = (random.nextDouble() * 2 - 1) * currentShakeAmount * 0.3;

        yawOffset += Math.sin(mc.player.tickCount * 0.5) * currentShakeAmount * 0.2;
        pitchOffset += Math.cos(mc.player.tickCount * 0.4) * currentShakeAmount * 0.1;

        switch (currentType) {
            case EXPLOSION -> {
                pitchOffset *= 1.5;
                rollOffset *= 2.0;
            }
            case HIT -> {
                yawOffset *= 0.5;
                pitchOffset *= 0.5;
            }
            case MELEE -> {
                yawOffset *= 1.2;
                rollOffset *= 1.3;
            }
        }

        event.setYaw(event.getYaw() + (float) yawOffset);
        event.setPitch(event.getPitch() + (float) pitchOffset);
        event.setRoll(event.getRoll() + (float) rollOffset);

        System.out.println("DEBUG: Screen shake active. Yaw: " + yawOffset + ", Pitch: " + pitchOffset + ", Roll: " + rollOffset);
    }

    @SubscribeEvent
    public static void onFovChange(ViewportEvent.ComputeFov event) {
        if (!isShaking()) return;

        float progress = 1.0f - ((float) shakeTimer / shakeDuration);
        float eased = (float)(1.0 - Math.pow(1.0 - progress, 3));
        float currentShakeAmount = shakeIntensity * (1.0f - eased);

        float fovShake = switch (currentType) {
            case MELEE -> currentShakeAmount * 5.0f;
            case EXPLOSION -> currentShakeAmount * 10.0f;
            case HIT -> currentShakeAmount * 2.5f;
        };

        event.setFOV(event.getFOV() + fovShake);
    }
}

package me.cryo.zombierool.client;

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
		MELEE, EXPLOSION, HIT, IMPACT
	}

	private static int shakeTimer = 0;
	private static float shakeIntensity = 0.0f;
	private static int shakeDuration = 0;
	private static ShakeType currentType = ShakeType.MELEE;
	private static final Random random = new Random();

	public static void startShake(int duration, float intensity, ShakeType type) {
	    if(shakeTimer>0&&intensity<shakeIntensity*(shakeTimer/(float)Math.max(1,shakeDuration)))return;
        shakeDuration = duration;
	    shakeIntensity = intensity;
	    shakeTimer = duration;
	    currentType = type;

	    LocalPlayer player = Minecraft.getInstance().player;
	    if (type != ShakeType.IMPACT && player != null && player.level() != null) {
	        player.level().playLocalSound(
	                player.getX(), player.getY(), player.getZ(),
	                SoundEvents.PLAYER_ATTACK_CRIT,
	                SoundSource.PLAYERS,
	                0.7f, 1.2f,
	                false
	        );
	    }
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

	    if(currentType==ShakeType.IMPACT){float age=shakeDuration-shakeTimer+Minecraft.getInstance().getFrameTime();float envelope=(float)Math.exp(-age/Math.max(1,shakeDuration*.42f));event.setPitch(event.getPitch()+(float)Math.cos(age*1.6)*shakeIntensity*envelope);event.setYaw(event.getYaw()+(float)Math.sin(age*1.25)*shakeIntensity*.35f*envelope);event.setRoll(event.getRoll()+(float)Math.sin(age*1.4)*shakeIntensity*.5f*envelope);return;}
	    double yawOffset = (random.nextDouble() * 2 - 1) * currentShakeAmount * 0.75;
	    double pitchOffset = (random.nextDouble() * 2 - 1) * currentShakeAmount * 0.5;
	    double rollOffset = (random.nextDouble() * 2 - 1) * currentShakeAmount * 0.3;

	    // Ajouter un mouvement sinusoïdal pour un effet plus fluide
	    yawOffset += Math.sin(mc.player.tickCount * 0.5) * currentShakeAmount * 0.2;
	    pitchOffset += Math.cos(mc.player.tickCount * 0.4) * currentShakeAmount * 0.1;

	    if (currentType == ShakeType.EXPLOSION) {
	        pitchOffset *= 1.5;
	        rollOffset *= 2.0;
	    } else if (currentType == ShakeType.HIT) {
	        yawOffset *= 0.5;
	        pitchOffset *= 0.5;
	    } else {
	        yawOffset *= 1.2;
	        rollOffset *= 1.3;
	    }

	    event.setYaw(event.getYaw() + (float) yawOffset);
	    event.setPitch(event.getPitch() + (float) pitchOffset);
	    event.setRoll(event.getRoll() + (float) rollOffset);

	    // SUPPRESSION DU SYSTEM.OUT.PRINTLN ICI QUI CAUSAIT LE FREEZE
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
            case IMPACT -> currentShakeAmount * .6f;
	    };

	    event.setFOV(event.getFOV() + fovShake);
	}
}

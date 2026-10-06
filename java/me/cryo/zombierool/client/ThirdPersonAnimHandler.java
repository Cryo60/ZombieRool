package me.cryo.zombierool.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ThirdPersonAnimHandler {
    public static final ResourceLocation LAYER_ID = new ResourceLocation("zombierool", "player_anim");
    private static final Map<UUID, Playing> ACTIVE = new ConcurrentHashMap<>();

    public static void registerFactory(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, player -> new ModifierLayer<>());
    }

    public static void startAnim(UUID uuid, String type, int ticks) {
        startAnim(uuid, type, ticks, null);
    }

    public static void startAnim(UUID uuid, String type, int ticks, Runnable whenDone) {
        String animName = "melee".equals(type) ? "knife_sweep" : type;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (whenDone != null) whenDone.run();
            return;
        }
        Player player = mc.level.getPlayerByUUID(uuid);
        if (!(player instanceof AbstractClientPlayer clientPlayer)) {
            if (whenDone != null) whenDone.run();
            return;
        }
        KeyframeAnimation data = findAnim(animName);
        if (data == null) {
            me.cryo.zombierool.ZombieroolMod.LOGGER.warn("[ZombieRool] PlayerAnimator has no animation '{}'", animName);
            if (whenDone != null) whenDone.run();
            return;
        }
        ModifierLayer<IAnimation> layer = layer(clientPlayer);
        if (layer == null) {
            if (whenDone != null) whenDone.run();
            return;
        }
        KeyframeAnimationPlayer playback = new KeyframeAnimationPlayer(data);
        playback.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
        playback.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        layer.setAnimation(playback);
        ACTIVE.put(uuid, new Playing(animName, playback, whenDone));
    }

    public static String currentAnim(UUID uuid) {
        Playing playing = ACTIVE.get(uuid);
        return playing == null ? null : playing.name;
    }

    public static boolean isAnimationPlaying(UUID uuid) {
        Playing playing = ACTIVE.get(uuid);
        return playing != null && playing.playback.isActive();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ACTIVE.entrySet().removeIf(entry -> {
            if (entry.getValue().playback.isActive()) return false;
            if (entry.getValue().whenDone != null) entry.getValue().whenDone.run();
            return true;
        });
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        Playing playing = ACTIVE.get(player.getUUID());
        if (playing == null || !playing.playback.isActive()) return;
        Vec3f rot = playing.playback.get3DTransform("head", TransformType.ROTATION, (float) event.getPartialTick(), new Vec3f(0f, 0f, 0f));
        event.setPitch(event.getPitch() + rot.getX());
        event.setYaw(event.getYaw() + rot.getY());
        event.setRoll(event.getRoll() - rot.getZ());
    }

    @SubscribeEvent
    public static void onKeyInput(net.minecraftforge.client.event.InputEvent.Key event) {
        cancelUse(event);
    }

    @SubscribeEvent
    public static void onMouseInput(net.minecraftforge.client.event.InputEvent.MouseButton event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null && isAnimationPlaying(player.getUUID())) {
            if (mc.options.keyAttack.matchesMouse(event.getButton()) || mc.options.keyUse.matchesMouse(event.getButton())) {
                if (event.isCancelable()) event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onInteraction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null && isAnimationPlaying(player.getUUID()) && event.isCancelable()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void cancelUse(net.minecraftforge.client.event.InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null && isAnimationPlaying(player.getUUID())) {
            if (mc.options.keyAttack.matches(event.getKey(), event.getScanCode())
                    || mc.options.keyUse.matches(event.getKey(), event.getScanCode())
                    || mc.options.keyDrop.matches(event.getKey(), event.getScanCode())
                    || mc.options.keySwapOffhand.matches(event.getKey(), event.getScanCode())) {
                if (event.isCancelable()) event.setCanceled(true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player) {
        IAnimation animation = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
        if (animation instanceof ModifierLayer<?> layer) return (ModifierLayer<IAnimation>) layer;
        return null;
    }

    private static KeyframeAnimation findAnim(String name) {
        KeyframeAnimation direct = PlayerAnimationRegistry.getAnimation(new ResourceLocation("zombierool", name));
        if (direct != null) return direct;
        Map<String, KeyframeAnimation> owned = PlayerAnimationRegistry.getModAnimations("zombierool");
        if (owned == null) return null;
        if (owned.containsKey(name)) return owned.get(name);
        for (Map.Entry<String, KeyframeAnimation> entry : owned.entrySet()) {
            if (entry.getKey().endsWith(name) || entry.getKey().contains(name)) return entry.getValue();
        }
        return null;
    }

    private static final class Playing {
        final String name;
        final KeyframeAnimationPlayer playback;
        final Runnable whenDone;

        Playing(String name, KeyframeAnimationPlayer playback, Runnable whenDone) {
            this.name = name;
            this.playback = playback;
            this.whenDone = whenDone;
        }
    }
}

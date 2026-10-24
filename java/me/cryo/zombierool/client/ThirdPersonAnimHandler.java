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
    private static AbstractClientPlayer localOwner;

    /** Cancellation never invokes a release/throw callback. */
    public static void reset() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=null)for(UUID id:ACTIVE.keySet()){
            Player player=mc.level.getPlayerByUUID(id);
            if(player instanceof AbstractClientPlayer client){var layer=layer(client);if(layer!=null)layer.setAnimation(null);}
        }
        if(localOwner!=null){var layer=layer(localOwner);if(layer!=null)layer.setAnimation(null);}
        ACTIVE.clear();localOwner=null;DrinkPerkAnimationHandler.reset();
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){reset();}

    public static void registerFactory(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, player -> new ModifierLayer<>());
    }

    public static void startAnim(UUID uuid, String type, int ticks) {
        startAnim(uuid, type, ticks, null);
    }

    public static void startAnim(UUID uuid, String type, int ticks, Runnable whenDone) {
        if("knuckle_cancel".equals(type)){stopAnim(uuid,"knuckle_crack");return;}
        String animName = "melee".equals(type) ? "knife_sweep" : type;
        Minecraft mc = Minecraft.getInstance();
        if(localOwner!=null&&localOwner!=mc.player)reset();
        localOwner=mc.player;
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
        playback.setFirstPersonMode(animName.startsWith("helmet_")?FirstPersonMode.VANILLA:FirstPersonMode.THIRD_PERSON_MODEL);
        playback.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        layer.setAnimation(playback);
        ACTIVE.put(uuid, new Playing(animName, playback, whenDone));
    }

    public static void stopAnim(UUID id,String name){
        Playing playing=ACTIVE.get(id);if(playing==null||!playing.name.equals(name))return;
        playing.playback.stop();ACTIVE.remove(id,playing);
        var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getPlayerByUUID(id) instanceof AbstractClientPlayer player){var layer=layer(player);if(layer!=null)layer.setAnimation(null);}
    }

    public static String currentAnim(UUID uuid) {
        Playing playing = ACTIVE.get(uuid);
        return playing == null ? null : playing.name;
    }

    public static int animationTick(UUID uuid){Playing playing=ACTIVE.get(uuid);return playing==null?0:playing.playback.getTick();}

    public static boolean isAnimationPlaying(UUID uuid) {
        Playing playing = ACTIVE.get(uuid);
        return playing != null && playing.playback.isActive();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||localOwner!=null&&(mc.player!=localOwner||!localOwner.isAlive())){reset();return;}
        ACTIVE.entrySet().removeIf(entry -> {
            var playing=entry.getValue();
            if(playing.name.equals("knuckle_crack")){
                int tick=playing.playback.getTick();var owner=mc.level.getPlayerByUUID(entry.getKey());
                for(int cue:new int[]{10,27})if(owner!=null&&playing.lastCueTick<cue&&tick>=cue)
                    mc.level.playLocalSound(owner.getX(),owner.getY(),owner.getZ(),net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool",cue==10?"knuckle_0":"knuckle_1")),net.minecraft.sounds.SoundSource.PLAYERS,.65f,1,false);
                playing.lastCueTick=tick;
            }
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
        float partialTick=(float)event.getPartialTick();
        // KeyframeAnimationPlayer reads the fraction stored by setupAnim, rather
        // than the argument of get3DTransform. Camera rendering precedes the model.
        playing.playback.setupAnim(partialTick);
        Vec3f rot = playing.playback.get3DTransform("head", TransformType.ROTATION, partialTick, Vec3f.ZERO);
        event.setPitch(event.getPitch() + (float)Math.toDegrees(rot.getX()));
        event.setYaw(event.getYaw() + (float)Math.toDegrees(rot.getY()));
        event.setRoll(event.getRoll() - (float)Math.toDegrees(rot.getZ()));
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
        int lastCueTick;

        Playing(String name, KeyframeAnimationPlayer playback, Runnable whenDone) {
            this.name = name;
            this.playback = playback;
            this.whenDone = whenDone;
        }
    }
}

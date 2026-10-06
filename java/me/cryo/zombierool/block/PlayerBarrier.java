package me.cryo.zombierool.block;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CBarrierStaticPacket;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerBarrier {
    private static final int WARN_TICKS = 100;
    private static final int MISS_WINDOW = 10;
    private static final int EGG_CHANCE = 20;
    private static final String VOICE_KEY = "zr_barrier_voice";
    private static final ResourceLocation VOICE = new ResourceLocation("zombierool", "return_to_combat");
    private static final ResourceLocation VOICE_EGG = new ResourceLocation("zombierool", "return_to_combat_egg");
    private static final int RESET_GAP = 15;
    private static final int DAMAGE_INTERVAL = 20;
    private static final float DAMAGE = 4.0F;

    private PlayerBarrier() {}

    private static String key(String kind, String field) {
        return "zr_barrier_" + kind + "_" + field;
    }

    public static void touch(Player player, boolean lethal) {
        if (!(player instanceof ServerPlayer server)) return;
        if (server.isCreative() || server.isSpectator() || !server.isAlive()) return;

        Level level = server.level();
        long now = level.getGameTime();
        String kind = lethal ? "death" : "damage";
        CompoundTag tag = server.getPersistentData();
        String touchKey = key(kind, "touch");
        String uiKey = key(kind, "ui");
        String warnKey = key(kind, "warn");
        String titledKey = key(kind, "titled");

        if (tag.getLong(uiKey) == now) return;

        boolean sameVisit = now - tag.getLong(touchKey) <= RESET_GAP && tag.contains(warnKey);
        if (!sameVisit) {
            tag.putLong(warnKey, now + WARN_TICKS);
            tag.putBoolean(titledKey, false);
            tag.putBoolean(key(kind, "resolved"), false);
        }
        tag.putLong(touchKey, now);
        tag.putLong(uiKey, now);

        long warnUntil = tag.getLong(warnKey);
        float elapsed = now < warnUntil ? 1.0F - (warnUntil - now) / (float) WARN_TICKS : 1.0F;
        syncStatic(server, now < warnUntil ? 0.16F + 0.64F * elapsed : 1.0F);
        if (now < warnUntil) {
            int seconds = (int) Math.max(1, (warnUntil - now + 19) / 20);
            boolean fresh = !tag.getBoolean(titledKey);
            warn(server, lethal, seconds, fresh);
            if (fresh) emit(server, "OnBarrierSpotted", kind);
            tag.putBoolean(titledKey, true);
            return;
        }

        if (!tag.getBoolean(key(kind, "resolved"))) {
            tag.putBoolean(key(kind, "resolved"), true);
            emit(server, "OnBarrierExecute", kind);
        }

        if (lethal) {
            server.displayClientMessage(Component.translatable("message.zombierool.death_barrier.hurting").withStyle(ChatFormatting.RED), true);
            server.invulnerableTime = 0;
            server.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            return;
        }

        server.displayClientMessage(Component.translatable("message.zombierool.damage_barrier.hurting").withStyle(ChatFormatting.RED), true);
        String nextKey = key(kind, "next");
        if (now < tag.getLong(nextKey)) return;
        tag.putLong(nextKey, now + DAMAGE_INTERVAL);
        server.invulnerableTime = 0;
        server.hurt(level.damageSources().magic(), DAMAGE);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer server)) return;
        CompoundTag tag = server.getPersistentData();
        if (!server.isAlive() || server.isCreative() || server.isSpectator()) {
            if (tag.contains(key("death", "warn"))) dropVisit(tag, "death");
            if (tag.contains(key("damage", "warn"))) dropVisit(tag, "damage");
            return;
        }
        long now = server.level().getGameTime();
        checkMiss(server, tag, now, "death");
        checkMiss(server, tag, now, "damage");
    }

    private static void checkMiss(ServerPlayer player, CompoundTag tag, long now, String kind) {
        String warnKey = key(kind, "warn");
        if (!tag.contains(warnKey) || tag.getBoolean(key(kind, "resolved"))) return;
        long touch = tag.getLong(key(kind, "touch"));
        if (now - touch <= 1) return;
        long warnUntil = tag.getLong(warnKey);
        if (touch >= warnUntil || warnUntil - touch > MISS_WINDOW) {
            if (now - touch > RESET_GAP) tag.remove(warnKey);
            return;
        }
        if (now < warnUntil) return;
        tag.putBoolean(key(kind, "resolved"), true);
        tag.remove(warnKey);
        emit(player, "OnBarrierMiss", kind);
    }

    private static void dropVisit(CompoundTag tag, String kind) {
        tag.remove(key(kind, "warn"));
        tag.putBoolean(key(kind, "resolved"), true);
    }

    private static void emit(ServerPlayer player, String event, String kind) {
        CompoundTag tag = player.getPersistentData();
        String stamp = "zr_barrier_evt_" + event;
        long now = player.level().getGameTime();
        if (tag.getLong(stamp) == now) return;
        tag.putLong(stamp, now);
        LuaScriptManager.callEvent(event, player.getUUID().toString(), kind, player.getX(), player.getY(), player.getZ());
    }

    private static void warn(ServerPlayer player, boolean lethal, int seconds, boolean showTitle) {
        String kind = lethal ? "death_barrier" : "damage_barrier";
        player.displayClientMessage(Component.translatable("message.zombierool." + kind + ".actionbar", seconds).withStyle(ChatFormatting.RED), true);
        if (!showTitle) return;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, WARN_TICKS, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.zombierool." + kind + ".title").withStyle(ChatFormatting.RED)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.zombierool." + kind + ".subtitle").withStyle(ChatFormatting.GOLD)));
        playVoice(player);
    }

    private static void syncStatic(ServerPlayer player, float intensity) {
        CompoundTag tag = player.getPersistentData();
        long now = player.level().getGameTime();
        float last = tag.getFloat("zr_barrier_static");
        long sentAt = tag.getLong("zr_barrier_static_tick");
        if (sentAt == now && intensity <= last) return;
        if (Math.abs(intensity - last) < 0.035F && now - sentAt < 8) return;
        tag.putFloat("zr_barrier_static", intensity);
        tag.putLong("zr_barrier_static_tick", now);
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CBarrierStaticPacket(intensity));
    }

    private static void playVoice(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        long now = player.level().getGameTime();
        if (now - tag.getLong(VOICE_KEY) < 10) return;
        tag.putLong(VOICE_KEY, now);

        boolean egg = player.getRandom().nextInt(EGG_CHANCE) == 0;
        player.connection.send(new ClientboundStopSoundPacket(VOICE, SoundSource.VOICE));
        player.connection.send(new ClientboundStopSoundPacket(VOICE_EGG, SoundSource.VOICE));
        player.playNotifySound(
                egg ? ZombieroolModSounds.RETURN_TO_COMBAT_EGG.get() : ZombieroolModSounds.RETURN_TO_COMBAT.get(),
                SoundSource.VOICE,
                1.0F,
                1.0F);
    }
}

package me.cryo.zombierool.client;

import java.util.*;
import me.cryo.zombierool.item.WhisperItem;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One positional loop per visible player, following the wielder on every sound tick. */
@Mod.EventBusSubscriber(modid="zombierool", value=Dist.CLIENT)
public final class WhisperSounds {
    private static final Map<Player, Loop> LOOPS = new HashMap<>();
    private static boolean ready(Player player) {
        var stack = player.getMainHandItem();
        return player.isAlive() && !player.isRemoved() && stack.getItem() instanceof WhisperItem gun
                && gun.getAmmo(stack) == 1 && !stack.getOrCreateTag().getBoolean(me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem.TAG_IS_RELOADING);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        for (var entry : List.copyOf(LOOPS.entrySet()))
            if (entry.getKey().level() != mc.level || !ready(entry.getKey())) entry.getValue().end();
        if (mc.level == null || mc.isPaused()) return;
        for (var player : mc.level.players()) if (ready(player) && !LOOPS.containsKey(player)) {
            var loop = new Loop(player); LOOPS.put(player, loop); mc.getSoundManager().play(loop);
        }
    }
    @SubscribeEvent public static void reload(net.minecraftforge.client.event.sound.SoundEngineLoadEvent e) { clear(); }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
    private static void clear() { for (var loop : List.copyOf(LOOPS.values())) loop.end(); }
    private static final class Loop extends AbstractTickableSoundInstance {
        private final Player player;
        Loop(Player player) {
            super(ZombieroolModSounds.FOUR_IS_READY.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player=player; looping=true; delay=0; volume=.45f; relative=false;
            attenuation=Attenuation.LINEAR; position();
        }
        private void position() { x=player.getX(); y=player.getEyeY(); z=player.getZ(); }
        void end() { stop(); LOOPS.remove(player, this); }
        @Override public void tick() {
            if (player.level()!=Minecraft.getInstance().level || !ready(player)) end(); else position();
        }
    }
}

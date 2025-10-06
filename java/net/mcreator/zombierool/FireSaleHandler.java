package net.mcreator.zombierool;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
// Removed ClientboundSoundPacket import as it's not used directly here

@Mod.EventBusSubscriber
public class FireSaleHandler {
    private static final ResourceLocation MUSIC_LOC = new ResourceLocation("zombierool", "fire_sale_music");
    private static final SoundEvent MUSIC = SoundEvent.createVariableRangeEvent(MUSIC_LOC);
    private static final Map<UUID,Integer> timers = new HashMap<>();

    public static void startFireSale(Player p) {
        if (p.level().isClientSide()) return;
        UUID id = p.getUUID();
        if (timers.containsKey(id)) return;
    
        float pitch = 0.5f + p.getRandom().nextFloat() * 1.5f;
        // 1) Notifier le client via playNotifySound
        // Change SoundSource.MASTER to SoundSource.AMBIENT to avoid cutting off other music
        if (p instanceof ServerPlayer sp) {
            sp.playNotifySound(
                MUSIC,
                SoundSource.AMBIENT, // Changed from MASTER
                1.0f,
                pitch
            );
        }
        // 2) En plus, jouer localement (immédiat en singleplayer ou pour ce joueur en multijoueur)
        // Change SoundSource.MASTER to SoundSource.AMBIENT to avoid cutting off other music
        p.level().playSound(
            p,
            p.blockPosition(),
            MUSIC,
            SoundSource.AMBIENT, // Changed from MASTER
            1.0f,
            pitch
        );
    
        timers.put(id, 600); // 30 seconds (600 ticks)
    }

     @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase!=TickEvent.Phase.END) return;
        Player p = ev.player;
        if (p.level().isClientSide()) return;
        Integer t = timers.get(p.getUUID());
        if (t!=null) {
            if (--t <= 0) {
                // arrêter la musique
                if (p instanceof ServerPlayer sp) {
                    // Change SoundSource.MASTER to SoundSource.AMBIENT
                    sp.connection.send(new ClientboundStopSoundPacket(MUSIC_LOC, SoundSource.AMBIENT)); // Changed from MASTER
                }
                timers.remove(p.getUUID());
                // consommer le lingot exactement maintenant
                for (var stack : p.getInventory().items) {
                    if (stack.getItem() instanceof net.mcreator.zombierool.item.IngotSaleItem) {
                        stack.shrink(1);
                        break;
                    }
                }
            } else {
                timers.put(p.getUUID(), t);
            }
        }
    }
}

package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT)
public class ClientBlockCrackHandler {
    
    private static class CrackData {
        BlockPos pos;
        int initialBreakStage;
        int currentBreakStage;
        int ticksRemaining;
        int maxDuration;

        public CrackData(BlockPos pos, int breakStage) {
            this.pos = pos;
            this.initialBreakStage = Math.min(9, Math.max(0, breakStage));
            this.currentBreakStage = this.initialBreakStage;
            this.maxDuration = 300; // 15 secondes avant disparition totale
            this.ticksRemaining = this.maxDuration;
        }
    }

    private static final Map<Integer, CrackData> activeCracks = new ConcurrentHashMap<>();
    private static int idCounter = 10000;

    public static void handlePacket(BlockPos pos, int level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int existingId = -1;
        for (Map.Entry<Integer, CrackData> entry : activeCracks.entrySet()) {
            if (entry.getValue().pos.equals(pos)) {
                existingId = entry.getKey();
                break;
            }
        }

        int id = (existingId != -1) ? existingId : idCounter++;
        if (idCounter > 1000000) idCounter = 10000;

        CrackData data = new CrackData(pos, level);
        activeCracks.put(id, data);
        mc.levelRenderer.destroyBlockProgress(id, pos, level);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;

        Iterator<Map.Entry<Integer, CrackData>> iterator = activeCracks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, CrackData> entry = iterator.next();
            CrackData data = entry.getValue();
            data.ticksRemaining--;

            // Calculer l'étape de régénération
            // Plus le temps passe, plus on réduit le breakStage
            // On commence à réduire quand il reste 1/3 du temps
            if (data.ticksRemaining < data.maxDuration / 3) {
                 float progress = (float) data.ticksRemaining / (data.maxDuration / 3f);
                 int newStage = (int) (data.initialBreakStage * progress);
                 if (newStage != data.currentBreakStage && newStage >= 0) {
                     data.currentBreakStage = newStage;
                     mc.levelRenderer.destroyBlockProgress(entry.getKey(), data.pos, newStage);
                 }
            }

            if (data.ticksRemaining <= 0) {
                mc.levelRenderer.destroyBlockProgress(entry.getKey(), data.pos, -1);
                iterator.remove();
            }
        }
    }
}
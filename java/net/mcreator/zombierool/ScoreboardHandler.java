// ScoreboardHandler.java
package net.mcreator.zombierool;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.world.scores.Objective; // Import Objective class 

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ScoreboardHandler {
    public static final String OBJECTIVE_ID = "zr_score";

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Scoreboard scoreboard = server.getScoreboard();
        
        if (scoreboard.getObjective(OBJECTIVE_ID) == null) {
            scoreboard.addObjective(
                OBJECTIVE_ID,
                ObjectiveCriteria.DUMMY,
                Component.literal("Points"),
                ObjectiveCriteria.RenderType.INTEGER
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Scoreboard scoreboard = player.level().getScoreboard();
            var objective = scoreboard.getObjective(OBJECTIVE_ID);
            
            if (objective != null) {
                var score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective);
                if (score.getScore() == 0) { // initialise à 500 si jamais inexistant
                    score.setScore(500);
                }
                // Set the display objective for the player upon login 
                scoreboard.setDisplayObjective(1, objective); // Set to sidebar (slot 1) 
            }
        }
    }
}
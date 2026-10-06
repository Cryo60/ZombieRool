package me.cryo.zombierool.client.discord;

import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.gameplay.MapEventManager;
import me.cryo.zombierool.gameplay.WaveManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class DiscordPresence {
    private static final Logger LOGGER = LogManager.getLogger("ZombieRool");
    private static final String DISCORD_URL = "https://discord.gg/HGv2r44hXM";

    static volatile Snapshot current = Snapshot.HIDDEN;
    private static int ticks;
    private static long matchStartedAt;
    private static boolean warned;
    private static DiscordIpc ipc;

    private DiscordPresence() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        if (!ZRClientConfig.isLoaded()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!ZRClientConfig.discordPresence()) {
            current = Snapshot.HIDDEN;
            return;
        }
        String applicationId = ZRClientConfig.discordApplicationId();
        if (!applicationId.matches("\\d{5,25}")) {
            if (!warned) {
                warned = true;
                LOGGER.info("Rich Presence en attente : colle l'Application ID Discord dans config/zombierool-client.toml (discordApplicationId).");
            }
            return;
        }
        current = minecraft.level == null || minecraft.player == null
                ? Snapshot.menus()
                : capture(minecraft);
        start(applicationId);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MapEventManager.ClientHandler.mapName = "";
        matchStartedAt = 0;
        current = Snapshot.menus();
    }

    private static void start(String applicationId) {
        if (ipc != null) return;
        ipc = new DiscordIpc(applicationId);
        Thread thread = new Thread(ipc, "ZombieRool-Discord");
        thread.setDaemon(true);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(ipc::close, "ZombieRool-Discord-Stop"));
    }

    private static Snapshot capture(Minecraft minecraft) {
        String map = MapEventManager.ClientHandler.mapName;
        if ((map == null || map.isBlank()) && minecraft.getSingleplayerServer() != null) {
            map = minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }
        String headline = map == null || map.isBlank() ? "ZombieRool" : trim("ZombieRool · " + map.trim());

        String playerLabel = players(countPlayers(minecraft));
        boolean playing = WaveManager.isGameRunning() && WaveManager.getCurrentWave() > 0;
        if (!playing) {
            matchStartedAt = 0;
            return new Snapshot(headline, I18n.get("discord.zombierool.waiting", playerLabel), 0);
        }
        if (matchStartedAt == 0) matchStartedAt = System.currentTimeMillis() / 1000L;
        return new Snapshot(headline, I18n.get("discord.zombierool.round", WaveManager.getCurrentWave(), playerLabel), matchStartedAt);
    }

    private static String players(int count) {
        return I18n.get("discord.zombierool.players." + playerForm(count), count);
    }

    private static String playerForm(int count) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (language != null && language.startsWith("ru")) {
            int mod10 = count % 10;
            int mod100 = count % 100;
            if (mod10 == 1 && mod100 != 11) return "one";
            if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "few";
            return "many";
        }
        return count == 1 ? "one" : "many";
    }

    private static int countPlayers(Minecraft minecraft) {
        if (minecraft.getConnection() == null) return minecraft.player != null ? 1 : 0;
        int count = 0;
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (info.getGameMode() != GameType.SPECTATOR) count++;
        }
        return Math.max(1, count);
    }

    private static String trim(String value) {
        String clean = value.replace('\n', ' ').trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }

    record Snapshot(String details, String state, long startedAt) {
        static final Snapshot HIDDEN = new Snapshot("", "", 0);

        static Snapshot menus() {
            return new Snapshot("ZombieRool", I18n.get("discord.zombierool.menus"), 0);
        }

        String activityJson(int pid) {
            if (details.isEmpty() && state.isEmpty()) {
                return "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"" + System.nanoTime() + "\",\"args\":{\"pid\":" + pid + "}}";
            }
            StringBuilder activity = new StringBuilder();
            activity.append("{\"type\":0,\"status_display_type\":2");
            activity.append(",\"details\":\"").append(escape(details)).append('"');
            activity.append(",\"state\":\"").append(escape(state)).append('"');
            if (startedAt > 0) activity.append(",\"timestamps\":{\"start\":").append(startedAt).append('}');
            activity.append(",\"buttons\":[{\"label\":\"Discord\",\"url\":\"").append(DISCORD_URL).append("\"}]");
            activity.append(",\"instance\":false}");
            return "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"" + System.nanoTime()
                    + "\",\"args\":{\"pid\":" + pid + ",\"activity\":" + activity + "}}";
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}

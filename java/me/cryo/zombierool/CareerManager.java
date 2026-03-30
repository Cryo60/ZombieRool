package me.cryo.zombierool.career;

import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CProgressChallengePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CareerManager {

    public enum ChallengeType { HEADSHOTS, KILLS, WAVES, REVIVES, GRENADE_KILLS, OBSTACLE_BOUGHT, PERK_BOUGHT, PAP_USED, MYSTERY_BOX_USED }

    public static class ChallengeDef {
        public ChallengeType type;
        public int target;
        public int reward;
        public ChallengeDef(ChallengeType type, int target, int reward) { 
            this.type = type; 
            this.target = target; 
            this.reward = reward; 
        }
    }

    public static class CamoDef {
        public String langKey;
        public int price;
        public CamoDef(String langKey, int price) { 
            this.langKey = langKey; 
            this.price = price; 
        }
    }

    public static class CareerData {
        public int zrfBalance = 0;
        public List<String> unlockedCamos = new ArrayList<>();
        public Map<String, String> equippedCamos = new HashMap<>();
        public Map<String, Integer> challengeProgress = new HashMap<>();
        public Map<String, Boolean> challengeCompleted = new HashMap<>();
        public Map<String, ChallengeDef> activeChallenges = new HashMap<>();
        public long lastChallengeResetTime = 0;
    }

    public static void buyCamo(ServerPlayer player, String camoId) {
    }

    public static void equipCamo(ServerPlayer player, String weaponId, String camoId) {
    }

    public static void progressChallenge(ServerPlayer player, ChallengeType type, int amount) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CProgressChallengePacket(type.name(), amount));
    }
}
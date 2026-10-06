package me.cryo.zombierool.core.manager;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import me.cryo.zombierool.scripting.LuaScriptManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class LookTriggerManager {
    public static class Trigger {
        public String id;
        public Vec3 pos;
        public double radius;
        public int requiredTicks;
        public boolean requireScope;

        public Trigger(String id, Vec3 pos, double radius, int requiredTicks, boolean requireScope) {
            this.id = id;
            this.pos = pos;
            this.radius = radius;
            this.requiredTicks = requiredTicks;
            this.requireScope = requireScope;
        }
    }

    private static final Map<String, Trigger> triggers = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> playerProgress = new HashMap<>();

    public static void register(String id, double x, double y, double z, double radius, double seconds, boolean requireScope) {
        triggers.put(id, new Trigger(id, new Vec3(x, y, z), radius, (int) (seconds * 20), requireScope));
    }

    public static void remove(String id) {
        triggers.remove(id);
        for (Map<String, Integer> prog : playerProgress.values()) {
            prog.remove(id);
        }
    }

    public static void clearAll() {
        triggers.clear();
        playerProgress.clear();
    }

    public static void tick(ServerLevel level) {
        if (triggers.isEmpty()) return;

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;

            boolean isScoping = player.getPersistentData().getBoolean("zr_is_scoping");
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0f);

            Map<String, Integer> progress = playerProgress.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

            for (Trigger t : triggers.values()) {
                boolean lookingAt = false;

                if (!t.requireScope || isScoping) {
                    AABB box = new AABB(
                        t.pos.x - t.radius, t.pos.y - t.radius, t.pos.z - t.radius,
                        t.pos.x + t.radius, t.pos.y + t.radius, t.pos.z + t.radius
                    );
                    Optional<Vec3> clip = box.clip(eye, eye.add(look.scale(150.0))); 
                    if (clip.isPresent()) {
                        lookingAt = true;
                    }
                }

                if (lookingAt) {
                    int current = progress.getOrDefault(t.id, 0) + 1;
                    progress.put(t.id, current);

                    if (current == t.requiredTicks) {
                        LuaScriptManager.callEvent("OnLookTriggerActivated", player.getUUID().toString(), t.id);
                        progress.put(t.id, 0); 
                    }
                } else {
                    progress.put(t.id, 0);
                }
            }
        }
    }
}
package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cryo.zombierool.block.AbstractTechnicalBlock;
import me.cryo.zombierool.block.ObstacleDoorBlock;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem.UniversalSpawnerBlock;
import me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem.UniversalSpawnerBlockEntity;
import me.cryo.zombierool.init.KeyBindings;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LinkRenderer {
    public static List<BlockPos> clientPlayerSpawners = new ArrayList<>();
    public static boolean isSurvivalViewEnabled = false;
    private static int selectedChannelFilter = -1;
    private static int maxChannelFound = 0;

    private static class SpawnerInfo {
        final BlockPos pos;
        final List<Integer> startChannels;
        final List<Integer> stopChannels;
        final float r, g, b;

        public SpawnerInfo(BlockPos pos, List<Integer> startChannels, List<Integer> stopChannels, float r, float g, float b) {
            this.pos = pos;
            this.startChannels = startChannels;
            this.stopChannels = stopChannels;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isCreative()) return;
        if (KeyBindings.CYCLE_CHANNEL_KEY.consumeClick()) {
            selectedChannelFilter++;
            if (selectedChannelFilter > maxChannelFound) {
                selectedChannelFilter = -1;
            }
            String msg = (selectedChannelFilter == -1)
                    ? "Tous les canaux"
                    : String.valueOf(selectedChannelFilter);
            mc.player.displayClientMessage(
                    Component.literal("§eFiltre des Liens : " + msg),
                    true
            );
        }
        if (KeyBindings.TOGGLE_SURVIVAL_VIEW_KEY.consumeClick()) {
            isSurvivalViewEnabled = !isSurvivalViewEnabled;
            mc.levelRenderer.allChanged();
            String status = isSurvivalViewEnabled ? "Activée" : "Désactivée";
            mc.player.displayClientMessage(
                    Component.literal("§eVue Survie : " + status),
                    true
            );
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isCreative() || mc.level == null) {
            return;
        }
        ItemStack mainStack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offStack = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        boolean holdingTool = isConfigItem(mainStack) || isConfigItem(offStack);
        if (!holdingTool) {
            return;
        }

        Level level = mc.level;
        List<SpawnerInfo> spawnersToRender = new ArrayList<>();
        Map<Integer, List<BlockPos>> obstaclesByChannel = new HashMap<>();
        int currentMaxChannel = 0;
        ChunkPos center = mc.player.chunkPosition();
        int renderDistance = mc.options.getEffectiveRenderDistance();

        for (int x = center.x - renderDistance; x <= center.x + renderDistance; x++) {
            for (int z = center.z - renderDistance; z <= center.z + renderDistance; z++) {
                if (level.hasChunk(x, z)) {
                    LevelChunk chunk = level.getChunk(x, z);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be.isRemoved()) continue;

                        if (be instanceof UniversalSpawnerBlockEntity spawner) {
                            List<Integer> starts = spawner.getParsedChannels(spawner.getStartChannels());
                            List<Integer> stops = spawner.getParsedChannels(spawner.getStopChannels());
                            
                            float r = 0, g = 0, b = 0;
                            switch (spawner.getMobType()) {
                                case ZOMBIE -> { r = 0.0f; g = 0.0f; b = 1.0f; } // Blue
                                case CRAWLER -> { r = 0.0f; g = 1.0f; b = 0.0f; } // Green
                                case HELLHOUND -> { r = 1.0f; g = 0.0f; b = 0.0f; } // Red
                                case PLAYER -> { r = 1.0f; g = 1.0f; b = 0.0f; } // Yellow
                            }
                            spawnersToRender.add(new SpawnerInfo(be.getBlockPos(), starts, stops, r, g, b));
                            
                            for (int c : starts) currentMaxChannel = Math.max(currentMaxChannel, c);
                            for (int c : stops) currentMaxChannel = Math.max(currentMaxChannel, c);
                            
                        } else if (be instanceof ObstacleDoorBlockEntity obstacle) {
                            try {
                                int c = Integer.parseInt(obstacle.getCanal());
                                obstaclesByChannel.computeIfAbsent(c, k -> new ArrayList<>()).add(be.getBlockPos());
                                currentMaxChannel = Math.max(currentMaxChannel, c);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }

        maxChannelFound = currentMaxChannel;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Vec3 camPos = event.getCamera().getPosition();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(3.0F);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (SpawnerInfo spawner : spawnersToRender) {
            for (int startCh : spawner.startChannels) {
                if (selectedChannelFilter == -1 || startCh == selectedChannelFilter) {
                    BlockPos targetPos = null;
                    if (startCh == 0) {
                        if (!clientPlayerSpawners.isEmpty()) targetPos = clientPlayerSpawners.get(0);
                    } else {
                        targetPos = getClosest(spawner.pos, obstaclesByChannel.get(startCh));
                    }
                    if (targetPos != null) drawSegmentedLine(level, buffer, matrix, spawner.pos, targetPos, spawner.r, spawner.g, spawner.b);
                }
            }

            for (int stopCh : spawner.stopChannels) {
                if (stopCh > 0 && (selectedChannelFilter == -1 || stopCh == selectedChannelFilter)) {
                    BlockPos targetPos = getClosest(spawner.pos, obstaclesByChannel.get(stopCh));
                    if (targetPos != null) {
                        drawSegmentedLine(level, buffer, matrix, spawner.pos, targetPos, 1.0f, 0.0f, 1.0f); // Magenta
                    }
                }
            }
        }

        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static BlockPos getClosest(BlockPos origin, List<BlockPos> targets) {
        if (targets == null || targets.isEmpty()) return null;
        BlockPos closest = null;
        double minSq = Double.MAX_VALUE;
        for (BlockPos t : targets) {
            double d = origin.distSqr(t);
            if (d < minSq) { minSq = d; closest = t; }
        }
        return closest;
    }

    private static boolean isConfigItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return false;
        return id.getPath().equals("universal_spawner") || item == ZombieroolModBlocks.OBSTACLE_DOOR.get().asItem();
    }

    private static boolean isVisualObstacle(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        Block block = state.getBlock();
        if (block instanceof AbstractTechnicalBlock) return false;
        if (block instanceof UniversalSpawnerBlock || block instanceof ObstacleDoorBlock) return false;
        return state.isSolidRender(level, pos) || state.canOcclude();
    }

    private static void drawSegmentedLine(Level level, BufferBuilder buffer, Matrix4f matrix, BlockPos start, BlockPos end, float r, float g, float b) {
        Vec3 startVec = new Vec3(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
        Vec3 endVec = new Vec3(end.getX() + 0.5, end.getY() + 0.5, end.getZ() + 0.5);
        Vec3 diff = endVec.subtract(startVec);
        double dist = diff.length();
        Vec3 dir = diff.normalize();
        double stepSize = 0.5;
        for (double d = 0; d < dist; d += stepSize) {
            Vec3 p1 = startVec.add(dir.scale(d));
            double nextD = Math.min(d + stepSize, dist);
            Vec3 p2 = startVec.add(dir.scale(nextD));
            BlockPos checkPos = BlockPos.containing(p1);
            if (!isVisualObstacle(level, checkPos)) {
                buffer.vertex(matrix, (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, 1.0f).endVertex();
                buffer.vertex(matrix, (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, 1.0f).endVertex();
            }
        }
    }
}
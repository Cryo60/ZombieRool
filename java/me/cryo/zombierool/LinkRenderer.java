package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.block.AbstractTechnicalBlock;
import me.cryo.zombierool.block.ObstacleDoorBlock;
import me.cryo.zombierool.block.PlayerSpawnerBlock;
import me.cryo.zombierool.block.SpawnerCrawlerBlock;
import me.cryo.zombierool.block.SpawnerDogBlock;
import me.cryo.zombierool.block.SpawnerZombieBlock;
import me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerDogBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerZombieBlockEntity;
import me.cryo.zombierool.init.KeyBindings;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.SyncMapVisualsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
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
	    final int channel;
	    final float r, g, b;
	
	    public SpawnerInfo(BlockPos pos, int channel, float r, float g, float b) {
	        this.pos = pos;
	        this.channel = channel;
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
	            ? Component.translatable("message.zombierool.filter_all").getString()
	            : String.valueOf(selectedChannelFilter);
	        mc.player.displayClientMessage(
	            Component.translatable("message.zombierool.channel_filter", msg), 
	            true
	        );
	    }
	
	    if (KeyBindings.TOGGLE_SURVIVAL_VIEW_KEY.consumeClick()) {
	        isSurvivalViewEnabled = !isSurvivalViewEnabled;
	        mc.levelRenderer.allChanged();
	        String status = isSurvivalViewEnabled 
	            ? Component.translatable("message.zombierool.enabled").getString()
	            : Component.translatable("message.zombierool.disabled").getString();
	        mc.player.displayClientMessage(
	            Component.translatable("message.zombierool.survival_view", status),
	            true
	        );
	    }
	}
	
	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
	    // RENDU CRITIQUE :
	    // AFTER_SOLID_BLOCKS permet de dessiner les lignes APRES les murs (donc les murs cachent les lignes si DepthTest activé)
	    // MAIS AVANT les blocs Translucides (comme AbstractTechnicalBlock).
	    // Ainsi, les lignes seront visibles "sous" les blocs techniques.
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
	
	    // Récupération des entités de bloc dans la distance de rendu
	    for (int x = center.x - renderDistance; x <= center.x + renderDistance; x++) {
	        for (int z = center.z - renderDistance; z <= center.z + renderDistance; z++) {
	            if (level.hasChunk(x, z)) {
	                LevelChunk chunk = level.getChunk(x, z);
	                for (BlockEntity be : chunk.getBlockEntities().values()) {
	                    if (be.isRemoved()) continue;
	                    
	                    int channel = -1;
	                    if (be instanceof SpawnerZombieBlockEntity zSpawner) {
	                        channel = zSpawner.getCanal();
	                        spawnersToRender.add(new SpawnerInfo(be.getBlockPos(), channel, 0.0f, 0.0f, 1.0f));
	                    } 
	                    else if (be instanceof SpawnerCrawlerBlockEntity cSpawner) {
	                        channel = cSpawner.getCanal();
	                        spawnersToRender.add(new SpawnerInfo(be.getBlockPos(), channel, 0.0f, 1.0f, 0.0f));
	                    } 
	                    else if (be instanceof SpawnerDogBlockEntity dSpawner) {
	                        channel = dSpawner.getCanal();
	                        spawnersToRender.add(new SpawnerInfo(be.getBlockPos(), channel, 1.0f, 0.0f, 0.0f));
	                    } 
	                    else if (be instanceof ObstacleDoorBlockEntity obstacle) {
	                        try {
	                            channel = Integer.parseInt(obstacle.getCanal());
	                            obstaclesByChannel.computeIfAbsent(channel, k -> new ArrayList<>()).add(be.getBlockPos());
	                        } catch (NumberFormatException ignored) {}
	                    }
	
	                    if (channel > currentMaxChannel) currentMaxChannel = channel;
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
	
	    // Configuration du rendu pour les lignes de debug
	    RenderSystem.enableBlend();
	    RenderSystem.defaultBlendFunc();
	    RenderSystem.disableCull();
	    
	    // On ACTIVE le test de profondeur pour que les murs solides cachent les lignes.
	    RenderSystem.enableDepthTest();
	    RenderSystem.depthFunc(GL11.GL_LEQUAL);
	    
	    // On désactive l'écriture dans le depth buffer pour éviter que les lignes ne se cachent entre elles 
	    // ou ne perturbent le rendu des blocs translucides suivants.
	    RenderSystem.depthMask(false);
	    
	    RenderSystem.setShader(GameRenderer::getPositionColorShader);
	    RenderSystem.lineWidth(3.0F); // Note: Peut être ignoré par certains drivers GPU, 1.0F est le fallback standard.
	
	    Tesselator tesselator = Tesselator.getInstance();
	    BufferBuilder buffer = tesselator.getBuilder();
	    buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
	
	    for (SpawnerInfo spawner : spawnersToRender) {
	        if (selectedChannelFilter != -1 && spawner.channel != selectedChannelFilter) {
	            continue;
	        }
	
	        BlockPos targetPos = null;
	        if (spawner.channel == 0) {
	            // Pour le canal 0, on lie au PlayerSpawner (synchronisé via packet)
	            if (!clientPlayerSpawners.isEmpty()) {
	                targetPos = clientPlayerSpawners.get(0);
	            }
	        } else {
	            // Pour les autres canaux, on cherche l'obstacle le plus proche
	            List<BlockPos> targets = obstaclesByChannel.get(spawner.channel);
	            if (targets != null && !targets.isEmpty()) {
	                double minSqDist = Double.MAX_VALUE;
	                for (BlockPos target : targets) {
	                    double sqDist = spawner.pos.distSqr(target);
	                    if (sqDist < minSqDist) {
	                        minSqDist = sqDist;
	                        targetPos = target;
	                    }
	                }
	            }
	        }
	
	        if (targetPos != null) {
	            drawSegmentedLine(level, buffer, matrix, spawner.pos, targetPos, spawner.r, spawner.g, spawner.b);
	        }
	    }
	
	    tesselator.end();
	    
	    // Restauration des états
	    RenderSystem.depthMask(true);
	    RenderSystem.enableCull();
	    RenderSystem.disableBlend();
	    poseStack.popPose();
	}
	
	private static boolean isConfigItem(ItemStack stack) {
	    if (stack.isEmpty()) return false;
	    Item item = stack.getItem();
	    return item == ZombieroolModBlocks.SPAWNER_ZOMBIE.get().asItem() ||
	           item == ZombieroolModBlocks.SPAWNER_CRAWLER.get().asItem() ||
	           item == ZombieroolModBlocks.SPAWNER_DOG.get().asItem() ||
	           item == ZombieroolModBlocks.OBSTACLE_DOOR.get().asItem() ||
	           item == ZombieroolModBlocks.PLAYER_SPAWNER.get().asItem();
	}
	
	private static boolean isVisualObstacle(Level level, BlockPos pos) {
	    BlockState state = level.getBlockState(pos);
	    if (state.isAir()) return false;
	    
	    Block block = state.getBlock();
	    
	    // 1. Les blocs techniques (invisibles/translucides) ne doivent jamais couper la ligne
	    if (block instanceof AbstractTechnicalBlock) {
	        return false;
	    }
	
	    // 2. Les blocs sources/destinations (Spawners, Portes) ne doivent pas couper la ligne
	    //    Cela permet à la ligne de partir du centre du bloc.
	    if (block instanceof SpawnerZombieBlock ||
	        block instanceof SpawnerCrawlerBlock ||
	        block instanceof SpawnerDogBlock ||
	        block instanceof ObstacleDoorBlock ||
	        block instanceof PlayerSpawnerBlock) {
	        return false;
	    }
	
	    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
	    if (id != null && id.getNamespace().equals("zombierool")) {
	        String path = id.getPath();
	        // Autres exceptions spécifiques
	        if (path.equals("restrict") || 
	            path.equals("traitor") ||
	            path.equals("limit") ||
	            path.equals("path")) {
	            return false; 
	        }
	    }
	
	    // 3. Sinon, on utilise la logique standard : si le bloc bloque le rendu (mur, pierre), c'est un obstacle
	    return state.isSolidRender(level, pos) || state.canOcclude();
	}
	
	private static void drawSegmentedLine(Level level, BufferBuilder buffer, Matrix4f matrix, BlockPos start, BlockPos end, float r, float g, float b) {
	    Vec3 startVec = new Vec3(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
	    Vec3 endVec = new Vec3(end.getX() + 0.5, end.getY() + 0.5, end.getZ() + 0.5);
	    
	    Vec3 diff = endVec.subtract(startVec);
	    double dist = diff.length();
	    Vec3 dir = diff.normalize();
	    
	    double stepSize = 0.5;
	    // On itère le long de la ligne. Si un segment traverse un obstacle, il n'est pas dessiné.
	    // Comme isVisualObstacle retourne false pour les spawners/technical, la ligne passera à travers eux.
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
	
	@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
	public static class ServerTicker {
	    private static int tickCounter = 0;
	
	    @SubscribeEvent
	    public static void onServerTick(TickEvent.ServerTickEvent event) {
	        if (event.phase != TickEvent.Phase.END) return;
	        if (event.getServer() == null) return;
	
	        tickCounter++;
	        if (tickCounter >= 20) {
	            tickCounter = 0;
	            ServerLevel overworld = event.getServer().overworld();
	            if (overworld == null) return;
	            
	            WorldConfig config = WorldConfig.get(overworld);
	            List<BlockPos> playerSpawners = new ArrayList<>(config.getPlayerSpawnerPositions());
	            
	            if (playerSpawners.isEmpty()) return;
	            
	            SyncMapVisualsPacket packet = new SyncMapVisualsPacket(playerSpawners);
	            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
	                if (player.isCreative()) {
	                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
	                }
	            }
	        }
	    }
	}
}

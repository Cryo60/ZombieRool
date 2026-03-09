package me.cryo.zombierool.event;

import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GameplayEventHandler {
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onItemTossed(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_VULTURE.get())) {
            int randomMultiplier = RANDOM.nextInt(21) + 10;
            int pointsToAward = randomMultiplier * 10;
            PointManager.modifyScore(player, pointsToAward);
            if (!player.level().isClientSide()) {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy"));
                if (sound != null) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBonemealUsed(BonemealEvent event) {
        if (event.getBlock().getBlock() instanceof SaplingBlock sapling) {
            event.setResult(BonemealEvent.Result.ALLOW);
            if (!event.getLevel().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getLevel();
                BlockPos pos = event.getPos();
                BlockState state = event.getBlock();
                sapling.performBonemeal(serverLevel, serverLevel.getRandom(), pos, state);
                serverLevel.levelEvent(2005, pos, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            if (server == null) return;
            GameRules gameRules = serverLevel.getGameRules();
            if (gameRules.getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            }
        }
    }
}
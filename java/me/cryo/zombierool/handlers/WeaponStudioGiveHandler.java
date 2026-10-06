package me.cryo.zombierool.handlers;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.api.IPackAPunchable;
import net.minecraft.ChatFormatting;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WeaponStudioGiveHandler {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;
        
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            File giveFile = FMLPaths.CONFIGDIR.get().resolve("zombierool_give.txt").toFile();
            if (giveFile.exists()) {
                try {
                    List<String> lines = Files.readAllLines(giveFile.toPath());
                    giveFile.delete();
                    
                    for (String line : lines) {
                        String[] parts = line.split(",");
                        String id = parts[0].trim();
                        boolean isPap = parts.length > 1 && parts[1].trim().equalsIgnoreCase("true");
                        
                        if (!id.isEmpty()) {
                            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                                for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                                    if (player.hasPermissions(2)) {
                                        ItemStack stack = new ItemStack(item);
                                        if (item instanceof IReloadable reloadable) {
                                            reloadable.initializeIfNeeded(stack);
                                        }
                                        if (isPap && item instanceof IPackAPunchable papItem) {
                                            papItem.applyPackAPunch(stack);
                                        }
                                        
                                        if (!player.getInventory().add(stack)) {
                                            player.drop(stack, false);
                                        }
                                        String papMsg = isPap ? " [Pack-A-Punch]" : "";
                                        player.sendSystemMessage(Component.translatable("message.zombierool.weapon_studio.give", id, papMsg).withStyle(ChatFormatting.GREEN));
                                        player.level().playSound(null, player.blockPosition(), 
                                            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.item.pickup")), 
                                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

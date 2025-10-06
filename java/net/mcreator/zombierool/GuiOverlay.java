package net.mcreator.zombierool.client.gui;

import net.mcreator.zombierool.block.DefenseDoorBlock;
import net.mcreator.zombierool.block.ObstacleDoorBlock;
import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GuiOverlay {
    private static final Minecraft mc = Minecraft.getInstance();

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // Priorité 1 : Vérifier les portes à réparer
        BlockPos repairPos = DefenseDoorBlock.getDoorInRepairZone(mc.level, mc.player.blockPosition());
        if (repairPos != null) {
            handleRepairMessage(event, repairPos);
            return;
        }

        // Priorité 2 : Vérifier les portes à acheter
        BlockPos purchasePos = findNearbyObstacleDoor(mc.level, mc.player.blockPosition());
        if (purchasePos != null) {
            handlePurchaseMessage(event, purchasePos);
        }
    }

    private static void handleRepairMessage(RenderGuiOverlayEvent.Post event, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof DefenseDoorBlock) {
            int stage = state.getValue(DefenseDoorBlock.STAGE);
            if (stage < 5) {
                Component text = getTranslatedComponent(
                    "Maintenez F pour réparer (" + (5 - stage) + "/5)",
                    "Hold F to repair (" + (5 - stage) + "/5)"
                );
                drawCenteredText(event, text);
            }
        }
    }

    private static void handlePurchaseMessage(RenderGuiOverlayEvent.Post event, BlockPos pos) {
        if (mc.level.getBlockEntity(pos) instanceof ObstacleDoorBlockEntity be) {
            int prix = be.getPrix();
            String canal = be.getCanal();
            
            if (prix > 0) { // Removed canal check
                MutableComponent text = getTranslatedComponent(
                    "Appuyez sur F pour acheter (",
                    "Press F to purchase ("
                )
                .append(Component.literal(prix + " points").withStyle(ChatFormatting.GOLD))
                .append(")");
                
                int x = (event.getWindow().getGuiScaledWidth() - mc.font.width(text)) / 2;
                int y = event.getWindow().getGuiScaledHeight() / 2;
                event.getGuiGraphics().drawString(mc.font, text, x, y, 0xFFFFFF, true);
            }
        }
    }

    public static BlockPos findNearbyObstacleDoor(Level level, BlockPos playerPos) {
    // Vérifie les 6 directions autour du joueur
        for (Direction dir : Direction.values()) {
            BlockPos checkPos = playerPos.relative(dir);
            if (level.getBlockState(checkPos).getBlock() instanceof ObstacleDoorBlock) {
                return checkPos;
            }
        }
        return null;
    }

    private static void drawCenteredText(RenderGuiOverlayEvent.Post event, Component text) {
        int x = (event.getWindow().getGuiScaledWidth() - mc.font.width(text)) / 2;
        int y = event.getWindow().getGuiScaledHeight() - 60;
        event.getGuiGraphics().drawString(mc.font, text, x, y, 0xFFFFFF, true);
    }
}

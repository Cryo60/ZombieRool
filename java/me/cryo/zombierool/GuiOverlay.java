package me.cryo.zombierool.client.gui;
import me.cryo.zombierool.block.system.DefenseDoorSystem; 
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlockEntity;
import me.cryo.zombierool.init.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; 
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

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        BlockPos repairPos = DefenseDoorSystem.DefenseDoorBlock.getDoorInRepairZone(mc.level, mc.player.blockPosition()); 
        if (repairPos != null) {
            handleRepairMessage(event, repairPos);
            return;
        }
        BlockPos purchasePos = findNearbyObstacleDoor(mc.level, mc.player.blockPosition());
        if (purchasePos != null) {
            handlePurchaseMessage(event, purchasePos);
        }
    }

    private static void handleRepairMessage(RenderGuiOverlayEvent.Post event, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof DefenseDoorSystem.DefenseDoorBlock) { 
            int stage = state.getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE); 
            if (stage < 5) {
                String actionKey = KeyBindings.REPAIR_AND_PURCHASE_KEY.getTranslatedKeyMessage().getString().toUpperCase();
                Component text = Component.translatable("gui.zombierool.overlay.repair", actionKey, (5 - stage)).withStyle(ChatFormatting.YELLOW);
                drawCenteredText(event, text);
            }
        }
    }

    private static void handlePurchaseMessage(RenderGuiOverlayEvent.Post event, BlockPos pos) {
        if (mc.level.getBlockEntity(pos) instanceof ObstacleDoorBlockEntity be) {
            int prix = be.getPrix();
            String canal = be.getCanal();
            if (prix > 0) { 
                String actionKey = KeyBindings.REPAIR_AND_PURCHASE_KEY.getTranslatedKeyMessage().getString().toUpperCase();
                MutableComponent text = Component.translatable("gui.zombierool.overlay.purchase", actionKey, prix).withStyle(ChatFormatting.WHITE);
                int x = (event.getWindow().getGuiScaledWidth() - mc.font.width(text)) / 2;
                int y = event.getWindow().getGuiScaledHeight() / 2;
                event.getGuiGraphics().drawString(mc.font, text, x, y, 0xFFFFFF, true);
            }
        }
    }

    public static BlockPos findNearbyObstacleDoor(Level level, BlockPos playerPos) {
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

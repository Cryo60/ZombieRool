package net.mcreator.zombierool.item;

import net.minecraft.client.Minecraft; // Import added for Minecraft client access
import net.minecraft.world.level.Level;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import added for MutableComponent
import net.minecraft.ChatFormatting; // Import added for ChatFormatting

import java.util.List;

public class IngotSaleItem extends Item {
    
    // --- Translation Helper Methods (Copied from MannequinEntity) ---
    
    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        // This is fine since tooltips are client-side only
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }
    
    // ----------------------------------------------------------------------

    public IngotSaleItem() {
        super(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack itemstack, Level world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        
        // Use the translation helper method
        // We also apply the formatting code (\u00A75 is purple/dark purple)
        MutableComponent tooltipText = getTranslatedComponent(
            "Vous pouvez acheter n'importe quoi !", 
            "You can buy anything!"
        );
        
        // Applying the formatting code equivalent (ChatFormatting.LIGHT_PURPLE or ChatFormatting.DARK_PURPLE)
        // \u00A75 is equivalent to ChatFormatting.DARK_PURPLE
        list.add(tooltipText.withStyle(ChatFormatting.DARK_PURPLE));
    }
}

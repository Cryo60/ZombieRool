package me.cryo.zombierool.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Re-establishes TacZ's action state after changing the preferred weapon system. */
public final class TacZWeaponModeBridge {
    private TacZWeaponModeBridge() {}

    public static void resetActions(LocalPlayer player) {
        if (player == null || !ModList.get().isLoaded("tacz")) return;
        var operator = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player);
        var data = operator.getDataHolder();
        if (data.drawFuture != null) {
            data.drawFuture.cancel(false);
            data.drawFuture = null;
        }
        data.reset();
        data.lockedCondition = null;
        operator.resetDraw();
        // Draw also sends TacZ's own packet: the server clears reload, bolt,
        // sprint and shot state and refreshes the selected gun's Lua cache.
        operator.draw(ItemStack.EMPTY);
    }
}

package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

public final class ContainerWeaponGuard {
    private static final ThreadLocal<Player> ACTOR = new ThreadLocal<>();

    private ContainerWeaponGuard() {}

    public static void begin(Player player) {
        ACTOR.set(player);
    }

    public static void end() {
        ACTOR.remove();
    }

    public static boolean blocks(Container container, ItemStack stack) {
        if (stack.isEmpty() || !WeaponFacade.isWeapon(stack) || !isStorage(container)) return false;
        if (!WaveManager.isGameRunning()) return false;
        Player player = ACTOR.get();
        return player == null || (!player.isCreative() && !player.isSpectator());
    }

    public static boolean isStorage(Container container) {
        return container instanceof net.minecraft.world.CompoundContainer || container instanceof BaseContainerBlockEntity
                || container instanceof PlayerEnderChestContainer
                || container instanceof ChestBoat
                || container instanceof AbstractMinecartContainer;
    }
}

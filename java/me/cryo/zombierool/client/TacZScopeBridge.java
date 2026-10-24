package me.cryo.zombierool.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.*;
import net.minecraftforge.fml.ModList;

/** Reads TacZ's actual ADS state, including its toggle-aim setting and rebinding. */
@OnlyIn(Dist.CLIENT)
public final class TacZScopeBridge {
    private TacZScopeBridge(){}
    public static boolean isAiming(LocalPlayer player){
        return ModList.get().isLoaded("tacz")&&com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
    }
}

package me.cryo.zombierool;

import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.configuration.ZRClientConfig;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD)
public class HalloweenInit {
    static {
        ZRClientConfig.register();
    }
}
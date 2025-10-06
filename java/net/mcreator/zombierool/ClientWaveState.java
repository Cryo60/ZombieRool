package net.mcreator.zombierool.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientWaveState {
    private static boolean isSpecial = false;

    public static void setSpecialWave(boolean special) {
        isSpecial = special;
    }

    public static boolean isSpecialWave() {
        return isSpecial;
    }
}
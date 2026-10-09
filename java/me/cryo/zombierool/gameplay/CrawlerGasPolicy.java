package me.cryo.zombierool.gameplay;

public final class CrawlerGasPolicy {
    private CrawlerGasPolicy() {}
    /** Explosives rupture the gas sac; half of body kills do, while knife/head kills stay quiet. */
    public static boolean shouldExplode(boolean explosive,boolean melee,boolean headshot,float roll) {
        return explosive || (!melee && !headshot && roll<.5f);
    }
}

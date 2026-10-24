package me.cryo.zombierool.util;

/** Straight-alpha ABGR composition for deposited blood, not airborne mist. */
public final class BloodCoverage {
    private BloodCoverage() {}

    public static int depositedAlpha(int sampleAlpha, int opacity) {
        // blood_drop is a faint particle sprite (its core is only about 1/4 opaque).
        // Preserve its feathered outline while making the deposited center solid.
        return Math.min(255, sampleAlpha * 4) * opacity / 255;
    }

    public static int over(int previous, int deposited) {
        int a = deposited >>> 24;
        if (a == 0) return previous;
        int remaining = ((previous >>> 24) * (255 - a) + 127) / 255;
        int alpha = a + remaining;
        int result = alpha << 24;
        for (int shift = 0; shift < 24; shift += 8) {
            int channel = (((deposited >>> shift) & 255) * a
                + ((previous >>> shift) & 255) * remaining + alpha / 2) / alpha;
            result |= channel << shift;
        }
        return result;
    }
}

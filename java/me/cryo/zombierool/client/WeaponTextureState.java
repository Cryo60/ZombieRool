package me.cryo.zombierool.client;

/** Four depletion stages, including empty, scaled to the current magazine capacity. */
public final class WeaponTextureState {
    private WeaponTextureState() {}

    public static int depletedStage(int ammo, int capacity) {
        capacity = Math.max(1, capacity);
        ammo = Math.max(0, Math.min(capacity, ammo));
        return 4 - (int) Math.ceil(4.0 * ammo / capacity);
    }
}

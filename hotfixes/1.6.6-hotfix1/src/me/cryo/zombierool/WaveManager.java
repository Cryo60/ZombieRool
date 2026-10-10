package me.cryo.zombierool;
/** Compatibility for maps written before managers moved into subpackages. */
public class WaveManager extends me.cryo.zombierool.gameplay.WaveManager {
    public static final java.util.Set<java.util.UUID> activeMobs = sharedActiveMobs();
    @SuppressWarnings("unchecked")
    private static java.util.Set<java.util.UUID> sharedActiveMobs() {
        try {
            var field = me.cryo.zombierool.gameplay.WaveManager.class.getDeclaredField("activeMobs");
            field.setAccessible(true);
            return (java.util.Set<java.util.UUID>) field.get(null);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
}

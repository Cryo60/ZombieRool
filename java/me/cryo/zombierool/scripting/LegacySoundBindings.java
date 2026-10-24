package me.cryo.zombierool.scripting;

/** Compatibility entry point; all bindings now use the explicit safe API table. */
public final class LegacySoundBindings {
    private LegacySoundBindings() {}
    public static org.luaj.vm2.LuaValue wrap(ZombieroolAPI api) { return SafeLuaBindings.wrap(api); }
}

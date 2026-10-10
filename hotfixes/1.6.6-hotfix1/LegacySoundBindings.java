package me.cryo.zombierool.scripting;

import org.luaj.vm2.LuaUserdata;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

/** Keep legacy sound defaults deterministic without changing other Java bindings. */
public final class LegacySoundBindings {
    private LegacySoundBindings() {}

    public static LuaValue wrap(ZombieroolAPI api) {
        LuaValue original = CoerceJavaToLua.coerce(api);
        return new LuaUserdata(api) {
            @Override public LuaValue get(LuaValue key) {
                String methodName = key.tojstring();
                if (!methodName.equals("playGlobalSound") && !methodName.equals("playSoundForPlayer")
                        && !methodName.equals("playDynamicSoundForPlayer") && !methodName.equals("playSound"))
                    return original.get(key);
                return new VarArgFunction() {
                    @Override public Varargs invoke(Varargs supplied) {
                        Varargs args = supplied.arg1().touserdata() == api ? supplied.subargs(2) : supplied;
                        switch (methodName) {
                            case "playGlobalSound" -> api.playGlobalSound(args.checkjstring(1), number(args, 2), number(args, 3));
                            case "playSoundForPlayer" -> api.playSoundForPlayer(args.checkjstring(1), args.checkjstring(2), number(args, 3), number(args, 4));
                            case "playDynamicSoundForPlayer" -> api.playDynamicSoundForPlayer(args.checkjstring(1), args.checkjstring(2), number(args, 3), number(args, 4));
                            case "playSound" -> api.playSound(args.checkdouble(1), args.checkdouble(2), args.checkdouble(3), args.checkjstring(4), number(args, 5), number(args, 6));
                        }
                        return LuaValue.NONE;
                    }
                };
            }
            @Override public void set(LuaValue key, LuaValue value) { original.set(key, value); }
            @Override public LuaValue getmetatable() { return original.getmetatable(); }
        };
    }

    private static float number(Varargs args, int index) {
        return (float) args.optdouble(index, 1.0);
    }
}

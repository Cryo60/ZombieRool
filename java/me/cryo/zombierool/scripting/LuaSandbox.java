package me.cryo.zombierool.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.*;

/** In-memory Lua only. No Java bridge, OS, filesystem, native modules or binary chunks. */
public final class LuaSandbox {
    private LuaSandbox() {}

    /** Fatal to Lua's pcall/xpcall, caught only at host callback boundaries. */
    public static final class BudgetExceeded extends Error {
        BudgetExceeded() { super("Lua execution budget exceeded (loop or expensive callback)"); }
    }
    public static final class Budget extends DebugLib {
        private long deadline;
        private int remaining;
        public void reset() { deadline=System.nanoTime()+150_000_000; remaining=500_000; }
        /** Map startup performs bounded file/quest setup before gameplay begins. */
        public void resetStartup() { deadline=System.nanoTime()+2_000_000_000L; remaining=500_000; }
        @Override public void onInstruction(int pc,org.luaj.vm2.Varargs args,int top) {
            if (--remaining<=0 || System.nanoTime()>deadline) throw new BudgetExceeded();
            super.onInstruction(pc,args,top);
        }
    }

    public static Globals create() {
        Globals globals = new Globals();
        globals.load(new BaseLib());
        // LuaJ's library installers expect this registry. No module loader is installed.
        var registry = new org.luaj.vm2.LuaTable();
        registry.set("loaded", new org.luaj.vm2.LuaTable());
        globals.set("package", registry);
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new Bit32Lib());
        globals.load(new org.luaj.vm2.lib.jse.JseMathLib());
        var budget = new Budget();
        globals.load(budget);
        globals.set("debug", LuaValue.NIL);
        budget.reset();
        globals.set("package", LuaValue.NIL);
        LuaC.install(globals);
        globals.finder = name -> null;
        globals.STDIN = null;
        globals.STDOUT = null;
        globals.STDERR = null;
        // Only the host loads map files. Scripts cannot read resources or bytecode.
        for (String name : new String[]{"dofile", "loadfile", "load", "loadstring", "collectgarbage"})
            globals.set(name, LuaValue.NIL);
        globals.get("string").set("dump", LuaValue.NIL);
        return globals;
    }
}

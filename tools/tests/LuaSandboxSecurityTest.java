import me.cryo.zombierool.scripting.LuaSandbox;
import org.luaj.vm2.*;

/** Runs against the actual Lua dependency, with no Minecraft boot or filesystem side effects. */
public final class LuaSandboxSecurityTest {
    public static void main(String[] args) {
        Globals g=LuaSandbox.create();
        g.load("""
            for _, key in ipairs({'io','os','luajava','package','require','dofile','loadfile',
                    'debug','coroutine','load','loadstring'}) do assert(_G[key] == nil, key) end
            assert(string.dump == nil)
            assert(math.floor(4.8)==4 and bit32.band(7,3)==3)
            local t={3,1,2}; table.sort(t); assert(table.concat(t, ',')=='1,2,3')
            assert(string.match('map_123','%d+')=='123')
            local ok=pcall(function() error('ordinary Lua errors still work') end); assert(not ok)
            for _, attack in ipairs({
                function() return os.execute('unused') end,
                function() return io.open('unused') end,
                function() return luajava.bindClass('java.lang.Runtime') end,
                function() return require('java') end,
                function() return loadfile('unused') end
            }) do assert(not pcall(attack)) end
            """
        ).call();
        var budget=(LuaSandbox.Budget)g.debuglib;
        for(String attack:new String[]{"while true do end", "while true do pcall(function() while true do end end) end",
                "xpcall(function() while true do end end, function() return 0 end)"}) {
            budget.reset();
            try { g.load(attack).call();throw new AssertionError("Budget bypass"); }
            catch(LuaSandbox.BudgetExceeded expected) {}
        }
        budget.reset();g.load("assert(2+3==5)").call();
        System.out.println("PASS: blocked system/Java/file/module access; Lua math/tables/strings/pcall preserved; protected infinite loops interrupted; VM recovers.");
    }
}

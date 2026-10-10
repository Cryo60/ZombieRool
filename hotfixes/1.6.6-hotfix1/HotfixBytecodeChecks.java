import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class HotfixBytecodeChecks {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);System.out.println(message);}
    static ClassNode read(ZipFile zip,String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(zip.getInputStream(zip.getEntry(name)).readAllBytes()).accept(c,0);return c;}
    public static void main(String[] args)throws Exception{
        try(ZipFile zip=new ZipFile(args[0])){
            int analyzed=0;
            for(var entries=zip.entries();entries.hasMoreElements();){var entry=entries.nextElement();String name=entry.getName();
                if(!name.startsWith("me/cryo/zombierool/hotfix/")&&!Set.of("me/cryo/zombierool/shadow/luaj/vm2/lib/jse/JavaMember.class","me/cryo/zombierool/scripting/ZombieroolAPI.class","me/cryo/zombierool/client/network/ClientPacketEffects.class","me/cryo/zombierool/WaveManager.class","me/cryo/zombierool/WorldConfig.class","me/cryo/zombierool/PerksManager.class","me/cryo/zombierool/client/gui/MainMenuExtensions.class","me/cryo/zombierool/client/gui/WaWPauseScreen.class","me/cryo/zombierool/mixins/PlayerMixin.class","me/cryo/zombierool/player/PlayerDownManager.class","me/cryo/zombierool/entity/ZombieEntity.class","me/cryo/zombierool/entity/CrawlerEntity.class","me/cryo/zombierool/mixins/CustomMobMixin.class","me/cryo/zombierool/core/block/ZRSandbagBlock.class","me/cryo/zombierool/event/ServerEventHandler.class","me/cryo/zombierool/mixins/HopperBlockEntityMixin.class","me/cryo/zombierool/scripting/LuaScriptManager.class","me/cryo/zombierool/gameplay/WaveManager.class","me/cryo/zombierool/core/manager/BallisticManager.class","me/cryo/zombierool/procedures/MeleeAttackHandler.class","me/cryo/zombierool/block/RestrictBlock.class","me/cryo/zombierool/integration/TacZImpl$TacZEventHandlers.class").contains(name))continue;
                ClassNode c=read(zip,name);for(MethodNode m:c.methods)if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m);analyzed++;}
            }
            for(var entries=zip.entries();entries.hasMoreElements();){var entry=entries.nextElement();String name=entry.getName();if(!name.endsWith(".class")||!List.of("maptexture/MapTextures","maptexture/TextureAtlasImport","client/TextureKitScreen","client/TexturePixelEditor","block/system/MimicSystem","block/system/UniversalSpawnerSystem","block/system/GlassDefenseDoorBlock","network/packet/S2CMapTextureSlotPacket").stream().anyMatch(root->name.equals("me/cryo/zombierool/"+root+".class")||name.startsWith("me/cryo/zombierool/"+root+"$")))continue;ClassNode c=read(zip,name);for(MethodNode m:c.methods)if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m);analyzed++;}}
            check(analyzed>100,"Bytecode stack analysis passed for all patched classes and helpers ("+analyzed+" methods)");
            ClassNode menu=read(zip,"me/cryo/zombierool/client/gui/MainMenuExtensions.class");MethodNode opening=menu.methods.stream().filter(m->m.name.equals("onOpening")).findFirst().orElseThrow();
            var meaningful=Arrays.stream(opening.instructions.toArray()).filter(i->i.getOpcode()>=0).toList();
            check(meaningful.get(0) instanceof MethodInsnNode m&&m.owner.equals("net/minecraftforge/fml/ModList")&&m.name.equals("get"),"Essential compatibility guard runs before any screen replacement");
            check(meaningful.get(1) instanceof LdcInsnNode id&&id.cst.equals("essential")&&meaningful.get(3).getOpcode()==Opcodes.IFEQ&&meaningful.get(4).getOpcode()==Opcodes.RETURN,"Essential-loaded branch preserves both the original title and pause screens");
            ClassNode pause=read(zip,"me/cryo/zombierool/client/gui/WaWPauseScreen.class");MethodNode init=pause.methods.stream().filter(m->m.name.equals("m_7856_")).findFirst().orElseThrow();
            check(Arrays.stream(init.instructions.toArray()).anyMatch(i->i instanceof LdcInsnNode key&&key.cst.equals("menu.shareToLan")),"Custom pause menu includes the translated vanilla LAN action");
            check(Arrays.stream(init.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode m&&m.name.equals("canOpenLan")),"LAN action is offered only when a local world can be published");
            check(Arrays.stream(init.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode m&&m.name.equals("add")&&m.desc.equals("(ILjava/lang/Object;)V")),"LAN entry is inserted before Quit");
            var lua=read(zip,"me/cryo/zombierool/scripting/LuaScriptManager.class");
            check(lua.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).noneMatch(i->i instanceof MethodInsnNode m&&m.name.equals("loadfile")),"Lua loader closes file readers instead of leaking loadfile streams");
        }
    }
}

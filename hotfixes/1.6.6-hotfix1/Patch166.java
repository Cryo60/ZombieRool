import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Surgical patches of the released SRG classes; untouched entries retain identical bytes. */
public final class Patch166 implements Opcodes {
    static final String BASE="me/cryo/zombierool/",HELP=BASE+"hotfix/";
    static int patches;
    static final List<String> REPLACEMENTS=List.of("maptexture/MapTextures","maptexture/TextureAtlasImport","client/TextureKitScreen","client/TexturePixelEditor","block/system/MimicSystem","block/system/UniversalSpawnerSystem","block/system/GlassDefenseDoorBlock","network/packet/S2CMapTextureSlotPacket");
    static boolean replacement(String name){return REPLACEMENTS.stream().anyMatch(root->name.equals(BASE+root+".class")||name.startsWith(BASE+root+"$")&&name.endsWith(".class"));}
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static MethodInsnNode call(String owner,String name,String desc){return new MethodInsnNode(INVOKESTATIC,owner,name,desc,false);}
    static byte[] patch(String name,byte[] data){
        if(!Set.of(BASE+"shadow/luaj/vm2/lib/jse/JavaMember.class",BASE+"scripting/ZombieroolAPI.class",BASE+"client/network/ClientPacketEffects.class",BASE+"client/gui/MainMenuExtensions.class",BASE+"client/gui/WaWPauseScreen.class",BASE+"mixins/PlayerMixin.class",BASE+"player/PlayerDownManager.class",BASE+"entity/ZombieEntity.class",BASE+"entity/CrawlerEntity.class",BASE+"mixins/CustomMobMixin.class",BASE+"core/block/ZRSandbagBlock.class",BASE+"event/ServerEventHandler.class",BASE+"mixins/HopperBlockEntityMixin.class",BASE+"scripting/LuaScriptManager.class",BASE+"gameplay/WaveManager.class",BASE+"core/manager/BallisticManager.class",BASE+"procedures/MeleeAttackHandler.class",BASE+"block/RestrictBlock.class",BASE+"integration/TacZImpl$TacZEventHandlers.class").contains(name))return data;
        ClassNode cls=new ClassNode();new ClassReader(data).accept(cls,0);boolean changed=false;
        if(name.endsWith("ZRSandbagBlock.class")) {
            MethodNode blockType=new MethodNode(ACC_PUBLIC,"getBlockPathType","(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;",null,null);
            require(cls.methods.stream().noneMatch(m->m.name.equals(blockType.name)&&m.desc.equals(blockType.desc)),"Sandbag path type override already exists");
            blockType.instructions.add(new FieldInsnNode(GETSTATIC,"net/minecraft/world/level/pathfinder/BlockPathTypes","BLOCKED","Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"));blockType.instructions.add(new InsnNode(ARETURN));cls.methods.add(blockType);changed=true;patches++;
        }
        if(name.endsWith("ZombieroolAPI.class")) {
            for(String soundMethod:List.of("playGlobalSound","playSoundForPlayer","playDynamicSoundForPlayer","playSound")) {
                String prefix=soundMethod.equals("playGlobalSound")?"Ljava/lang/String;":soundMethod.equals("playSound")?"DDDLjava/lang/String;":"Ljava/lang/String;Ljava/lang/String;";
                for(int suppliedFloats=0;suppliedFloats<=1;suppliedFloats++) {
                    String desc="("+prefix+"F".repeat(suppliedFloats)+")V";
                    require(cls.methods.stream().noneMatch(m->m.name.equals(soundMethod)&&m.desc.equals(desc)),"Sound overload already exists");
                    MethodNode overload=new MethodNode(ACC_PUBLIC,soundMethod,desc,null,null);
                    overload.instructions.add(new VarInsnNode(ALOAD,0));int slot=1;
                    for(Type argument:Type.getArgumentTypes(desc)){overload.instructions.add(new VarInsnNode(argument.getOpcode(ILOAD),slot));slot+=argument.getSize();}
                    for(int defaulted=suppliedFloats;defaulted<2;defaulted++)overload.instructions.add(new InsnNode(FCONST_1));
                    overload.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,cls.name,soundMethod,"("+prefix+"FF)V",false));overload.instructions.add(new InsnNode(RETURN));cls.methods.add(overload);patches++;
                }
            }
            changed=true;
        }
        for(MethodNode method:cls.methods){
            if(name.endsWith("/JavaMember.class")&&method.name.equals("score")){
                LabelNode normal=new LabelNode();InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new VarInsnNode(ALOAD,1));hook.add(call(HELP+"HotfixGameplay","soundOverloadScore","(Ljava/lang/Object;L"+BASE+"shadow/luaj/vm2/Varargs;)I"));hook.add(new InsnNode(DUP));hook.add(new JumpInsnNode(IFLT,normal));hook.add(new InsnNode(IRETURN));hook.add(normal);if(cls.version>=V1_6)hook.add(new FrameNode(F_SAME1,0,null,1,new Object[]{INTEGER}));hook.add(new InsnNode(POP));method.instructions.insert(hook);changed=true;patches++;
            }

            if(name.equals(BASE+"gameplay/WaveManager.class")) {
                for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode m){
                    if(method.name.equals("startGame")&&m.owner.equals(BASE+"gameplay/MatchWorldJournal")&&m.name.equals("restore")){
                        InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(call(HELP+"HotfixMatchConfig","begin","(Lnet/minecraft/server/level/ServerLevel;)V"));method.instructions.insert(insn,hook);changed=true;patches++;
                    }
                    if(method.name.equals("endMatch")&&m.owner.equals(BASE+"config/WorldConfig")&&m.name.equals("get")){
                        InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(call(HELP+"HotfixMatchConfig","restore","(Lnet/minecraft/server/level/ServerLevel;)V"));method.instructions.insertBefore(insn,hook);changed=true;patches++;
                    }
                    if(method.name.equals("onServerStopping")&&m.owner.equals(BASE+"gameplay/MatchWorldJournal")&&m.name.equals("restore")){
                        InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,2));hook.add(call(HELP+"HotfixMatchConfig","restore","(Lnet/minecraft/server/level/ServerLevel;)V"));method.instructions.insert(insn,hook);changed=true;patches++;
                    }
                }
            }

            if(name.endsWith("ClientPacketEffects.class")&&method.name.equals("playGlobalSound")) {
                for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof FieldInsnNode f&&f.owner.equals("net/minecraftforge/registries/ForgeRegistries")&&f.name.equals("SOUND_EVENTS")){
                    InsnList resolve=new InsnList();resolve.add(new VarInsnNode(ALOAD,0));resolve.add(call(HELP+"HotfixGameplay","resolveLegacySound","(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceLocation;"));resolve.add(new VarInsnNode(ASTORE,0));method.instructions.insertBefore(insn,resolve);changed=true;patches++;
                }
            }

            if(name.endsWith("RestrictBlock.class")&&method.name.equals("m_5939_")){
                method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();
                method.instructions.add(new VarInsnNode(ALOAD,4));method.instructions.add(call(HELP+"HotfixGameplay","restrictCollision","(Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"));method.instructions.add(new InsnNode(ARETURN));changed=true;patches++;
            }
            if(name.endsWith("TacZImpl$TacZEventHandlers.class")&&method.name.equals("onAmmoHitBlock")){
                LabelNode normal=new LabelNode();InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKEVIRTUAL,"com/tacz/guns/api/event/server/AmmoHitBlockEvent","getState","()Lnet/minecraft/world/level/block/state/BlockState;",false));hook.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/world/level/block/state/BlockState","m_60734_","()Lnet/minecraft/world/level/block/Block;",false));hook.add(new TypeInsnNode(INSTANCEOF,BASE+"block/RestrictBlock"));hook.add(new JumpInsnNode(IFEQ,normal));hook.add(new VarInsnNode(ALOAD,0));hook.add(new InsnNode(ICONST_1));hook.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraftforge/eventbus/api/Event","setCanceled","(Z)V",false));hook.add(new InsnNode(RETURN));hook.add(normal);hook.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(hook);changed=true;patches++;
            }

            if(name.endsWith("ServerEventHandler.class")&&method.name.equals("isMatchInteractionLocked")) {
                method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();
                method.instructions.add(new InsnNode(ICONST_0));method.instructions.add(new InsnNode(IRETURN));changed=true;patches++;
            }
            if(name.endsWith("HopperBlockEntityMixin.class")&&method.name.startsWith("zombierool$block")) {
                method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();method.instructions.add(new InsnNode(RETURN));changed=true;patches++;
            }
            if(name.endsWith("LuaScriptManager.class")&&method.name.equals("loadScripts")) {
                for(var insn:method.instructions.toArray())if(insn instanceof MethodInsnNode m&&m.name.equals("loadfile")){
                    method.instructions.set(m,call(HELP+"HotfixGameplay","loadScript","(L"+BASE+"shadow/luaj/vm2/Globals;Ljava/lang/String;)L"+BASE+"shadow/luaj/vm2/LuaValue;"));changed=true;patches++;
                }

                for(var insn:method.instructions.toArray())if(insn instanceof MethodInsnNode m&&m.owner.equals("java/io/File")&&m.name.equals("listFiles")){
                    method.instructions.insert(m,call(HELP+"HotfixGameplay","sortScripts","([Ljava/io/File;)[Ljava/io/File;"));changed=true;patches++;
                }
            }
            if(name.endsWith("WaveManager.class")&&method.name.equals("startGame")) {
                for(var insn:method.instructions.toArray())if(insn instanceof MethodInsnNode m){
                    if(m.owner.endsWith("UniversalSpawnerSystem$UniversalSpawnerBlockEntity")&&m.name.equals("getMobType")) {
                        method.instructions.set(m,call(HELP+"HotfixGameplay","initialSpawnType","(L"+BASE+"block/system/UniversalSpawnerSystem$UniversalSpawnerBlockEntity;)L"+BASE+"block/system/UniversalSpawnerSystem$SpawnerMobType;"));changed=true;patches++;
                    }
                    if(m.owner.equals(BASE+"scripting/LuaScriptManager")&&m.name.equals("callEvent")) {
                        InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(call(BASE+"scripting/LuaScriptManager","loadScripts","(Lnet/minecraft/server/level/ServerLevel;)V"));method.instructions.insertBefore(m,hook);changed=true;patches++;
                    }
                }
            }
            if(name.endsWith("BallisticManager.class")||name.endsWith("MeleeAttackHandler.class")) {
                for(var insn:method.instructions.toArray())if(insn instanceof MethodInsnNode m&&m.name.equals("m_45547_")&&m.desc.equals("(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;")){
                    method.instructions.set(m,call(HELP+"HotfixGameplay","clip","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"));changed=true;patches++;
                }
            }

            if(name.endsWith("CustomMobMixin.class") && method.name.equals("onAiUpdate")) {
                for(AbstractInsnNode insn:method.instructions.toArray())
                    if(insn instanceof MethodInsnNode m && m.owner.equals("net/minecraft/world/entity/Mob") && m.name.equals("m_6710_") && m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;)V")) {
                        method.instructions.set(m,call(HELP+"ReachablePlayerTargetGoal","chooseAndSet","(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/LivingEntity;)V")); changed=true;patches++;
                    }
            }

            if(name.endsWith("MainMenuExtensions.class")&&method.name.equals("onOpening")){
                InsnList hook=new InsnList();LabelNode vanilla=new LabelNode();
                hook.add(call("net/minecraftforge/fml/ModList","get","()Lnet/minecraftforge/fml/ModList;"));hook.add(new LdcInsnNode("essential"));
                hook.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraftforge/fml/ModList","isLoaded","(Ljava/lang/String;)Z",false));hook.add(new JumpInsnNode(IFEQ,vanilla));hook.add(new InsnNode(RETURN));hook.add(vanilla);hook.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(hook);changed=true;patches++;
            }
            if(name.endsWith("WaWPauseScreen.class")&&method.name.equals("m_7856_")){
                AbstractInsnNode point=null;
                for(AbstractInsnNode insn:method.instructions)if(insn instanceof FieldInsnNode field&&field.name.equals("remembered")){point=insn.getPrevious();break;}
                require(point!=null&&point.getOpcode()==ALOAD,"Pause init insertion point");
                InsnList hook=new InsnList();LabelNode done=new LabelNode();hook.add(call(HELP+"HotfixMenuCompat","canOpenLan","()Z"));hook.add(new JumpInsnNode(IFEQ,done));
                hook.add(new VarInsnNode(ALOAD,0));hook.add(new FieldInsnNode(GETFIELD,cls.name,"entries","Ljava/util/List;"));hook.add(new InsnNode(ICONST_3));hook.add(new TypeInsnNode(NEW,cls.name+"$Entry"));hook.add(new InsnNode(DUP));hook.add(new LdcInsnNode("menu.shareToLan"));hook.add(new VarInsnNode(ALOAD,0));hook.add(call(HELP+"HotfixMenuCompat","lanAction","(Lnet/minecraft/client/gui/screens/Screen;)Ljava/lang/Runnable;"));hook.add(new MethodInsnNode(INVOKESPECIAL,cls.name+"$Entry","<init>","(Ljava/lang/String;Ljava/lang/Runnable;)V",false));hook.add(new MethodInsnNode(INVOKEINTERFACE,"java/util/List","add","(ILjava/lang/Object;)V",true));hook.add(done);hook.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insertBefore(point,hook);changed=true;patches++;
            }
            if(name.endsWith("PlayerMixin.class"))for(AbstractInsnNode insn:method.instructions)if(insn instanceof LdcInsnNode ldc&&ldc.cst instanceof Float value&&value==.01f){ldc.cst=.015f;changed=true;patches++;}
            if(name.endsWith("PlayerDownManager.class")&&method.name.equals("resetAll"))for(AbstractInsnNode insn:method.instructions.toArray())if(insn.getOpcode()==RETURN){method.instructions.insertBefore(insn,call(HELP+"HotfixDownReset","resetAll","()V"));changed=true;patches++;}
            if(name.endsWith("PlayerDownManager.class")&&method.name.equals("onPlayerRespawn")){
                InsnList hook=new InsnList();
                for(String field:List.of("playersDown","soloQuickRevivePlayers","savedPlayerInventories","lastKnownHandgun")){
                    hook.add(new FieldInsnNode(GETSTATIC,cls.name,field,"Ljava/util/Map;"));hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraftforge/event/entity/player/PlayerEvent$PlayerRespawnEvent","getEntity","()Lnet/minecraft/world/entity/player/Player;",false));hook.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/world/entity/player/Player","m_20148_","()Ljava/util/UUID;",false));hook.add(new MethodInsnNode(INVOKEINTERFACE,"java/util/Map","remove","(Ljava/lang/Object;)Ljava/lang/Object;",true));hook.add(new InsnNode(POP));
                }
                hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraftforge/event/entity/player/PlayerEvent$PlayerRespawnEvent","getEntity","()Lnet/minecraft/world/entity/player/Player;",false));hook.add(call(HELP+"HotfixDownReset","resetPlayer","(Lnet/minecraft/world/entity/player/Player;)V"));method.instructions.insert(hook);changed=true;patches++;
            }
            if(name.endsWith("ZombieEntity.class")||name.endsWith("CrawlerEntity.class"))for(AbstractInsnNode insn:method.instructions){
                String old="net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal";
                if(insn instanceof TypeInsnNode type&&type.getOpcode()==NEW&&type.desc.equals(old)){type.desc=HELP+"ReachablePlayerTargetGoal";changed=true;}
                if(insn instanceof MethodInsnNode ctor&&ctor.owner.equals(old)&&ctor.name.equals("<init>")&&ctor.desc.equals("(Lnet/minecraft/world/entity/Mob;Ljava/lang/Class;ZZ)V")){ctor.owner=HELP+"ReachablePlayerTargetGoal";changed=true;patches++;}
            }
        }
        require(changed,"No patch applied: "+name);ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);cls.accept(writer);return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        try(ZipFile original=new ZipFile(args[0]);ZipFile helpers=new ZipFile(args[1]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[2])))){
            var entries=original.entries();while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();byte[] bytes=original.getInputStream(entry).readAllBytes();String name=entry.getName();
                if(name.equals("META-INF/mods.toml"))bytes=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replace("version=\"1.6.6\"","version=\"1.6.6-hotfix1\"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(name.equals("META-INF/MANIFEST.MF"))bytes=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replace("Implementation-Version: 1.6.6","Implementation-Version: 1.6.6-hotfix1").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(replacement(name)){ZipEntry compiled=helpers.getEntry(name);require(compiled!=null||name.contains("$"),"Missing replacement "+name);if(compiled!=null)bytes=helpers.getInputStream(compiled).readAllBytes();}
                bytes=patch(name,bytes);out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry();
            }
            var additional=helpers.entries();while(additional.hasMoreElements()){ZipEntry e=additional.nextElement();if(original.getEntry(e.getName())!=null)continue;if(!(replacement(e.getName())||e.getName().startsWith(HELP)||Set.of(BASE+"WaveManager.class",BASE+"WorldConfig.class",BASE+"PerksManager.class").contains(e.getName()))||!e.getName().endsWith(".class"))continue;out.putNextEntry(new ZipEntry(e.getName()));out.write(helpers.getInputStream(e).readAllBytes());out.closeEntry();}
        }
        require(patches>=16,"Expected all hotfix patch points, got "+patches);System.out.println("Applied surgical hotfix patches to released classes.");
    }
}

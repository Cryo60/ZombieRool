import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Only for tests in Mojang-named userdev: FART does not resolve mixin shadow owners. */
public final class DevMixinNames {
 static String mapped(String name){return name.equals("m_5448_")?"getTarget":name.equals("m_6710_")?"setTarget":name;}
 public static void main(String[] args)throws Exception {
  try(ZipFile input=new ZipFile(args[0]);ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
   var entries=input.entries();while(entries.hasMoreElements()) {
    var entry=entries.nextElement();byte[] bytes=input.getInputStream(entry).readAllBytes();
    if(entry.getName().equals("me/cryo/zombierool/mixins/CustomMobMixin.class")) {
     ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
     for(MethodNode m:c.methods){m.name=mapped(m.name);for(var instruction:m.instructions)
      if(instruction instanceof MethodInsnNode call && call.owner.equals(c.name))call.name=mapped(call.name);}
     ClassWriter w=new ClassWriter(0);c.accept(w);bytes=w.toByteArray();
    }
    output.putNextEntry(new ZipEntry(entry.getName()));output.write(bytes);output.closeEntry();
   }
  }
 }
}

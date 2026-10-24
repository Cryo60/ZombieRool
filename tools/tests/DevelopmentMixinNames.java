import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Test-only names for shadow members whose owners are mixin classes, rather than Minecraft. */
public final class DevelopmentMixinNames {
    static final java.util.Map<String,String> names=new java.util.HashMap<>();
    static String mapped(String name){return names.getOrDefault(name,name);}
    public static void main(String[] args)throws Exception{
        for(String line:Files.readAllLines(Path.of(args[2]))){
            String[] parts=line.split(" ");
            if(parts[0].equals("FD:")||parts[0].equals("MD:")){
                String from=parts[1],to=parts[parts[0].equals("FD:")?2:3];
                names.put(from.substring(from.lastIndexOf('/')+1),to.substring(to.lastIndexOf('/')+1));
            }
        }
        try(ZipFile input=new ZipFile(args[0]);ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
            var entries=input.entries();while(entries.hasMoreElements()){
                var entry=entries.nextElement();byte[] bytes=input.getInputStream(entry).readAllBytes();
                if(entry.getName().startsWith(args.length > 3 ? args[3] : "me/cryo/zombierool/mixins/")&&entry.getName().endsWith(".class")){
                    ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
                    for(FieldNode f:c.fields)f.name=mapped(f.name);
                    for(MethodNode m:c.methods){m.name=mapped(m.name);for(var instruction:m.instructions){
                        if(instruction instanceof MethodInsnNode call&&(call.owner.equals(c.name)||call.name.startsWith("m_")))call.name=mapped(call.name);
                        if(instruction instanceof FieldInsnNode field&&(field.owner.equals(c.name)||field.name.startsWith("f_")))field.name=mapped(field.name);
                    }}
                    ClassWriter w=new ClassWriter(0);c.accept(w);bytes=w.toByteArray();
                }
                output.putNextEntry(new ZipEntry(entry.getName()));output.write(bytes);output.closeEntry();
            }
        }
    }
}

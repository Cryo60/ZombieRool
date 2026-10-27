package me.cryo.zombierool.maptexture;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Import original PNGs and their sidecars as pairs; never overwrite an authored texture. */
public final class MapTextureFileImport {
    private record Source(Path png,Path metadata){}
    private record Copy(Source source,Path target,String name){}
    private MapTextureFileImport(){}
    public static List<String> importFiles(Path world,String folder,List<Path> dropped)throws IOException{
        if(dropped.isEmpty()||dropped.size()>64)throw new IOException("Drop up to 32 PNG files with their .png.mcmeta files.");
        if(!folder.isEmpty()&&!MapTextures.sanitize(folder).equals(folder))throw new IOException("Invalid texture folder.");
        Path root=me.cryo.zombierool.util.WorldZombieroolPaths.path(world,"custom_blocks").toAbsolutePath().normalize();
        Path destination=root.resolve(folder).normalize();
        if(!destination.startsWith(root))throw new IOException("Invalid texture folder.");
        for(Path part=destination;part!=null&&part.startsWith(root);part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("Linked texture folders are not supported.");
        var sources=new LinkedHashMap<Path,Source>();
        for(Path input:dropped){
            String leaf=input.getFileName().toString();String lower=leaf.toLowerCase(Locale.ROOT);
            Path png=lower.endsWith(".png.mcmeta")?input.resolveSibling(leaf.substring(0,leaf.length()-7)):input;
            if(!png.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))throw new IOException("Only PNG and .png.mcmeta files are supported.");
            png=png.toAbsolutePath().normalize();
            if(!Files.isRegularFile(png)||Files.size(png)>1048576)throw new IOException("Missing PNG or PNG larger than 1 MB: "+png.getFileName());
            Path meta=MapTextureAnimation.metadata(png);
            if(Files.exists(meta))MapTextureAnimation.read(png);
            else TextureAtlasImport.convert(Files.readAllBytes(png),32);
            sources.putIfAbsent(png,new Source(png,Files.isRegularFile(meta)?meta:null));
        }
        var plan=new ArrayList<Copy>();var reserved=new HashSet<Path>();
        for(var source:sources.values()){
            String leaf=source.png.getFileName().toString();String stem=MapTextures.sanitize(leaf.substring(0,leaf.length()-4));
            if(stem.isEmpty())throw new IOException("Invalid texture name: "+leaf);
            String name=stem;Path target=destination.resolve(name+".png");int suffix=2;
            while((Files.exists(target)||Files.exists(MapTextureAnimation.metadata(target))||reserved.contains(target))&&!target.equals(source.png)){
                name=stem.substring(0,Math.min(55,stem.length()))+"_"+suffix++;target=destination.resolve(name+".png");
            }
            if(!reserved.add(target))continue;
            plan.add(new Copy(source,target,(folder.isEmpty()?"":folder+"/")+name));
        }
        long free=Arrays.stream(MapTextures.readSlots(world)).filter(s->s==null||s.isEmpty()).count();
        if(plan.stream().filter(c->!c.target.equals(c.source.png)).count()>free)throw new IOException("Not enough free texture slots (32 maximum).");
        Files.createDirectories(destination);var created=new ArrayList<Path>();
        try{
            for(var copy:plan){
                if(copy.target.equals(copy.source.png))continue;
                Files.copy(copy.source.png,copy.target);created.add(copy.target);
                if(copy.source.metadata!=null){Path meta=MapTextureAnimation.metadata(copy.target);Files.copy(copy.source.metadata,meta);created.add(meta);}
            }
        }catch(IOException error){for(var path:created)Files.deleteIfExists(path);throw error;}
        return plan.stream().map(Copy::name).toList();
    }
}

package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.*;
import java.io.*;

/** Keep large model trees out of vanilla metadata's two-MiB NBT accounting limit. */
public final class MapActorProfileWire {
    private static final String PACKED="zr_profile_gzip";
    private static final String VISUAL="zr_visual_key";
    private static final int MAX_COMPRESSED=1_000_000;
    private static final long MAX_DECODED=16L*1024*1024;
    private MapActorProfileWire(){}
    /** Client profiles are read-only; entities can reuse a decoded tree while it is still in use.
     * Weak values avoid retaining model trees after their last entity leaves the world. */
    public static final class ClientCache {
        private record Key(byte[] bytes,int hash) {
            Key(byte[] bytes){this(bytes,java.util.Arrays.hashCode(bytes));}
            @Override public int hashCode(){return hash;}
            @Override public boolean equals(Object other){return other instanceof Key key&&java.util.Arrays.equals(bytes,key.bytes);}
        }
        private final java.util.LinkedHashMap<Key,java.lang.ref.WeakReference<CompoundTag>> entries=new java.util.LinkedHashMap<>(64,.75f,true);
        private int packedBytes;
        public CompoundTag decode(CompoundTag wire){
            if(!wire.contains(PACKED,Tag.TAG_BYTE_ARRAY))return MapActorProfileWire.decode(wire);
            byte[] bytes=wire.getByteArray(PACKED);
            if(bytes.length>MAX_COMPRESSED)throw new IllegalArgumentException("Map actor profile exceeds network limit");
            var key=new Key(bytes);var reference=entries.get(key);var cached=reference==null?null:reference.get();
            if(cached!=null)return cached;
            var decoded=MapActorProfileWire.decode(wire);
            if(reference==null)packedBytes+=bytes.length;
            entries.put(key,new java.lang.ref.WeakReference<>(decoded));
            var iterator=entries.entrySet().iterator();
            while((entries.size()>64||packedBytes>4*1024*1024)&&iterator.hasNext()){
                packedBytes-=iterator.next().getKey().bytes.length;iterator.remove();
            }
            return decoded;
        }
        public void clear(){entries.clear();packedBytes=0;}
    }
    public static CompoundTag encode(CompoundTag profile){
        if(profile.isEmpty())return new CompoundTag();
        try{
            var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(profile,bytes);
            if(bytes.size()>MAX_COMPRESSED)throw new IllegalArgumentException("Map actor profile exceeds network limit");
            var wire=new CompoundTag();wire.putByteArray(PACKED,bytes.toByteArray());wire.putString(VISUAL,visualKey(profile));return wire;
        }catch(IOException e){throw new IllegalArgumentException("Cannot encode map actor profile",e);}
    }
    public static String visualKey(CompoundTag profile){
        try{
            var visual=new CompoundTag();for(String key:java.util.List.of("bones","boxes","animations","textureWidth","textureHeight","fullBright","despawnAfterDeath","renderAlpha","sourceMotion"))if(profile.contains(key))visual.put(key,profile.get(key));
            var digest=java.security.MessageDigest.getInstance("SHA-256");try(var out=new DataOutputStream(new java.security.DigestOutputStream(OutputStream.nullOutputStream(),digest))){canonical(visual,out);}return java.util.HexFormat.of().formatHex(digest.digest());
        }catch(IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static void canonical(Tag value,DataOutputStream out)throws IOException{
        out.writeByte(value.getId());
        if(value instanceof CompoundTag compound){var keys=new java.util.ArrayList<>(compound.getAllKeys());java.util.Collections.sort(keys);out.writeInt(keys.size());for(String key:keys){out.writeUTF(key);canonical(compound.get(key),out);}}
        else if(value instanceof ListTag list){out.writeInt(list.size());for(Tag item:list)canonical(item,out);}
        else NbtIo.writeUnnamedTag(value,out);
    }
    public static String visualKey(CompoundTag wire,CompoundTag profile){String key=wire.getString(VISUAL);return key.isEmpty()?visualKey(profile):key;}
    public static CompoundTag decode(CompoundTag wire){
        // Old world/entity data and uncompressed authoring profiles remain readable.
        if(!wire.contains(PACKED,Tag.TAG_BYTE_ARRAY))return wire;
        byte[] bytes=wire.getByteArray(PACKED);if(bytes.length>MAX_COMPRESSED)throw new IllegalArgumentException("Map actor profile exceeds network limit");
        try(var input=new DataInputStream(new java.util.zip.GZIPInputStream(new ByteArrayInputStream(bytes)))){
            return NbtIo.read(input,new NbtAccounter(MAX_DECODED));
        }catch(IOException e){throw new IllegalArgumentException("Cannot decode map actor profile",e);}
    }
}

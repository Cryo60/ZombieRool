package me.cryo.zombierool.util;
/** Bundled client data, outside the resource-pack asset namespace. */
public final class InternalResources {
 private InternalResources(){}
 public static java.io.InputStream open(String path)throws java.io.IOException{
  var in=InternalResources.class.getClassLoader().getResourceAsStream("META-INF/zombierool/"+path);
  if(in==null)throw new java.io.FileNotFoundException("Missing bundled data: "+path);
  return in;
 }
}

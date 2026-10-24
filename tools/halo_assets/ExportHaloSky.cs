using Reclaimer.Blam.Common;
using Reclaimer.Drawing;
using Reclaimer.Geometry;
using System.IO;
using System.Text.Json;
using System.Numerics;
var cache=CacheFactory.ReadCacheFile(args[0]);
var output=args.Length>1?args[1]:null;if(output!=null)Directory.CreateDirectory(output);
Console.WriteLine(cache.GetType().FullName);
foreach(var item in cache.TagIndex){
 if(output==null){if(item.TagName.Contains("sky",StringComparison.OrdinalIgnoreCase)||item.TagName.Contains("ambient",StringComparison.OrdinalIgnoreCase)||item.ClassCode=="lsnd")Console.WriteLine($"{item.Id} | {item.ClassCode} | {item.TagName}");continue;}
 if(item.ClassCode=="bitm"&&(item.TagName.Contains("sky",StringComparison.OrdinalIgnoreCase)||args.Length>3&&args[3].Split(",").Contains(item.Id.ToString())))try{
  if(ContentFactory.TryGetBitmapContent(item,out var provider)){var bitmap=provider.GetContent();for(int sub=0;sub<bitmap.SubmapCount;sub++){var dds=bitmap.ToDds(sub);dds.WriteToDisk(Path.Combine(output,$"{item.Id}_{sub}.dds"));Console.WriteLine($"BITMAP {item.Id} {sub} {dds.Width}x{dds.Height} {dds.FormatCode} {item.TagName}");}}
 }catch(Exception e){Console.WriteLine($"ERROR {item.Id} {e.GetType().Name}: {e.Message}");}
 if(item.ClassCode=="rmsh"&&item.TagName.Contains("sky",StringComparison.OrdinalIgnoreCase))try{var mat=item.ReadMetadata<Reclaimer.Blam.Halo3.ShaderTag>();var data=mat.ShaderProperties.Select(prop=>new{constants=prop.TilingData.Select(v=>new[]{v.X,v.Y,v.Z,v.W}),names=prop.TemplateReference.Tag.ReadMetadata<Reclaimer.Blam.Halo3.RenderMethodTemplateTag>().Arguments.Select(v=>v.ToString()),maps=prop.ShaderMaps.Select(m=>new{id=m.BitmapReference.Tag?.Id,name=m.BitmapReference.Tag?.TagName,tiling=m.TilingIndex})});File.WriteAllText(Path.Combine(output,$"material_{item.Id}.json"),JsonSerializer.Serialize(data));}catch(Exception e){Console.WriteLine(e.Message);}
 if(item.ClassCode=="mat "&&item.TagName.Contains("sky",StringComparison.OrdinalIgnoreCase))try{var mat=item.ReadMetadata<Reclaimer.Blam.Halo4.MaterialTag>();var data=mat.ShaderProperties.Select(prop=>new{blend=prop.BlendMode.ToString(),constants=prop.FloatConstants.Select(v=>new[]{v.X,v.Y,v.Z,v.W}),tiling=prop.TilingData.Select(v=>new[]{v.X,v.Y,v.Z,v.W}),maps=prop.ShaderMaps.Select(m=>new{id=m.BitmapReference.Tag?.Id,name=m.BitmapReference.Tag?.TagName,tiling=m.TilingIndex})});File.WriteAllText(Path.Combine(output,$"material_{item.Id}.json"),JsonSerializer.Serialize(data));}catch(Exception e){Console.WriteLine(e.Message);}
 if(item.ClassCode=="mode"&&item.Id==int.Parse(args[2]))try{
  ContentFactory.TryGetGeometryContent(item,out var provider);var scene=provider.GetContent();
  var meshes=scene.EnumerateModels().SelectMany(model=>model.Meshes).Where(mesh=>mesh!=null).Select(mesh=>new{positions=mesh.GetPositions().Select(v=>Vector3.Transform(v,mesh.PositionBounds.CreateExpansionMatrix())).Select(v=>new[]{v.X,v.Y,v.Z}).ToArray(),uv=mesh.GetTexCoords()?.Select(v=>Vector2.Transform(v,mesh.TextureBounds.CreateExpansionMatrix())).Select(v=>new[]{v.X,v.Y}).ToArray(),colors=mesh.VertexBuffer.HasColors?mesh.GetColors().Select(v=>new[]{v.X,v.Y,v.Z,v.W}).ToArray():null,segments=mesh.Segments.Select(seg=>new{indices=mesh.GetTriangleIndicies(seg).ToArray(),id=seg.Material?.Id,name=seg.Material?.Name,alpha=seg.Material?.AlphaMode,mappings=seg.Material?.TextureMappings.Select(m=>new{usage=m.Usage,id=m.Texture.Id,name=m.Texture.Name,tile=new[]{m.Tiling.X,m.Tiling.Y}}),tints=seg.Material?.Tints.Select(t=>new{usage=t.Usage,color=new[]{t.Color.R,t.Color.G,t.Color.B,t.Color.A}})})});
  File.WriteAllText(Path.Combine(output,"geometry.json"),JsonSerializer.Serialize(meshes));Console.WriteLine($"GEOMETRY {item.Id} {item.TagName}");
 }catch(Exception e){Console.WriteLine(e.ToString());}
}

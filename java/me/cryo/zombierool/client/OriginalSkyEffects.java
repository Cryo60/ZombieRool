package me.cryo.zombierool.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Playback adapter for the retained original FX curves, texture atlases and aircraft rig. */
public final class OriginalSkyEffects {
    private static Object owner;
    private static final Map<String,List<Element>> effects=new HashMap<>();
    private static float[] earth;
    private static AirRig aircraft;
    private record Sprite(float[] rect,boolean additive){}
    private record Range(float base,float amplitude){float at(float random){return base+amplitude*random;}}
    private static final class Element {
        final int type,flags,interval,spawnCount,atlasBehavior,atlasIndex,atlasFps,atlasCount,atlasRange,cols,rows;
        final boolean looping;final Range life,delay,radius,height,rotation,gravity;final Range[] origin,angles;
        final float[][] samples,velocity;final Sprite[] sprites;final float[] pivot;
        Element(JsonObject n){
            type=n.get("type").getAsInt();flags=n.get("flags").getAsInt();looping=n.get("group").getAsString().equals("looping");var spawn=n.getAsJsonObject("spawn");interval=Math.max(1,spawn.get("base").getAsInt());spawnCount=Math.max(0,spawn.get("base").getAsInt());
            life=range(n,"life");delay=range(n,"delay");radius=range(n,"offsetRadius");height=range(n,"offsetHeight");rotation=range(n,"initialRotation");gravity=range(n,"gravity");origin=ranges(n.getAsJsonArray("origin"));angles=ranges(n.getAsJsonArray("angles"));pivot=array(n.getAsJsonArray("pivot"));
            var atlas=n.getAsJsonObject("atlas");atlasBehavior=atlas.get("behavior").getAsInt();atlasIndex=atlas.get("index").getAsInt();atlasFps=atlas.get("fps").getAsInt();cols=1<<Math.min(8,atlas.get("colBits").getAsInt());rows=1<<Math.min(8,atlas.get("rowBits").getAsInt());atlasCount=Math.max(1,Math.min(cols*rows,atlas.get("entries").getAsInt()&511));atlasRange=Math.max(1,atlas.get("entries").getAsInt()>>>9);
            var visual=n.getAsJsonArray("visualSamples");samples=new float[visual.size()][16];for(int i=0;i<samples.length;i++){var sample=visual.get(i).getAsJsonObject();var base=sample.getAsJsonObject("base");var amplitude=sample.getAsJsonObject("amplitude");float[] c0=array(base.getAsJsonArray("color")),c1=array(amplitude.getAsJsonArray("color")),s0=array(base.getAsJsonArray("size")),s1=array(amplitude.getAsJsonArray("size"));System.arraycopy(c0,0,samples[i],0,4);System.arraycopy(c1,0,samples[i],4,4);samples[i][8]=s0[0];samples[i][9]=s0[1];samples[i][10]=s1[0];samples[i][11]=s1[1];samples[i][12]=base.get("rotationTotal").getAsFloat();samples[i][13]=amplitude.get("rotationTotal").getAsFloat();samples[i][14]=base.get("scale").getAsFloat();samples[i][15]=amplitude.get("scale").getAsFloat();}
            var velocities=n.getAsJsonArray("velocitySamples");velocity=new float[velocities.size()][];for(int i=0;i<velocity.length;i++)velocity[i]=array(velocities.get(i).getAsJsonArray());
            var visualSprites=n.getAsJsonArray("sprites");sprites=new Sprite[visualSprites.size()];for(int i=0;i<sprites.length;i++){var sprite=visualSprites.get(i).getAsJsonObject();sprites[i]=new Sprite(array(sprite.getAsJsonArray("rect")),sprite.get("additive").getAsBoolean());}
        }
    }
    private static float[] array(JsonArray a){var f=new float[a.size()];for(int i=0;i<f.length;i++)f[i]=a.get(i).getAsFloat();return f;}
    private static Range range(JsonObject n,String key){var row=n.getAsJsonObject(key);return new Range(row.get("base").getAsFloat(),row.get("amplitude").getAsFloat());}
    private static Range[] ranges(JsonArray a){var out=new Range[a.size()];for(int i=0;i<out.length;i++){var n=a.get(i).getAsJsonObject();out[i]=new Range(n.get("base").getAsFloat(),n.get("amplitude").getAsFloat());}return out;}
    private static void load(){
        var resources=Minecraft.getInstance().getResourceManager();if(owner==resources)return;
        try(var input=resources.getResource(new ResourceLocation("zombierool","sky/original_effects.json")).orElseThrow().open();var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var parsed=new HashMap<String,List<Element>>();for(var entry:root.getAsJsonObject("effects").entrySet()){var elements=new ArrayList<Element>();for(var raw:entry.getValue().getAsJsonObject().getAsJsonArray("elements"))elements.add(new Element(raw.getAsJsonObject()));parsed.put(entry.getKey(),List.copyOf(elements));}effects.clear();effects.putAll(parsed);earth=array(root.getAsJsonArray("earth"));aircraft=null;owner=resources;
        }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load original sky scenery",e);}
    }
    public static int sourceElements(String id){load();return effects.getOrDefault(id,List.of()).size();}
    private static AirRig aircraft(){
        load();if(aircraft==null)try{aircraft=AirRig.read(Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("zombierool","sky/air_battle.zrs.gz")).orElseThrow().open());}catch(IOException e){throw new IllegalStateException("Cannot load sky aircraft rig",e);}return aircraft;
    }
    public static int aircraftFrames(){return aircraft().frames;}
    public static void render(SkyObjectsRenderer mesh,String type,float seconds){load();switch(type){
        case "nuclear_cloud"->{emit(mesh,"cloud",seconds*350+35000,0,1/12500f,new Vector3f(),new Vector3f(),0);nuclearColumn(mesh,seconds);nuclearCrown(mesh,seconds);}
        case "earth"->earth(mesh);
        case "eclipse"->emit(mesh,"eclipse",seconds*1000+35000,1,1/1500f,new Vector3f(),new Vector3f(),0);
        case "meteor"->{float cycle=positive(seconds,18);if(cycle<12)emit(mesh,"meteor",cycle*1000,2,1/450f,new Vector3f(),new Vector3f(0,-1.8f,0),0);else if(cycle<14)emit(mesh,"meteorImpact",(cycle-12)*1000,2,1/1800f,new Vector3f(),new Vector3f(),0);}
        case "dogfight"->aircraft().render(mesh,seconds);
        default->{}
    }}
    private static float positive(float n,float mod){return (n%mod+mod)%mod;}
    private static float random(long seed,int lane){long x=seed+0x9e3779b97f4a7c15L*(lane+1);x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return ((x^(x>>>31))>>>40)/(float)(1<<24);}
    private static float sample(float[][] rows,float t,int channel){if(rows.length==0)return 0;float f=Math.max(0,Math.min(1,t))*(rows.length-1);int a=(int)f,b=Math.min(a+1,rows.length-1);return rows[a][channel]+(rows[b][channel]-rows[a][channel])*(f-a);}
    /** Original effect-local axes to the editable sky object, preserving the nuclear emitter's roll. */
    private static Vector3f map(Vector3f p,int mode,boolean position){return switch(mode){case 0->new Vector3f(p.z*.9702957f-p.y*.2419219f,p.x-(position?12500:0),p.y*.9702957f+p.z*.2419219f);case 1->new Vector3f(-p.y,p.z,p.x);default->new Vector3f(-p.y,p.z,-p.x);};}
    private static void emit(SkyObjectsRenderer mesh,String id,float now,int mode,float scale,Vector3f offset,Vector3f motion,int salt){
        var elements=effects.getOrDefault(id,List.of());for(int element=0;element<elements.size();element++){
            // Replaced below by the thick, rising column; avoid a second stretched streak beside it.
            if(mode==0&&element==17)continue;
            var e=elements.get(element);if(e.type>6||e.sprites.length==0||e.samples.length==0)continue;
            float longest=Math.max(1,e.life.base+Math.abs(e.life.amplitude)),maxDelay=e.delay.base+Math.abs(e.delay.amplitude);int interval=Math.max(1,e.interval);long latest=e.looping?(long)Math.floor(now/interval):Math.max(0,Math.min(256,e.spawnCount))-1;
            long earliest=e.looping?(long)Math.floor((now-longest-maxDelay)/interval)-1:0;earliest=Math.max(e.looping?0:0,Math.max(earliest,latest-255));
            for(long spawn=earliest;spawn<=latest;spawn++){
                long seed=(long)(id.hashCode()+salt)*0x100000001b3L+element*7919L+spawn*104729L;float age=now-(e.looping?spawn*interval:0)-e.delay.at(random(seed,0)),life=Math.max(1,e.life.at(random(seed,1)));if(age<0||age>=life)continue;float t=age/life;
                float colorRandom=random(seed,2),sizeRandom=random(seed,3),angleRandom=random(seed,4),alphaRandom=random(seed,5);float[] color=new float[4];for(int i=0;i<4;i++){float a=sample(e.samples,t,i),b=sample(e.samples,t,i+4);color[i]=(a+(b-a)*(i==3?alphaRandom:colorRandom))/255f;}
                if(color[3]<.002f)continue;float sx=Math.max(0,sample(e.samples,t,8)+sample(e.samples,t,10)*sizeRandom),sy=Math.max(0,sample(e.samples,t,9)+sample(e.samples,t,11)*sizeRandom);if(sx<.001f||sy<.001f)continue;
                var nativePosition=new Vector3f(e.origin[0].at(random(seed,6)),e.origin[1].at(random(seed,7)),e.origin[2].at(random(seed,8)));
                float radius=e.radius.at(random(seed,9)),height=e.height.at(random(seed,10)),azimuth=random(seed,11)*(float)Math.PI*2;int shape=e.flags&0x30;if(shape!=0){float z=shape==0x10?random(seed,12)*2-1:0;float ring=(float)Math.sqrt(Math.max(0,1-z*z));nativePosition.add(z*radius+height,(float)Math.cos(azimuth)*radius*ring,(float)Math.sin(azimuth)*radius*ring);}
                if(e.velocity.length>0){float u=random(seed,13),v=random(seed,14);for(int axis=0;axis<3;axis++)nativePosition.setComponent(axis,nativePosition.get(axis)+(sample(e.velocity,t,6+axis)+sample(e.velocity,t,9+axis)*u+sample(e.velocity,t,18+axis)+sample(e.velocity,t,21+axis)*v)*life);}
                var center=map(nativePosition,mode,true).mul(scale).add(offset).fma(-age/1000,motion);var angle=new Vector3f(e.angles[0].at(random(seed,15)),e.angles[1].at(random(seed,16)),e.angles[2].at(random(seed,17)));var basis=new Matrix3f().rotateZ(angle.y).rotateY(angle.x).rotateX(angle.z);var right=map(basis.transform(new Vector3f(0,1,0)),mode,false);var up=map(basis.transform(new Vector3f(0,0,1)),mode,false);
                float rotation=e.rotation.at(angleRandom)+(sample(e.samples,t,12)+sample(e.samples,t,13)*angleRandom)*life;
                Sprite sprite=e.sprites[Math.min(e.sprites.length-1,(int)(random(seed,18)*e.sprites.length))];int frame=switch(e.atlasBehavior&3){case 1->(int)(random(seed,19)*e.atlasCount);case 2->e.atlasIndex;case 3->e.atlasIndex+(int)(random(seed,19)*e.atlasRange);default->0;};frame+=(e.atlasBehavior&4)!=0?(int)(t*e.atlasCount):(int)(age*e.atlasFps/1000);frame=Math.floorMod(frame,e.atlasCount);float[] rect=sprite.rect.clone();rect[0]+=rect[2]*(frame%e.cols)/e.cols;rect[1]+=rect[3]*(frame/e.cols)/e.rows;rect[2]/=e.cols;rect[3]/=e.rows;
                // Inset each flipbook cell to prevent bleeding into neighbouring frames.
                float insetU=Math.min(rect[2]*.01f,1/4096f),insetV=Math.min(rect[3]*.01f,1/2048f);rect[0]+=insetU;rect[1]+=insetV;rect[2]-=2*insetU;rect[3]-=2*insetV;
                if((e.flags&0x200000)!=0){center.fma(e.pivot[0]*sx*scale,right).fma(e.pivot[1]*sy*scale,up);}
                // The source's rotated column sprites are edge-on in the sky object's basis.
                // Face the viewer so the original tall smoke layers remain visible at every bearing.
                boolean column=mode==0&&e.origin[0].base<15000;
                if(mode==0&&sprite.additive)color[3]*=.4f;
                // The native fire cards sit inside the rolling smoke, rather than forming
                // the silhouette themselves. Keep their detail without exposed flame spikes.
                if(mode==0&&(element==1||element==8)){color[3]*=.7f;sx*=.8f;sy*=.75f;}
                mesh.sprite(center,right,up,sx*scale,sy*scale,rotation,rect,color[0],color[1],color[2],Math.min(1,color[3]*(column?1.8f:1)),sprite.additive,column||(mode==0&&e.type==2)||e.type==0||e.type==3||e.type==4||e.type==5||e.type==6);
            }
        }
    }
    private static float[] aircraftSpriteRect(Element e,Sprite sprite,int frame){
        frame=Math.floorMod(frame,e.atlasCount);var rect=sprite.rect.clone();rect[0]+=rect[2]*(frame%e.cols)/e.cols;rect[1]+=rect[3]*(frame/e.cols)/e.rows;rect[2]/=e.cols;rect[3]/=e.rows;
        float u=Math.min(rect[2]*.01f,1/4096f),v=Math.min(rect[3]*.01f,1/2048f);rect[0]+=u;rect[1]+=v;rect[2]-=2*u;rect[3]-=2*v;return rect;
    }
    /** Fill the source's thin streak layers with overlapping rising billows, using its original smoke atlas. */
    private static void nuclearColumn(SkyObjectsRenderer mesh,float seconds){
        var smoke=effects.get("cloud").get(0);var sprite=smoke.sprites[0];
        for(int layer=0;layer<28;layer++)for(int ring=0;ring<3;ring++){
            int seed=layer*3+ring;float h=positive(layer/28f+seconds*.0045f,1),angle=ring*2.094395f+seconds*.014f+h*3;
            float shoulder=Math.max(0,(h-.7f)/.3f);
            float radius=.22f+.12f*h+.14f*shoulder*shoulder+.018f*(float)Math.sin(seconds*.07f+seed),spread=.08f+.045f*h+.06f*shoulder;
            var center=new Vector3f((float)Math.cos(angle)*spread+.035f*(float)Math.sin(h*6+seconds*.02f),-1.65f+h*2.8f,(float)Math.sin(angle)*spread);
            float sampleAge=.18f+.45f*h,shade=.34f+.18f*random(seed,1),alpha=.9f*Math.min(1,h*12)*Math.min(1,(1-h)*14),heat=Math.max(0,(h-.64f)/.36f);
            float r=sample(smoke.samples,sampleAge,0)/255*shade+heat*.16f,g=sample(smoke.samples,sampleAge,1)/255*shade+heat*.065f,b=sample(smoke.samples,sampleAge,2)/255*shade+heat*.012f;
            mesh.sprite(center,new Vector3f(1,0,0),new Vector3f(0,1,0),radius,radius,seed*1.7f+seconds*(ring%2==0?.007f:-.007f),aircraftSpriteRect(smoke,sprite,seed),r,g,b,alpha,false,true);
        }
    }

    /** Continuous toroidal convection: rising inner wall, outward roof, descending rim.
     * This is a volumetric approximation using the retained source smoke sprites, not
     * a claim to reproduce the original engine's material/lighting implementation.
     */
    private static void nuclearCrown(SkyObjectsRenderer mesh,float seconds){
        var smoke=effects.get("cloud").get(9);var sprite=smoke.sprites[0];
          for(int sector=0;sector<12;sector++)for(int band=0;band<4;band++){
            int seed=sector*4+band;
            float azimuth=sector*(float)(Math.PI*2/12)+.10f*(float)Math.sin(seed*2.3f)+seconds*.0015f;
            // Positive phase travels up the inner wall and rolls over the outer lip.
            float phase=band*(float)(Math.PI*2/4)-seconds*.045f+sector*.37f;
            float radial=.68f+.31f*(float)Math.cos(phase);
            float y=1.02f+.29f*(float)Math.sin(phase);
            var center=new Vector3f(radial*(float)Math.cos(azimuth),y,radial*(float)Math.sin(azimuth));
            float size=.34f+.10f*random(seed,21);
            // The inner underside receives amber light; the outer shell stays charcoal.
            float inner=(1-(float)Math.cos(phase))*.5f;
            float underside=(1-(float)Math.sin(phase))*.5f;
            float heat=(.3f+.7f*inner)*(float)Math.sqrt(underside);
            float shade=.25f+.10f*random(seed,22);
            float r=Math.min(1,shade+heat*.95f),g=shade*.94f+heat*.43f,b=shade*.85f+heat*.08f;
            mesh.sprite(center,new Vector3f(1,0,0),new Vector3f(0,1,0),size,size,
                seed*2.39996f-phase*.24f,aircraftSpriteRect(smoke,sprite,seed),r,g,b,.43f,false,true);
        }
        // Emission belongs inside the lower fold. It is depth-sorted with the smoke
        // and the column, so it cannot become a full-screen glow or show through walls.
        var fire=effects.get("cloud").get(1);var flame=fire.sprites[0];
        var glow=effects.get("cloud").get(7);var halo=glow.sprites[0];
        for(int i=0;i<18;i++){
            float a=i*(float)(Math.PI*2/18)+seconds*.003f;
            float pulse=.75f+.25f*(float)Math.sin(seconds*.17f+i*1.7f);
            float radius=.55f+.055f*(float)Math.sin(a*3+seconds*.022f);
            var center=new Vector3f(radius*(float)Math.cos(a),.83f+.09f*(float)Math.sin(a*2+seconds*.025f),radius*(float)Math.sin(a));
            // Crossfade original atlas cells; never jump from one fire frame to the next.
            float frame=20+positive(seconds*.6f+i*1.73f,9);int base=(int)frame;float blend=frame-base;
            for(int j=0;j<2;j++)mesh.sprite(center,new Vector3f(1,0,0),new Vector3f(0,1,0),.34f,.30f,
                a*.3f,aircraftSpriteRect(fire,flame,20+Math.floorMod(base-20+j,9)),1,.9f,.65f,
                pulse*.7f*(j==0?1-blend:blend),true,true);
            mesh.sprite(center,new Vector3f(1,0,0),new Vector3f(0,1,0),.42f,.36f,0,
                aircraftSpriteRect(glow,halo,0),1,.44f,.08f,.11f*pulse,true,true);
        }
    }

    private static void earth(SkyObjectsRenderer mesh){
        float[] shade={.9f,.65f,1,.6f,.9f,.7f};for(int face=0;face<6;face++){float[] rect={earth[0]+earth[2]*(face%3)/3,earth[1]+earth[3]*(face/3)/2,earth[2]/3,earth[3]/2};float u=rect[0],v=rect[1],w=rect[2],h=rect[3];var corners=new Vector3f[]{cube(face,-1,-1),cube(face,1,-1),cube(face,1,1),cube(face,-1,1)};mesh.textured(corners,new float[]{u,v+h,u+w,v+h,u+w,v,u,v},shade[face],shade[face],shade[face],1,false);}
    }
    private static Vector3f cube(int face,float u,float v){return switch(face){case 0->new Vector3f(1,v,u);case 1->new Vector3f(-1,v,-u);case 2->new Vector3f(u,1,v);case 3->new Vector3f(u,-1,-v);case 4->new Vector3f(u,v,1);default->new Vector3f(-u,v,-1);};}
    private record Vertex(float x,float y,float z,float nx,float ny,float nz,float u,float v,int[] bones,float[] weights){}
    private static final class Surface {final Vertex[] vertices;final int[] indices;final float[] rect,posed;Surface(Vertex[] vertices,int[] indices,float[] rect){this.vertices=vertices;this.indices=indices;this.rect=rect;posed=new float[vertices.length*6];}}
    private static final class AirRig {
        final int bones,frames,sockets;final float fps;final Surface[] surfaces;final float[] transforms,socketPositions,pose;final int[] socketTypes;
        AirRig(int bones,int frames,int sockets,float fps,Surface[] surfaces,float[] transforms,float[] socketPositions,int[] socketTypes){this.bones=bones;this.frames=frames;this.sockets=sockets;this.fps=fps;this.surfaces=surfaces;this.transforms=transforms;this.socketPositions=socketPositions;this.socketTypes=socketTypes;pose=new float[bones*12];}
        private static int bounded(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<1||n>max)throw new IOException("Invalid sky rig count");return n;}
        static AirRig read(InputStream raw)throws IOException{try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))){
            if(in.readInt()!=0x5a525332)throw new IOException("Invalid sky rig");int bones=bounded(in,128),count=bounded(in,16),frames=bounded(in,1000),sockets=bounded(in,128);float fps=in.readFloat();var surfaces=new Surface[count];
            for(int surface=0;surface<count;surface++){int nv=bounded(in,100000),ni=bounded(in,300000);if(ni%3!=0)throw new IOException("Invalid triangle stream");float[] rect={in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat()};var vertices=new Vertex[nv];for(int i=0;i<nv;i++){float x=in.readFloat(),y=in.readFloat(),z=in.readFloat(),nx=in.readFloat(),ny=in.readFloat(),nz=in.readFloat(),u=in.readFloat(),v=in.readFloat();int n=in.readUnsignedByte();if(n<1||n>4)throw new IOException("Invalid skin influences");int[] b=new int[n];float[] w=new float[n];for(int j=0;j<n;j++){b[j]=in.readUnsignedShort();w[j]=in.readFloat();if(b[j]>=bones)throw new IOException("Invalid sky bone");}vertices[i]=new Vertex(x,y,z,nx,ny,nz,u,v,b,w);}int[] indices=new int[ni];for(int i=0;i<ni;i++){indices[i]=in.readInt();if(indices[i]<0||indices[i]>=nv)throw new IOException("Invalid sky vertex");}surfaces[surface]=new Surface(vertices,indices,rect);}
            var transforms=new float[frames*bones*12];var positions=new float[frames*sockets*3];for(int frame=0;frame<frames;frame++){for(int i=0;i<bones*12;i++)transforms[frame*bones*12+i]=in.readFloat();for(int i=0;i<sockets*3;i++)positions[frame*sockets*3+i]=in.readFloat();}int[] types=new int[sockets];for(int i=0;i<sockets;i++)types[i]=in.readUnsignedByte();return new AirRig(bones,frames,sockets,fps,surfaces,transforms,positions,types);
        }}
        void render(SkyObjectsRenderer mesh,float seconds){
            float f=positive(seconds*fps,frames-1);int a=(int)f,b=Math.min(frames-1,a+1);float alpha=f-a;int size=bones*12;for(int i=0;i<size;i++)pose[i]=transforms[a*size+i]+(transforms[b*size+i]-transforms[a*size+i])*alpha;
            for(var surface:surfaces){for(int i=0;i<surface.vertices.length;i++){var v=surface.vertices[i];int offset=i*6;float x=0,y=0,z=0,nx=0,ny=0,nz=0;for(int j=0;j<v.bones.length;j++){int k=v.bones[j]*12;float w=v.weights[j];x+=(pose[k]*v.x+pose[k+1]*v.y+pose[k+2]*v.z+pose[k+3])*w;y+=(pose[k+4]*v.x+pose[k+5]*v.y+pose[k+6]*v.z+pose[k+7])*w;z+=(pose[k+8]*v.x+pose[k+9]*v.y+pose[k+10]*v.z+pose[k+11])*w;nx+=(pose[k]*v.nx+pose[k+1]*v.ny+pose[k+2]*v.nz)*w;ny+=(pose[k+4]*v.nx+pose[k+5]*v.ny+pose[k+6]*v.nz)*w;nz+=(pose[k+8]*v.nx+pose[k+9]*v.ny+pose[k+10]*v.nz)*w;}var p=surface.posed;p[offset]=x;p[offset+1]=y;p[offset+2]=z;p[offset+3]=nx;p[offset+4]=ny;p[offset+5]=nz;}
                var points=new Vector3f[]{new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};var uv=new float[8];for(int tri=0;tri<surface.indices.length;tri+=3){float light=0;for(int corner=0;corner<4;corner++){int index=surface.indices[tri+Math.min(corner,2)],offset=index*6;var p=surface.posed;var v=surface.vertices[index];points[corner].set(p[offset],p[offset+1],p[offset+2]);uv[corner*2]=surface.rect[0]+surface.rect[2]*Math.max(0,Math.min(1,v.u));uv[corner*2+1]=surface.rect[1]+surface.rect[3]*Math.max(0,Math.min(1,v.v));light+=.65f+.35f*Math.max(0,p[offset+4]);}light/=4;mesh.textured(points,uv,light,light,light,1,false);}
            }
            // Source-authored smoke and gun sockets follow the original bones, including trail history.
            for(int socket=0;socket<sockets;socket++){boolean smoke=socketTypes[socket]==0;int samples=smoke?12:5;if(!smoke&&((int)(seconds*3)+socket)%7>1)continue;for(int step=0;step<samples;step++){
                float history=positive(f-step*(smoke?3:1),frames-1);int frame=(int)history;int offset=(frame*sockets+socket)*3;var point=new Vector3f(socketPositions[offset],socketPositions[offset+1],socketPositions[offset+2]);var element=effects.get(smoke?"planeTrail":"tracers").get(0);float age=smoke?step/(float)samples:.3f;float side=Math.max(.002f,(sample(element.samples,age,8)+sample(element.samples,age,10)*.5f)/5000);float[] color=new float[4];for(int k=0;k<4;k++)color[k]=sample(element.samples,age,k)/255;var sprite=element.sprites[0];mesh.sprite(point,new Vector3f(1,0,0),new Vector3f(0,1,0),side,smoke?side:side*4,0,aircraftSpriteRect(element,sprite,smoke?socket*7+step:element.atlasIndex),color[0],color[1],color[2],color[3],sprite.additive,true);
            }}
        }
    }
}

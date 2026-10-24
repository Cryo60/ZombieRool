package me.cryo.zombierool.client.render;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Authored block-style scenery. Static surfaces are baked once, shared by every generator. */
final class AmbientSceneryModels {
    private static final ResourceLocation TEXTURE=new ResourceLocation("zombierool","textures/scenery/military_materials.png");
    private static final int OLIVE=0x626949, DARK=0x30372f, STEEL=0x535b5b, BLACK=0x222829, CANVAS=0x9a9872, GLASS=0x425f69, WOOD=0x70503a;
    private record Face(float[] xyz,float nx,float ny,float nz,int color,int tile) {}
    private static final class Model {
        final ResourceLocation texture;
        Model(){this(TEXTURE);}
        Model(ResourceLocation texture){this.texture=texture;}
        final List<Face> faces=new ArrayList<>();
        Model box(double x,double y,double z,double w,double h,double d,int color){
            double[][] v={{x,y,z},{x+w,y,z},{x+w,y+h,z},{x,y+h,z},{x,y,z+d},{x+w,y,z+d},{x+w,y+h,z+d},{x,y+h,z+d}};
            int[][] sides={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
            for(int[] f:sides)face(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color);
            return this;
        }
        void face(double[] a,double[] b,double[] c,double[] d,int color){
            double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,len=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(len<1e-8)return;
            faces.add(new Face(new float[]{(float)a[0],(float)a[1],(float)a[2],(float)b[0],(float)b[1],(float)b[2],(float)c[0],(float)c[1],(float)c[2],(float)d[0],(float)d[1],(float)d[2]},(float)(nx/len),(float)(ny/len),(float)(nz/len),color,material(color)));
        }
        // A thin rectangular beam between arbitrary endpoints: struts, braces, cables and railings.
        Model rod(double x,double y,double z,double xx,double yy,double zz,double width,int color){
            double dx=xx-x,dy=yy-y,dz=zz-z,len=Math.sqrt(dx*dx+dy*dy+dz*dz);if(len<1e-8)return this;
            double sx=-dz,sy=0,sz=dx,sl=Math.sqrt(sx*sx+sz*sz);if(sl<1e-8){sx=1;sz=0;sl=1;}
            sx*=width/sl/2;sz*=width/sl/2;double ax=dy*sz/len,ay=(dz*sx-dx*sz)/len,az=-dy*sx/len;
            double[][] v=new double[8][3];for(int i=0;i<8;i++){double q=i<4?0:1,s=(i%4==0||i%4==3)?-1:1,t=i%4<2?-1:1;v[i]=new double[]{x+dx*q+sx*s+ax*t,y+dy*q+sy*s+ay*t,z+dz*q+sz*s+az*t};}
            for(int[] f:new int[][]{{0,3,2,1},{4,5,6,7},{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7}})face(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color);return this;
        }
        void draw(PoseStack p,MultiBufferSource buffers,int light){draw(p,buffers,light,1);}
        void draw(PoseStack p,MultiBufferSource buffers,int light,float opacity){
            var out=buffers.getBuffer(opacity>=.999f?RenderType.entityCutout(texture):RenderType.entityTranslucent(texture));var matrix=p.last();
            for(Face f:faces){float r=((f.color>>16)&255)/255f,g=((f.color>>8)&255)/255f,b=(f.color&255)/255f;
                float u0=(f.tile%4)/4f+.00195f,u1=(f.tile%4+1)/4f-.00195f,v0=(f.tile/4)/2f+.00195f,v1=(f.tile/4+1)/2f-.00195f;
                for(int i=0;i<4;i++)out.vertex(matrix.pose(),f.xyz[i*3],f.xyz[i*3+1],f.xyz[i*3+2]).color(r,g,b,opacity).uv(i==1||i==2?u1:u0,i>=2?v1:v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(matrix.normal(),f.nx,f.ny,f.nz).endVertex();
            }
        }
    }
    private static int material(int color){
        if(color==BRONZE||color==IRON||color==RUNE||color==STEEL||color==0x575e50||color==0x555c4d)return 1;
        if(color==CANVAS||color==0x858866||color==0x686d50||color==0xd0c8a2)return 2;
        if(color==WOOD)return 3;
        if(color==GLASS)return 4;
        if(color==BLACK)return 5;
        if(color==0x666a57||color==0x92927c||color==0x818571)return 6;
        return 0;
    }
    private static Model NECROPOLIS,NECROPOLIS_GLOW;
    static void necropolis(PoseStack pose,MultiBufferSource buffers,int light){
        if(NECROPOLIS==null){var white=new ResourceLocation("zombierool","textures/scenery/tracer_white.png");NECROPOLIS=new Model(white);NECROPOLIS_GLOW=new Model(white);for(var tag:me.cryo.zombierool.gameplay.SkyObjects.necropolisGeometry()){var n=(net.minecraft.nbt.CompoundTag)tag;int color=((int)(n.getDouble("r")*255)<<16)|((int)(n.getDouble("g")*255)<<8)|(int)(n.getDouble("b")*255);var model=n.getDouble("emissive")>0?NECROPOLIS_GLOW:NECROPOLIS;double sx=n.getDouble("sx"),sy=n.getDouble("sy"),sz=n.getDouble("sz");model.box(n.getDouble("x")-sx/2,n.getDouble("y")-sy/2,n.getDouble("z")-sz/2,sx,sy,sz,color);}}
        NECROPOLIS.draw(pose,buffers,light);NECROPOLIS_GLOW.draw(pose,buffers,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
    }
    private static final Model BASE=dcaBase(),GUN=dcaGun(),PLANE=plane(),SHIP=airship(),SHIELD=dcaShield(),PROPELLER=propeller();
    private static final int BRONZE=0xc49a51, IRON=0x444c53, BLUE=0x345b86, RUNE=0x9ac8e4;
    private static final Model SIEGE_BASE=siegeBase(),SIEGE_CRADLE=siegeCradle(),SIEGE_GUN=siegeGun();
    private static final Model LIGHT_BASE=lightBase(),LIGHT_GUN=lightGun(),MODERN_BASE=modernBase(),MODERN_GUN=modernGun();
    static void dcaBase(PoseStack p,MultiBufferSource b,int light,String style){if(style.equals("dwarven"))SIEGE_BASE.draw(p,b,light);else if(style.equals("quad14"))VehicleDecorModels.draw("dca_quad_normal",p,b,light,1);else if(style.equals("light20"))LIGHT_BASE.draw(p,b,light);else if(style.equals("modern"))MODERN_BASE.draw(p,b,light);else BASE.draw(p,b,light);}
    static void dcaShield(PoseStack p,MultiBufferSource b,int light,String style){if(style.equals("dwarven"))SIEGE_CRADLE.draw(p,b,light);else if(style.equals("flak88"))SHIELD.draw(p,b,light);}
    static void dcaGun(PoseStack p,MultiBufferSource b,int light,String style){if(style.equals("dwarven"))SIEGE_GUN.draw(p,b,light);else if(style.equals("quad14")){p.pushPose();p.translate(0,-1.4,0);VehicleDecorModels.draw("dca_quad_normal",p,b,light,2);p.popPose();}else if(style.equals("light20"))LIGHT_GUN.draw(p,b,light);else if(style.equals("modern"))MODERN_GUN.draw(p,b,light);else GUN.draw(p,b,light);}
    /** Hollow faceted tube: the bore has inward-facing walls and an annular muzzle. */
    private static void siegeTube(Model m,double z0,double z1,double r0,double r1,double bore,int color){
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6,b=(i+1)*Math.PI/6,ax=Math.cos(a),ay=Math.sin(a),bx=Math.cos(b),by=Math.sin(b);
            m.face(new double[]{ax*r0,ay*r0,z0},new double[]{bx*r0,by*r0,z0},new double[]{bx*r1,by*r1,z1},new double[]{ax*r1,ay*r1,z1},color);
            m.face(new double[]{ax*r1,ay*r1,z1},new double[]{bx*r1,by*r1,z1},new double[]{bx*bore,by*bore,z1},new double[]{ax*bore,ay*bore,z1},BRONZE);
            m.face(new double[]{ax*bore,ay*bore,z1},new double[]{bx*bore,by*bore,z1},new double[]{bx*bore,by*bore,z0},new double[]{ax*bore,ay*bore,z0},IRON);
        }
    }
    private static void siegeWheel(Model m,int side){
        double x=side*1.12,y=.68,z=.12;
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6,b=(i+1)*Math.PI/6;
            for(double[] ring:new double[][]{{.69,.53,.26,WOOD},{.72,.66,.29,IRON}}){
                double outer=ring[0],inner=ring[1],half=ring[2]/2;int color=(int)ring[3];
                double[] u={x-half,y+Math.sin(a)*outer,z+Math.cos(a)*outer},v={x-half,y+Math.sin(b)*outer,z+Math.cos(b)*outer},w={x+half,v[1],v[2]},q={x+half,u[1],u[2]};
                m.face(q,w,v,u,color);
                for(int edge:new int[]{-1,1}){
                    double xx=x+edge*half;double[] p0={xx,y+Math.sin(a)*outer,z+Math.cos(a)*outer},p1={xx,y+Math.sin(b)*outer,z+Math.cos(b)*outer},p2={xx,y+Math.sin(b)*inner,z+Math.cos(b)*inner},p3={xx,y+Math.sin(a)*inner,z+Math.cos(a)*inner};
                    if(edge<0)m.face(p0,p1,p2,p3,color);else m.face(p3,p2,p1,p0,color);
                }
            }
            if(i%2==0)m.rod(x,y,z,x,y+Math.sin(a)*.59,z+Math.cos(a)*.59,.11,WOOD);
        }
        m.box(x-.19,y-.14,z-.14,.38,.28,.28,BRONZE).box(x+side*.21-.06,y-.06,z-.06,.12,.12,.12,STEEL);
    }
    private static Model siegeBase(){var m=new Model();
        // Broad timber carriage, iron straps, two spoked wheels and rear trail.
        m.box(-.83,.39,-1.02,1.66,.2,2.08,WOOD).box(-.88,.58,-.83,1.76,.16,1.68,WOOD);
        for(double x:new double[]{-.64,.4}){
            m.box(x,.66,-.96,.24,.19,1.96,IRON);
            for(double z:new double[]{-.8,-.2,.55})m.box(x+.075,.855,z,.09,.035,.09,BRONZE);
        }
        m.rod(-1.34,.68,.12,1.34,.68,.12,.17,IRON);
        for(int side:new int[]{-1,1}){
            siegeWheel(m,side);m.rod(side*.62,.53,-.67,side*.34,.12,-2.12,.22,WOOD);
            m.box(side*.34-.17,.04,-2.35,.34,.13,.46,IRON);
        }
        m.box(-.44,.79,-.44,.88,.16,.88,BRONZE).box(-.3,.95,-.3,.6,.16,.6,IRON);
        // A rear crate of cannonballs remains part of the shared static mesh.
        m.box(-.46,.75,-1.08,.92,.12,.44,WOOD);
        for(double x:new double[]{-.25,.05,.35})m.box(x-.1,.87,-.99,.2,.2,.2,IRON);
        return m;
    }
    private static Model siegeCradle(){var m=new Model();
        for(int side:new int[]{-1,1}){
            m.box(side<0?-.81:.59,-.53,-.48,.22,.77,.96,WOOD);
            m.box(side<0?-.83:.57,-.1,-.5,.26,.27,1,IRON);
            m.box(side<0?-.91:.83,-.36,-.36,.08,.58,.72,BLUE);
            for(double z:new double[]{-.38,.32})m.box(side<0?-.93:.91,-.4,z,.035,.64,.045,BRONZE);
            m.box(side<0?-.93:.91,-.4,-.38,.035,.05,.76,BRONZE);
            // Raised hammer crest on each blue side plate.
            m.box(side<0?-.95:.945,-.29,-.025,.022,.35,.07,BRONZE);
            m.box(side<0?-.95:.945,.01,-.18,.022,.13,.36,BRONZE);
            m.rod(side*.69,-.37,-.28,side*.69,-.08,.24,.065,BRONZE);
        }
        return m;
    }
    private static Model siegeGun(){var m=new Model();
        // Short, heavy bronze bombard; muzzle and recoil share the engine's gun axis.
        siegeTube(m,-.77,.4,.43,.48,.08,BRONZE);
        siegeTube(m,.4,2.6,.48,.35,.27,BRONZE);
        siegeTube(m,2.6,2.92,.35,.47,.29,BRONZE);
        for(double[] band:new double[][]{{-.77,.45},{.4,.5},{1.5,.44},{2.56,.4}})siegeTube(m,band[0],band[0]+.11,band[1]+.025,band[1]+.025,.28,IRON);
        m.box(-.31,-.31,-.84,.62,.62,.07,BRONZE).box(-.15,-.15,-1.02,.3,.3,.2,IRON);
        m.rod(-.72,0,0,.72,0,0,.2,IRON);
        // Top vent and angular engraved runes, repeated on both sides.
        m.box(-.055,.43,-.42,.11,.09,.11,IRON);
        for(int side:new int[]{-1,1})for(double z:new double[]{.7,1.02,1.34}){
            double x=side*(.48-(z-.4)*.059);
            m.rod(x,-.1,z,x,.12,z,.018,RUNE).rod(x,.12,z,x,.02,z+.1,.018,RUNE).rod(x,-.1,z,x,.0,z+.1,.018,RUNE);
        }
        return m;
    }
    private static Model lightBase(){var m=new Model();m.box(-.38,.08,-.38,.76,.22,.76,DARK).box(-.2,.3,-.2,.4,1,.4,OLIVE);for(int i=0;i<3;i++){double a=i*Math.PI*2/3;m.rod(0,.5,0,Math.sin(a)*1.1,.07,Math.cos(a)*1.1,.14,STEEL);}return m;}
    private static Model lightGun(){var m=new Model();m.box(-.48,-.3,-.4,.96,.65,.16,OLIVE).box(-.25,-.15,-.35,.5,.3,.9,STEEL).box(-.28,-.08,.5,.18,.18,1.5,BLACK).box(.1,-.08,.5,.18,.18,1.5,BLACK).box(-.65,-.05,-.05,.35,.3,.45,CANVAS).box(.3,-.05,-.05,.35,.3,.45,CANVAS);m.rod(0,.12,-.3,0,.38,.8,.035,STEEL);return m;}
    private static Model modernBase(){var m=new Model();m.box(-.8,.02,-.8,1.6,.26,1.6,STEEL).box(-.57,.28,-.57,1.14,.7,1.14,0xb8b6a7).box(-.45,.98,-.45,.9,.3,.9,DARK);for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})m.rod(x*.6,.3,z*.6,x*.9,.04,z*.9,.13,STEEL);return m;}
    private static Model modernGun(){var m=new Model();m.box(-.48,-.2,-.55,.96,.8,1.15,0xb8b6a7).box(-.38,.6,-.44,.76,.6,.7,0xd2d0bd).box(-.3,1.2,-.35,.6,.1,.52,DARK).box(-.18,.15,.6,.36,.36,.25,STEEL);for(int i=0;i<6;i++){double a=i*Math.PI/3;m.box(Math.sin(a)*.17-.055,.3+Math.cos(a)*.17-.055,.75,.11,.11,1.25,BLACK);}m.box(-.35,-.3,.8,.7,.12,.15,STEEL).box(.5,.05,-.2,.3,.5,.8,DARK);return m;}
    static void plane(PoseStack p,MultiBufferSource b,int light,double time,int index){
        PLANE.draw(p,b,light);p.pushPose();p.translate(0,.03,2.67);p.mulPose(Axis.ZP.rotationDegrees((float)(time*1700+index*71)));PROPELLER.draw(p,b,light);p.popPose();
    }
    static void airship(PoseStack p,MultiBufferSource b,int light,double time,float opacity){SHIP.draw(p,b,light,opacity);for(int side:new int[]{-1,1})for(double z:new double[]{-2.9,2.4}){p.pushPose();p.translate(side*2.75,-1.85,z+.78);p.mulPose(Axis.ZP.rotationDegrees((float)(time*1100)));p.scale(.48f,.48f,.48f);PROPELLER.draw(p,b,light,opacity);p.popPose();}}
    private static Model dcaBase(){Model m=new Model();
        m.box(-.47,.08,-.47,.94,.22,.94,DARK).box(-.34,.3,-.34,.68,.55,.68,OLIVE).box(-.42,.79,-.42,.84,.16,.84,STEEL);
        for(int side:new int[]{-1,1}){m.rod(0,.19,0,side*1.12,.13,0,.23,OLIVE).rod(0,.19,0,0,.13,side*1.12,.23,OLIVE);m.box(side*1.1-.16,0,-.23,.32,.16,.46,DARK).box(-.23,0,side*1.1-.16,.46,.16,.32,DARK);m.rod(side*.36,.33,0,side*.94,.18,0,.08,STEEL);m.box(side*.61-.075,.83,-.44,.15,.55,.86,OLIVE);}
        for(double z:new double[]{-.34,.34})for(double x:new double[]{-.32,.32})m.box(x-.045,.94,z-.045,.09,.04,.09,BLACK);
        m.box(-.75,.43,-.8,.42,.09,.42,WOOD).rod(-.54,.42,-.6,-.54,.83,-.4,.07,STEEL);
        return m;
    }
    private static Model dcaShield(){Model m=new Model();
        // Two-piece armoured shield, open around the elevating gun cradle.
        for(int side:new int[]{-1,1}){
            m.box(side<0?-1.05:.28,-.91,.52,.77,1.64,.09,OLIVE);
            m.box(side<0?-1.02:.28,.73,.52,.74,.27,.09,OLIVE);
            m.box(side<0?-.95:.86,-.7,.43,.09,1.45,.08,DARK);
            for(double y:new double[]{-.7,.05,.65})m.box(side*.87-.035,y,.615,.07,.07,.025,STEEL);
            // Trunnions, handwheels and their spokes.
            m.box(side*.55-.09,-.15,-.23,.18,.43,.52,STEEL);
            double x=side*.88;for(int i=0;i<12;i++){double a=i*Math.PI/6,c=(i+1)*Math.PI/6;m.rod(x,Math.cos(a)*.25-.15,Math.sin(a)*.25-.43,x,Math.cos(c)*.25-.15,Math.sin(c)*.25-.43,.045,BLACK);}
            for(int i=0;i<4;i++){double a=i*Math.PI/2;m.rod(x,-.15,-.43,x,Math.cos(a)*.25-.15,Math.sin(a)*.25-.43,.025,STEEL);}
        }
        m.box(-.28,-.91,.52,.56,.45,.09,OLIVE);
        m.box(-.88,-.84,-.96,.4,.09,.4,WOOD).rod(-.68,-.85,-.78,-.68,-.39,-.47,.09,STEEL);
        m.box(.68,-.75,-.8,.38,.09,.4,WOOD).rod(.85,-.75,-.6,.85,-.3,-.39,.08,STEEL);
        m.box(-.61,.29,-.55,.17,.17,.6,BLACK).box(-.63,.27,-.62,.21,.21,.08,STEEL);
        return m;
    }
    private static Model dcaGun(){Model m=new Model();
        // Flak 36: long single 88 mm tube, stepped jacket, recoil cylinder and rear breech.
        m.box(-.22,-.27,-1.12,.44,.5,1.66,STEEL).box(-.28,-.23,-1.35,.56,.4,.3,DARK);
        m.box(-.19,-.2,-1.52,.38,.33,.2,STEEL).box(-.25,-.34,-.88,.5,.08,.82,BLACK);
        m.box(-.17,-.17,.4,.34,.34,1.15,OLIVE).box(-.13,-.13,1.55,.26,.26,1.15,STEEL).box(-.103,-.103,2.7,.206,.206,1.43,STEEL);
        for(double z:new double[]{.44,1.49,2.65})m.box(-.19,-.19,z,.38,.38,.065,DARK);
        // The square rim surrounds an inset dark bore; no oversized muzzle brake.
        m.box(-.12,-.12,4.1,.24,.24,.1,STEEL).box(-.082,-.082,4.202,.164,.164,.004,BLACK);
        m.box(-.115,.21,-.55,.23,.23,2.25,OLIVE).box(-.07,.26,1.7,.14,.14,.38,STEEL);
        m.box(-.29,-.38,-.4,.58,.11,1.4,DARK);
        for(double z:new double[]{-.4,.35,1.0})m.box(-.2,.15,z,.4,.11,.09,DARK);
        m.rod(.28,-.08,-1.15,.46,-.08,-1.15,.065,STEEL).rod(.46,-.08,-1.15,.46,.15,-1.15,.065,WOOD);
        // Breech block, loading tray and fasteners remain visible from the rear.
        m.box(-.15,-.12,-1.57,.3,.21,.03,BLACK).box(-.2,-.28,-1.95,.4,.045,.55,STEEL);
        for(double x:new double[]{-.225,.18})for(double z:new double[]{-.9,-.55,-.2})m.box(x,-.09,z,.045,.06,.06,BLACK);
        return m;
    }
    private static Model plane(){Model m=new Model();
        // Stepped nose and tail keep the silhouette readable without smooth, high-poly surfaces.
        for(int i=0;i<9;i++){double z=-2.5+i*.56,w=.22+Math.sin((i+1)*Math.PI/11)*.6,h=.25+Math.sin((i+1)*Math.PI/11)*.4;m.box(-w/2,-h/2,z,w,h,.58,i>6?STEEL:OLIVE);}
        m.box(-.34,-.25,2.2,.68,.54,.4,BLACK).box(-.24,-.18,2.59,.48,.4,.15,STEEL);
        for(int side:new int[]{-1,1})for(int j=0;j<4;j++)m.box(side*.35-.045,-.14+j*.1,2.23,.09,.04,.25,STEEL);
        // Open cockpit: dark well, padded rim, seat and a small framed windscreen.
        m.box(-.25,.29,-.15,.5,.035,.66,BLACK).box(-.3,.27,-.21,.06,.12,.8,WOOD).box(.24,.27,-.21,.06,.12,.8,WOOD).box(-.24,.28,-.22,.48,.17,.09,WOOD).box(-.24,.3,.49,.48,.24,.045,GLASS);
        for(double y:new double[]{-.27,.92}){
            for(int side:new int[]{-1,1})for(int segment=0;segment<6;segment++){double x=side*(.38+segment*.48),chord=segment==5?.83:1.22;m.box(Math.min(x,x+side*.49),y,-.21+(1.22-chord)*.5,.5,.12,chord,segment%2==0?CANVAS:0x858866);}
            for(int side:new int[]{-1,1})for(int rib=0;rib<9;rib++)m.box(side*(.5+rib*.31)-.018,y+.121,-.18,.036,.016,1.14,0x686d50);
        }
        for(int side:new int[]{-1,1}){
            for(double x:new double[]{side*1.35,side*2.65}){m.rod(x,-.15,-.12,x,.93,.02,.065,WOOD).rod(x,-.15,.85,x,.93,.84,.065,WOOD);m.rod(x,-.13,-.1,x,.93,.84,.018,STEEL);}
            m.rod(side*.25,.26,.05,side*.65,.94,.05,.065,STEEL).rod(side*.25,.2,.8,side*.65,.94,.8,.065,STEEL);
            m.box(side*1.04-.55,.03,-2.4,1.1,.09,.65,OLIVE).rod(side*.25,-.2,.95,side*.65,-.8,1.1,.085,STEEL).rod(side*.25,-.2,1.8,side*.65,-.8,1.1,.085,STEEL);
            m.box(side*.68-.1,-1.03,.83,.2,.5,.5,BLACK).box(side*.68-.115,-.9,.97,.23,.23,.23,STEEL);
            // Small contrasting wing insignia and exhaust manifold.
            m.box(side*2.4-.22,1.045,.2,.44,.008,.44,0xd0c8a2).box(side*2.4-.09,1.056,.33,.18,.008,.18,0x7b3831);
            for(int j=0;j<4;j++)m.box(side*.38-.04,.05,1.3+j*.18,.08,.13,.09,0x493f34);
        }
        m.rod(-.7,-.79,1.08,.7,-.79,1.08,.09,STEEL).rod(0,-.1,-2.4,0,-.52,-2.7,.07,STEEL);
        m.box(-.055,.05,-2.5,.11,.9,.55,OLIVE).box(-.055,.95,-2.37,.11,.17,.35,CANVAS);
        // Paired machine guns along the cowling.
        for(double x:new double[]{-.18,.18})m.box(x-.045,.29,.8,.09,.09,1.45,BLACK);
        return m;
    }
    private static Model propeller(){return new Model().box(-1.18,-.08,-.035,2.36,.16,.07,WOOD).box(-1.18,-.08,-.043,.22,.16,.085,CANVAS).box(.96,-.08,-.043,.22,.16,.085,CANVAS).box(-.14,-.14,-.12,.28,.28,.25,STEEL);}
    private static Model airship(){Model m=new Model();int rings=20,sides=12;double length=21;
        for(int ring=0;ring<rings;ring++)for(int side=0;side<sides;side++){
            double z0=-length/2+length*ring/rings,z1=-length/2+length*(ring+1)/rings,r0=3.1*Math.sqrt(Math.max(.001,1-z0*z0/(length*length/4))),r1=3.1*Math.sqrt(Math.max(.001,1-z1*z1/(length*length/4))),a=side*Math.PI*2/sides,c=(side+1)*Math.PI*2/sides;
            double[] v0={Math.cos(a)*r0,Math.sin(a)*r0,z0},v1={Math.cos(c)*r0,Math.sin(c)*r0,z0},v2={Math.cos(c)*r1,Math.sin(c)*r1,z1},v3={Math.cos(a)*r1,Math.sin(a)*r1,z1};m.face(v0,v1,v2,v3,(ring%4==0)?0x666a57:side%3==0?0x92927c:0x818571);
            // Longitudinal structural strips and circumferential reinforcing bands.
            m.rod(v0[0],v0[1],z0,v3[0],v3[1],z1,.035,0x575e50);
            if(ring%4==0)m.rod(v0[0],v0[1],z0,v1[0],v1[1],z0,.045,0x555c4d);
        }
        for(int side:new int[]{-1,1}){
            m.box(side<0?-4.9:0,-.065,-9.1,4.9,.13,2.6,OLIVE).box(-.065,side<0?-4.9:0,-9.1,.13,4.9,2.6,OLIVE);
            m.rod(side*.6,-2.55,-2.8,side*.6,-3.8,-2,.09,STEEL).rod(side*.6,-2.55,2.2,side*.6,-3.8,1.5,.09,STEEL);
            for(double z:new double[]{-2.9,2.4}){m.rod(side*1.6,-2.1,z,side*2.75,-1.85,z,.14,STEEL);m.box(side*2.75-.35,-2.16,z-.62,.7,.62,1.35,DARK);m.box(side*2.75-.26,-2.1,z+.67,.52,.5,.12,STEEL);for(int j=0;j<4;j++)m.box(side*2.75-.36,-2.1+j*.12,z-.4,.72,.035,.65,STEEL);}
        }
        m.box(-.7,-4.05,-2.5,1.4,.85,5.6,DARK).box(-.78,-3.35,-2.65,1.56,.18,5.9,OLIVE).box(-.52,-3.9,3.1,1.04,.5,.35,GLASS);
        for(int side:new int[]{-1,1})for(int j=0;j<7;j++)m.box(side*.705-.025,-3.7,-2.2+j*.68,.05,.32,.4,j<2?BLACK:GLASS);
        for(int side:new int[]{-1,1})m.rod(side*.67,-3.1,-2.2,side*.67,-3.1,2.9,.045,STEEL);
        m.rod(0,-4,-2,0,-4.7,-2.4,.06,STEEL).rod(0,2.95,0,0,4.1,0,.05,STEEL);
        return m;
    }
    private AmbientSceneryModels(){}
}

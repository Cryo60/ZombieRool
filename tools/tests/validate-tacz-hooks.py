"""Check client hook targets in the installed TacZ bytecode, without launching a client."""
from pathlib import Path
import subprocess,re

ROOT=Path(__file__).resolve().parents[2]
JAVAP=Path('C:/Java/jdk-17.0.20.1+1/bin/javap.exe')
TACZ=ROOT/'dev-libs/tacz-1.20.1-1.1.8-hotfix2.jar'
checks=0
def inspect(clazz):
    text=subprocess.check_output([str(JAVAP),'-classpath',str(TACZ),'-p','-c','-s',clazz],text=True)
    parts=re.split(r'(?m)^  ((?:public|private|protected).*\);)\n',text)
    return {parts[i]:parts[i+1] for i in range(1,len(parts)-1,2)}
def method(methods,name):
    found=[(header,body) for header,body in methods.items() if ' '+name+'(' in header]
    assert len(found)==1,(name,[h for h in methods])
    return found[0]
def check(condition,message):
    global checks
    assert condition,message
    checks+=1

gun=inspect('com.tacz.guns.client.renderer.item.GunItemRendererWrapper')
first=method(gun,'lambda$renderFirstPerson$5')[1]
third=method(gun,'lambda$renderByItem$6')[1]
for body,symbol,label in [(first,'m_110452_','first-person cutout'),(first,'m_110473_','first-person translucent'),(third,'m_110452_','third-person cutout')]:
    check(body.count('RenderType.'+symbol+':')==1,'Camo redirect matches exactly one '+label+' call')
first_descriptor='(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/LocalPlayer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;ILcom/tacz/guns/client/resource/GunDisplayInstance;)V'
third_descriptor='(Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/client/resource/GunDisplayInstance;)V'
check(first_descriptor in first,'First-person redirect captures the real lambda arguments')
check(third_descriptor in third,'Third-person redirect captures the real lambda arguments')
check('static' in method(gun,'lambda$renderByItem$6')[0],'Third-person redirect is static like its target')
ocular=method(inspect('com.tacz.guns.client.model.BedrockAttachmentModel'),'renderOcularAndDivision')[1]
check('stencilFunc' in ocular and 'stencilOp' in ocular and 'ixor' in ocular and 'iand' in ocular,'Scope mask uses inverted TacZ ocular stencil values')
runner=inspect('com.tacz.guns.api.client.animation.ObjectAnimationRunner')
check('lastUpdateNs' in method(runner,'update')[1],'Animation pause intercepts TacZ nano-time accumulator')
check('updateProgress' in method(runner,'updateSoundOnly')[1],'Sound-only animations use the same paused progress hook')
print(f'TACZ CLIENT HOOK PASS: {checks} bytecode checks (not a visual client test)')

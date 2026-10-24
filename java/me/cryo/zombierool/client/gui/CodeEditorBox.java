package me.cryo.zombierool.client.gui;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.regex.Pattern;

/** Shared code surface: accurate mouse selection, syntax colors, cursor line and line numbers. */
public class CodeEditorBox extends MultiLineEditBox {
    private final Font codeFont;private boolean dragging;private int blink;
    private static final Pattern TOKENS=Pattern.compile("(--.*|\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'(?:\\\\.|[^'\\\\])*'|\\b(?:function|local|end|if|then|else|elseif|for|while|do|return|and|or|not|nil|true|false|in|repeat|until)\\b|\\b[0-9]+(?:\\.[0-9]+)?\\b)");
    public CodeEditorBox(Font font,int x,int y,int w,int h,Component placeholder,Component title){super(font,x,y,w,h,placeholder,title);codeFont=font;setCharacterLimit(1000000);}
    private MultilineTextField text(){return net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(MultiLineEditBox.class,this,"f_238540_");}
    @Override public void tick(){super.tick();blink++;}
    @Override protected void renderDecorations(GuiGraphics g){}
    @Override protected void renderContents(GuiGraphics g,int mouseX,int mouseY,float partial){
        var f=text();String value=getValue();int y=getY()+innerPadding(),cursor=f.cursor();
        var selection=FieldAccess.selected(f);
        for(var view:FieldAccess.lines(f)){
            if(withinContentAreaTopBottom(y,y+9)){
                int x=getX()+innerPadding();boolean current=cursor>=view.beginIndex()&&cursor<=view.endIndex();
                if(current)g.fill(getX()+1,y-1,getX()+getWidth()-1,y+9,0xFF263746);
                if(f.hasSelection()&&selection.beginIndex()<=view.endIndex()&&selection.endIndex()>=view.beginIndex()){
                    int a=Math.max(selection.beginIndex(),view.beginIndex()),b=Math.min(selection.endIndex(),view.endIndex());
                    g.fill(x+codeFont.width(value.substring(view.beginIndex(),a)),y-1,x+codeFont.width(value.substring(view.beginIndex(),b)),y+9,0xFF3E5976);
                }
                String line=value.substring(view.beginIndex(),view.endIndex());var matcher=TOKENS.matcher(line);int start=0,drawX=x;
                while(matcher.find()){
                    String prefix=line.substring(start,matcher.start());g.drawString(codeFont,prefix,drawX,y,0xDFE8F0,false);drawX+=codeFont.width(prefix);
                    String token=matcher.group();int color=token.startsWith("--")?0x7FA886:token.startsWith("\"")||token.startsWith("'")?0xE6BC86:Character.isDigit(token.charAt(0))?0xA8D7BB:0xC49DE8;
                    g.drawString(codeFont,token,drawX,y,color,false);drawX+=codeFont.width(token);start=matcher.end();
                }
                g.drawString(codeFont,line.substring(start),drawX,y,0xDFE8F0,false);
                if(current&&isFocused()&&blink/6%2==0){int cx=x+codeFont.width(value.substring(view.beginIndex(),cursor));g.fill(cx,y-1,cx+1,y+9,0xFFE7EEF5);}
            }y+=9;
        }
    }
    public void renderLineNumbers(GuiGraphics g){int y=getY()+innerPadding()-(int)scrollAmount(),line=1;String value=getValue();for(var view:FieldAccess.lines(text())){if(y>=getY()&&y+9<=getY()+getHeight())g.drawString(codeFont,view.beginIndex()==0||value.charAt(view.beginIndex()-1)=='\n'?Integer.toString(line):"Â·",getX()-24,y,0x8498AA,false);for(int i=view.beginIndex();i<view.endIndex();i++)if(value.charAt(i)=='\n')line++;if(view.endIndex()<value.length()&&value.charAt(view.endIndex())=='\n')line++;y+=9;}}
    private record Span(int beginIndex,int endIndex){}
    private static final class FieldAccess extends MultilineTextField {
        private FieldAccess(Font font){super(font,1);}
        static java.util.List<Span> lines(MultilineTextField field){var result=new java.util.ArrayList<Span>();for(StringView view:field.iterateLines())result.add(new Span(view.beginIndex(),view.endIndex()));return result;}
        static Span selected(MultilineTextField field){StringView view=field.getSelected();return new Span(view.beginIndex(),view.endIndex());}
    }
    private void point(double x,double y){text().seekCursorToPoint(x-getX()-innerPadding(),y-getY()-innerPadding()+scrollAmount());}
    @Override public boolean mouseClicked(double x,double y,int button){if(visible&&active&&button==0&&withinContentAreaPoint(x,y)){text().setSelecting(Screen.hasShiftDown());point(x,y);text().setSelecting(false);dragging=true;return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&dragging){if(y<getY())setScrollAmount(scrollAmount()-9);else if(y>getY()+getHeight())setScrollAmount(scrollAmount()+9);text().setSelecting(true);point(x,Math.max(getY()+innerPadding(),Math.min(getY()+getHeight()-innerPadding(),y)));text().setSelecting(false);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
}

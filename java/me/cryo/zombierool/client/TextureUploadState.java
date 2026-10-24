package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Keep dynamic image uploads off the samplers owned by the world and shader packs. */
public final class TextureUploadState implements AutoCloseable {
    private final int unit,binding;
    public TextureUploadState(){unit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);RenderSystem.activeTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}
    @Override public void close(){RenderSystem.activeTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);RenderSystem.bindTexture(binding);RenderSystem.activeTexture(unit);GL13.glActiveTexture(unit);}
}

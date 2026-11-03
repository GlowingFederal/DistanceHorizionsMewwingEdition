package com.seibel.distanthorizons.forge17.mixin;

import java.nio.ByteBuffer;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.seibel.distanthorizons.forge17.MixinFlags;

@Mixin(Framebuffer.class)
public class MixinFramebuffer
{
    @Shadow public int framebufferTextureWidth;
    @Shadow public int framebufferTextureHeight;
    @Shadow public int framebufferObject;
    @Shadow public int depthBuffer;
    @Shadow public boolean useDepth;

    @Unique private boolean dh$depthIsTexture;

    @Inject(method = "createFramebuffer", at = @At("TAIL"))
    private void dh$replaceCompletedDepthAttachment(int width, int height, CallbackInfo ci)
    {
        if (!MixinFlags.framebufferMixinEnabled || !this.useDepth || !OpenGlHelper.isFramebufferEnabled())
        {
            return;
        }

        // Let vanilla finish its renderbuffer storage and attachment calls first.
        // Replacing only the initial bind lets those later calls overwrite the
        // texture attachment, leaving the fade shader sampling stale depth.
        int renderbuffer = this.depthBuffer;
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture = TextureUtil.glGenTextures();
        boolean stencil = MinecraftForgeClient.getStencilBits() > 0;
        try
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0,
                stencil ? GL30.GL_DEPTH24_STENCIL8 : GL14.GL_DEPTH_COMPONENT24,
                this.framebufferTextureWidth, this.framebufferTextureHeight, 0,
                stencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT,
                stencil ? GL30.GL_UNSIGNED_INT_24_8 : GL11.GL_FLOAT, (ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferObject);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, texture, 0);
            if (stencil)
            {
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, texture, 0);
            }
            OpenGlHelper.func_153184_g(renderbuffer);
            this.depthBuffer = texture;
            this.dh$depthIsTexture = true;
        }
        finally
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    @Redirect(method = "deleteFramebuffer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/OpenGlHelper;func_153184_g(I)V"))
    private void dh$deleteOwnedDepthAttachment(int depth)
    {
        // Ownership belongs to this framebuffer, not the global activation flag:
        // the splash may have created a renderbuffer before conversion was enabled.
        if (this.dh$depthIsTexture)
        {
            GL11.glDeleteTextures(depth);
            this.dh$depthIsTexture = false;
        }
        else
        {
            OpenGlHelper.func_153184_g(depth);
        }
    }
}

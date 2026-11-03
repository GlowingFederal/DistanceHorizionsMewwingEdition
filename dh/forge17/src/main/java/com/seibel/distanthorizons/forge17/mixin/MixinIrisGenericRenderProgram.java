package com.seibel.distanthorizons.forge17.mixin;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.coderbot.iris.compat.dh.IrisGenericRenderProgram", remap = false)
public abstract class MixinIrisGenericRenderProgram
{
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
        target = "Lcom/gtnewhorizons/angelica/glsm/GLStateManager;glVertexAttribPointer(IIIZIJ)V"), require = 0)
    private void dh$deferUnboundVertexPointer(int index, int size, int type,
        boolean normalized, int stride, long pointer)
    {
        // Angelica configures a placeholder pointer before binding a VBO here.
        // LWJGL 2 rejects offset pointers with no array buffer. bindVertexBuffer
        // installs the real pointer and stride before any generic geometry draws.
        if (GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != 0)
        {
            GLStateManager.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
        }
    }
}

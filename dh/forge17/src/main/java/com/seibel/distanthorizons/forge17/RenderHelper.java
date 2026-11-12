package com.seibel.distanthorizons.forge17;

import java.nio.FloatBuffer;

import com.seibel.distanthorizons.common.backports.Camera;
import com.seibel.distanthorizons.common.commonMixins.MixinVanillaFogCommon;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IAngelicaAccessor;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.lwjgl.GL32;
import net.minecraft.client.Minecraft;

import org.joml.Matrix4f;

import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.util.math.DhMat4f;

import static com.seibel.distanthorizons.lwjgl.LWJGLServiceProvider.LWJGL;

public class RenderHelper 
{
	// Matrix capture can load RenderHelper before Forge registers optional accessors.
	private static final class DelayedAccessors
	{
		private static final IAngelicaAccessor ANGELICA = ModAccessorInjector.INSTANCE.get(IAngelicaAccessor.class);
	}
	
	private static DhMat4f modelViewMatrix;
	private static DhMat4f projectionMatrix;
	public static boolean usesAngelica() { return DelayedAccessors.ANGELICA != null; }
	public static void updateVanillaSectionVisibility()
	{ if (DelayedAccessors.ANGELICA != null) DelayedAccessors.ANGELICA.updateVanillaSectionVisibility(); }
	
	
	
	//=================//
	// matrix handling //
	//=================//
	//region
	
	public static DhMat4f getModelViewMatrix() { return new DhMat4f(modelViewMatrix); }
	public static DhMat4f getProjectionMatrix() { return new DhMat4f(projectionMatrix); }
	
	public static void setModelViewMatrixFromBuffer(FloatBuffer modelviewBuffer)
	{
		modelViewMatrix = McObjectConverter.convert(new Matrix4f(modelviewBuffer));
		Camera.INSTANCE.captureModelView(modelViewMatrix);
	}
	public static void setProjectionMatrixFromBuffer(FloatBuffer projectionBuffer)
	{ projectionMatrix = McObjectConverter.convert(new Matrix4f(projectionBuffer)); }
	
	//endregion
	
	
	
	//===========//
	// rendering //
	//===========//
	//region
	
	public static void renderLods() 
    {
		updateVanillaSectionVisibility();
        ClientApi.RENDER_STATE.mcModelViewMatrix = getModelViewMatrix();
        ClientApi.RENDER_STATE.mcProjectionMatrix = getProjectionMatrix();
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapper(Minecraft.getMinecraft().theWorld);
	    ClientApi.RENDER_STATE.partialTickTime = MinecraftRenderWrapper.INSTANCE.getPartialTickTime();
		
	    // only crash during development
	    if (ModInfo.IS_DEV_BUILD)
	    {
		    ClientApi.RENDER_STATE.canRenderOrThrow();
	    }
	    
	    
		
		// set GL state for DH rendering
	    // TODO could we use DH's GLState object? or would that be overkill?
	    if (DelayedAccessors.ANGELICA == null)
		{
			LWJGL.glDisable(GL32.GL_ALPHA_TEST);
        }
       
        int oldActiveTex = LWJGL.glGetInteger(GL32.GL_ACTIVE_TEXTURE);
        int oldBoundTex = LWJGL.glGetInteger(GL32.GL_TEXTURE_BINDING_2D);
       
		
		
        ClientApi.INSTANCE.renderLods();
        
		
		
		// restore the GL State
	    LWJGL.glDepthFunc(GL32.GL_LEQUAL);
        if (DelayedAccessors.ANGELICA == null)
		{
			LWJGL.glEnable(GL32.GL_ALPHA_TEST);
        }
	    LWJGL.glDisable(GL32.GL_BLEND);
	    
	    LWJGL.glActiveTexture(oldActiveTex);
	    LWJGL.glBindTexture(GL32.GL_TEXTURE_2D, oldBoundTex);
    }
	
	// TODO why do we need to disable depth here?
    public static void beforeWater() { LWJGL.glDepthMask(true); }
	
    public static void renderFade(boolean translucent) 
    {
        if (DelayedAccessors.ANGELICA != null
            && !DelayedAccessors.ANGELICA.canDoFadeShader())
		{
			return;
        }
        ClientApi.RENDER_STATE.mcModelViewMatrix = getModelViewMatrix();
        ClientApi.RENDER_STATE.mcProjectionMatrix = getProjectionMatrix();
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapper(Minecraft.getMinecraft().theWorld);
	    
	    if (DelayedAccessors.ANGELICA == null)
	    {
		    LWJGL.glDisable(GL32.GL_ALPHA_TEST);
	    }
		
	    if (translucent)
	    {
		    ClientApi.INSTANCE.renderFadeTransparent();
	    }
	    else
	    {
		    ClientApi.INSTANCE.renderFadeOpaque();
	    }
	    if (DelayedAccessors.ANGELICA == null)
	    {
		    LWJGL.glEnable(GL32.GL_ALPHA_TEST);
	    }
	    
	    LWJGL.glDepthFunc(GL32.GL_LEQUAL);
	    LWJGL.glDisable(GL32.GL_BLEND);
    }
	
    public static void renderDeferredLods() 
    {
        if (DelayedAccessors.ANGELICA == null)
		{
            return;
        }
        ClientApi.RENDER_STATE.mcModelViewMatrix = getModelViewMatrix();
        ClientApi.RENDER_STATE.mcProjectionMatrix = getProjectionMatrix();
        ClientApi.RENDER_STATE.clientLevelWrapper = ClientLevelWrapper.getWrapper(Minecraft.getMinecraft().theWorld);

        ClientApi.INSTANCE.renderDeferredLodsForShaders();
    }
	
	//endregion
	
	
	
	
	
	/** 
	 * Unbinding is necessary to prevent 
	 * a crash if DH is enabled while MC starts loading into the world 
	 */
    public static void UnbindAfterTesselatorDraw() { LWJGL.glBindBuffer(GL32.GL_ARRAY_BUFFER, 0); }

	
	
	//=====//
	// fog //
	//=====//
	//region
	
    public static void enableFog() { LWJGL.glEnable(GL32.GL_FOG); }

    public static void disableFogDuringSetup() 
    {
        if (!MixinVanillaFogCommon.cancelFog()) 
		{
            return;
        }
	    LWJGL.glDisable(GL32.GL_FOG);
		
        // Extremely high values cause issues, but 15 mebi-meters out should be 
	    // practically infinite For Angelica
	    LWJGL.glFogf(GL32.GL_FOG_START, 1024 * 1024 * 15);
	    LWJGL.glFogf(GL32.GL_FOG_END, 1024 * 1024 * 16);
    }
	
	public static void disableFogDuringRender(int cap)
	{
		// Cancel enabling fog if needed
		if (MixinVanillaFogCommon.cancelFog()
			&& cap == GL32.GL_FOG)
		{
			return;
		}
		
		LWJGL.glEnable(cap);
	}
	
	//endregion
	
	
	
	
	
}

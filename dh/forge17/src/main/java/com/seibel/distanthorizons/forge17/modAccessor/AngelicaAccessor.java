package com.seibel.distanthorizons.forge17.modAccessor;

import java.awt.Color;

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IAngelicaAccessor;
import com.seibel.distanthorizons.forge17.modAccessor.exceptions.AngelicaVersionGuiException;
import cpw.mods.fml.common.versioning.VersionParser;
import cpw.mods.fml.common.versioning.VersionRange;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;

import java.nio.FloatBuffer;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import com.seibel.distanthorizons.core.render.VanillaSectionCoverage;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.util.iterator.ByteIterator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Iterator;

public class AngelicaAccessor implements IAngelicaAccessor 
{
	private static final MethodHandle GEOMETRY_ITERATOR = findGeometryIterator();

	private static MethodHandle findGeometryIterator()
	{
		try
		{
			MethodHandle iterator;
			try { iterator = MethodHandles.publicLookup().findVirtual(ChunkRenderList.class, "sectionsWithGeometryIterator", MethodType.methodType(ByteIterator.class)); }
			catch (NoSuchMethodException olderAngelica)
			{
				iterator = MethodHandles.publicLookup().findVirtual(ChunkRenderList.class, "sectionsWithGeometryIterator", MethodType.methodType(ByteIterator.class, boolean.class));
				iterator = MethodHandles.insertArguments(iterator, 1, false);
			}
			return iterator.asType(MethodType.methodType(ByteIterator.class, Object.class));
		}
		catch (ReflectiveOperationException ex) { throw new IllegalStateException("Unsupported Angelica render-list API", ex); }
	}

	private static ByteIterator geometryIterator(ChunkRenderList list)
	{
		try { return (ByteIterator) GEOMETRY_ITERATOR.invokeExact((Object)list); }
		catch (Throwable ex) { throw new IllegalStateException("Unable to read Angelica's current draw list", ex); }
	}

	@Override
	public void updateVanillaSectionVisibility()
	{
		VanillaSectionCoverage coverage = VanillaSectionCoverage.INSTANCE;
		CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstance();
		coverage.beginVisibility();
		if (renderer != null && renderer.getRenderSectionManager() != null)
		{
			Iterator<ChunkRenderList> lists = renderer.getRenderSectionManager().getRenderLists().iterator(false);
			while (lists.hasNext())
			{
				ChunkRenderList list = lists.next();
				ByteIterator sections = geometryIterator(list);
				if (sections == null) continue;
				while (sections.hasNext())
				{
					RenderSection section = list.getRegion().getSection(sections.nextByteAsInt());
					if (section != null && section.isBuilt() && !section.isDisposed())
						coverage.markVisible(section.getChunkX(), section.getChunkZ());
				}
			}
		}
		coverage.endVisibility();
	}
	
	public static final String ANGELICA_MOD_ID = "angelica";
	public static final String MINIMUM_ANGELICA_VERSION = "2.1.5";
	public static final VersionRange SUPPORTED_ANGELICA_RANGE = VersionParser
		.parseRange("[" + MINIMUM_ANGELICA_VERSION + ",)");
	
	@Override
	public String getModName() { return ANGELICA_MOD_ID; }
	
	
	
	//====================//
	// version validation //
	//====================//
	//region
	
	public void throwIfUnsupportedAngelicaVersion()
		throws IllegalStateException, AngelicaVersionGuiException
	{
		ModContainer angelica = Loader.instance()
			.getIndexedModList()
			.get(ANGELICA_MOD_ID);
		
		if (angelica == null)
		{
			throw new IllegalStateException("Angelica mod container could not be found.");
		}
		
		String installedVersion = angelica.getVersion();
		ArtifactVersion installedArtifactVersion = new DefaultArtifactVersion(installedVersion);
		if (SUPPORTED_ANGELICA_RANGE.containsVersion(installedArtifactVersion))
		{
			return;
		}
		
		throw new AngelicaVersionGuiException(installedVersion, MINIMUM_ANGELICA_VERSION);
	}
	
	//endregion
	
	
	
	//==================//
	// accessor methods //
	//==================//
	//region
	
	@Override
	public int getDepthTextureId() 
    {
	    final Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();
		return ((IRenderTargetExt) framebuffer).iris$getDepthTextureId(); 
	}

	@Override
    public boolean canDoFadeShader() { return AngelicaConfig.enableIris; }

	@Override
    public Color getFogColor() 
    {
        // Read the fog color buffer rather than GLStateManager.getFogColor(), which returns
        // an org.joml.Vector3d. DH shades and relocates JOML, so calling that method would
        // rewrite the descriptor in this class too and fail with a NoSuchMethodError against
        // Angelica's un-relocated JOML. Angelica keeps this buffer in sync with the vector.
        // Layout is (red, green, blue, alpha); the fog alpha isn't used here.
        FloatBuffer fogColor = GLStateManager.getFogState().getFogColorBuffer();
        return new Color(
            Math.max(0.0f, Math.min(1.0f, fogColor.get(0))),
            Math.max(0.0f, Math.min(1.0f, fogColor.get(1))),
            Math.max(0.0f, Math.min(1.0f, fogColor.get(2))));
    }
	
	//endregion
	
	
	
}

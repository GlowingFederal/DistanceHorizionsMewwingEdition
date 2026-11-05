/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiCullingFrustum;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShadowCullingFrustum;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.QuadTree.LodQuadTree;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.cullingFrustum.DhFrustumBounds;
import com.seibel.distanthorizons.core.render.renderer.cullingFrustum.NeverCullFrustum;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import org.joml.Matrix4f;

import java.util.ArrayList;

/**
 * This object tells the {@link LodRenderer} what buffers to render
 */
public class RenderBufferHandler implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);

	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	
	// static values for re-use to reduce GC pressure
	private static final float[] JOML_TRANSPOSE_ARRAY = new float[16];
	private static final Matrix4f WORLD_VIEW_JOML_MATRIX = new Matrix4f();
	private static final Matrix4f WORLD_VIEW_PROJ_JOML_MATRIX = new Matrix4f();
	private static final DhMat4f FRUSTOM_DH_MATRIX = new DhMat4f();
	
	
	/** contains all relevant data */
	public final LodQuadTree lodQuadTree;
	
	private final SortedArraySet<LodBufferContainer> loadedNearToFarBuffers;

	
	private final LodPresentation<LodBufferContainer> presentation = new LodPresentation<>(LodBufferContainer::releaseFromRender);
    private final ArrayList<LodBufferContainer> desiredBuffers = new ArrayList<>();
    private final ArrayList<LodBufferContainer> presentedBuffers = new ArrayList<>();
    private final LodCoverageIndex desiredCoverage = new LodCoverageIndex();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet fallbackPositions = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet fallbackAncestors = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final ArrayList<LodBufferContainer> fallbackBuffers = new ArrayList<>();
    private volatile boolean closed;
    private int visibleBufferCount;
	private int culledBufferCount;
	private int shadowVisibleBufferCount;
	private int shadowCulledBufferCount;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region
	
	public RenderBufferHandler(LodQuadTree lodQuadTree) 
	{ 
		this.lodQuadTree = lodQuadTree;
		
		IDhApiCullingFrustum coreCameraFrustum = DhApi.overrides.get(IDhApiCullingFrustum.class, IOverrideInjector.CORE_PRIORITY);
		if (coreCameraFrustum == null)
		{
			DhApi.overrides.bind(IDhApiCullingFrustum.class, new DhFrustumBounds());
		}
		
		// by default the shadow pass shouldn't have any frustum culling
		IDhApiShadowCullingFrustum coreShadowFrustum = DhApi.overrides.get(IDhApiShadowCullingFrustum.class, IOverrideInjector.CORE_PRIORITY);
		if (coreShadowFrustum == null)
		{
			DhApi.overrides.bind(IDhApiShadowCullingFrustum.class, new NeverCullFrustum());
		}
		
		this.loadedNearToFarBuffers = new SortedArraySet<>(this::sortBufferContainersNearToFar);
	}
	private int sortBufferContainersNearToFar(LodBufferContainer loadedBufferA, LodBufferContainer loadedBufferB)
	{
		DhBlockPos2D aPos = DhSectionPos.getCenterBlockPos(loadedBufferA.pos);
		DhBlockPos2D bPos = DhSectionPos.getCenterBlockPos(loadedBufferB.pos);
		
		DhBlockPos2D centerPos = this.lodQuadTree.getCenterBlockPos();
		
		int aManhattanDistance = aPos.manhattanDist(centerPos);
		int bManhattanDistance = bPos.manhattanDist(centerPos);
		return aManhattanDistance - bManhattanDistance;
	}
	
	//endregion
	
	
	
	//=================//
	// render building //
	//=================//
	//region
	
	/**
	 * The following buildRenderList sorting method is based on the following reddit post: <br>
	 * <a href="https://www.reddit.com/r/VoxelGameDev/comments/a0l8zc/correct_depthordering_for_translucent_discrete/">correct_depth_ordering_for_translucent_discrete</a>
	 */
	public void buildRenderList(RenderParam renderParams)
	{
        if (this.closed) { this.loadedNearToFarBuffers.clear(); return; }
		if (ModInfo.IS_DEV_BUILD)
		{
			if (!RenderThreadTaskHandler.INSTANCE.isCurrentThread())
			{
				LodUtil.assertNotReach("Should only be run on the render thread");
			}
		}
		
		// clear the old list so we can start fresh
		this.loadedNearToFarBuffers.clear();
		
		
		
		//====================================//
		// get and update the culling frustum //
		//====================================//
		
		// get the culling frustum
		boolean enableFrustumCulling;
		IDhApiCullingFrustum frustum;
		boolean isShadowPass = (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isRenderingShadowPass());
		if (isShadowPass)
		{
			enableFrustumCulling = !Config.Client.Advanced.Graphics.Culling.disableShadowPassFrustumCulling.get();
			frustum = DhApi.overrides.get(IDhApiShadowCullingFrustum.class);
		}
		else
		{
			enableFrustumCulling = !Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get();
			frustum = DhApi.overrides.get(IDhApiCullingFrustum.class);
		}
		
		
		// update the frustum if necessary
		if (enableFrustumCulling)
		{
			int worldMinY = renderParams.clientLevelWrapper.getMinHeight();
			int worldHeight = renderParams.clientLevelWrapper.getMaxHeight();
			
			renderParams.mcModelViewMatrix.putValuesInArray(JOML_TRANSPOSE_ARRAY);
			WORLD_VIEW_JOML_MATRIX
				.setTransposed(JOML_TRANSPOSE_ARRAY)
				.translate(
					-(float) renderParams.exactCameraPosition.x,
					-(float) renderParams.exactCameraPosition.y,
					-(float) renderParams.exactCameraPosition.z);
			
			renderParams.dhProjectionMatrix.putValuesInArray(JOML_TRANSPOSE_ARRAY);
			WORLD_VIEW_PROJ_JOML_MATRIX
				.setTransposed(JOML_TRANSPOSE_ARRAY)
				.mul(WORLD_VIEW_JOML_MATRIX);

			FRUSTOM_DH_MATRIX.set(WORLD_VIEW_PROJ_JOML_MATRIX);
			frustum.update(worldMinY, worldMinY + worldHeight, FRUSTOM_DH_MATRIX);
		}
		
		
		
		//=========================//
		// Update the section list //
		//=========================//
		//region
		
		if (isShadowPass)
		{
			this.shadowCulledBufferCount = 0;
		}
		else
		{
			this.culledBufferCount = 0;
		}
		
        // Update the complete frontier before frustum culling. Looking away must not retire coverage.
        if (!isShadowPass)
        {
            this.lodQuadTree.acquireEnabledBuffers(this.desiredBuffers);
            if (!this.presentation.matchesCurrent(this.desiredBuffers)) this.preserveUnreplacedCoverage();
            this.presentation.update(this.desiredBuffers, System.nanoTime(),
                renderParams.renderDefinition.getRenderApi() == com.seibel.distanthorizons.api.enums.config.EDhApiRenderingApi.OPEN_GL
                    && Config.Client.Advanced.Graphics.Quality.smoothLodTransitions.get()
                    ? Config.Client.Advanced.Graphics.Quality.lodTransitionDurationMs.get() : 0);
            this.desiredBuffers.clear(); // leases now belong to presentation
        }
        this.presentedBuffers.clear();
        this.presentedBuffers.addAll(this.presentation.current());
        this.presentedBuffers.addAll(this.presentation.outgoing());
        for (LodBufferContainer buffer : this.presentedBuffers)
        {
            boolean outgoing = !this.presentation.isCurrent(buffer);
            buffer.visibilityMin = outgoing ? this.presentation.progress() : 0.0f;
            buffer.visibilityMax = outgoing ? 1.0f : (this.presentation.isIncoming(buffer) ? this.presentation.progress() : 1.0f);
            try
            {
                if (enableFrustumCulling && !frustum.intersects(
                    DhSectionPos.getMinCornerBlockX(buffer.pos), DhSectionPos.getMinCornerBlockZ(buffer.pos),
                    DhSectionPos.getBlockWidth(buffer.pos), DhSectionPos.getDetailLevel(buffer.pos)))
                {
                    if (isShadowPass) this.shadowCulledBufferCount++;
                    else this.culledBufferCount++;
                    continue;
                }
            }
            catch (Exception e) { LOGGER.error("Terrain frustum error; retaining coverage", e); }
            this.loadedNearToFarBuffers.add(buffer);
        }

		if (isShadowPass)
		{
			this.shadowVisibleBufferCount = this.loadedNearToFarBuffers.size();
		}
		else
		{
			this.visibleBufferCount = this.loadedNearToFarBuffers.size();
		}
		
		//endregion
		
	}
	
    /** Preserve live coverage even across hard cache clears and partially restored frontiers. */
    private void preserveUnreplacedCoverage()
    {
        DhBlockPos2D center = this.lodQuadTree.getCenterBlockPos();
        int radius = this.lodQuadTree.diameterInBlocks() / 2;
        this.desiredCoverage.reset(center.x - radius, center.z - radius, center.x + radius, center.z + radius);
        this.fallbackBuffers.clear();
        this.fallbackPositions.clear();
        this.fallbackAncestors.clear();
        for (LodBufferContainer buffer : this.desiredBuffers)
        {
            this.desiredCoverage.add(buffer.pos, buffer.coverage);
        }
        for (LodBufferContainer previous : this.presentation.current())
        {
            if (!this.lodQuadTree.isSectionPosInBounds(previous.pos)) continue;
            if (this.desiredCoverage.covers(previous.pos, previous.coverage)) continue;
            this.fallbackBuffers.add(previous);
            this.fallbackPositions.add(previous.pos);
            for (long pos = previous.pos; DhSectionPos.getDetailLevel(pos) <= 30; pos = DhSectionPos.getParentPos(pos))
                this.fallbackAncestors.add(pos);
        }
        if (this.fallbackBuffers.isEmpty()) return;
        for (int i = this.desiredBuffers.size() - 1; i >= 0; i--)
        {
            LodBufferContainer buffer = this.desiredBuffers.get(i);
            boolean overlap = this.fallbackAncestors.contains(buffer.pos);
            for (long pos = buffer.pos; !overlap && DhSectionPos.getDetailLevel(pos) <= 30; pos = DhSectionPos.getParentPos(pos))
                overlap = this.fallbackPositions.contains(pos);
            if (overlap) { this.desiredBuffers.remove(i); buffer.releaseFromRender(); }
        }
        for (LodBufferContainer fallback : this.fallbackBuffers)
            if (fallback.retainForRender()) this.desiredBuffers.add(fallback);
    }

	//endregion
	
	
	
	//================//
	// render methods //
	//================//
	//region
	
	public SortedArraySet<LodBufferContainer> getColumnRenderBuffers() { return this.loadedNearToFarBuffers; }
	
	//endregion
	
	
	
	//=========//
	// F3 menu //
	//=========//
	//region
	
	public String getVboRenderDebugMenuString()
	{
		String countText = F3Screen.NUMBER_FORMAT.format(this.visibleBufferCount);
		if (!Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get())
		{
			countText += "/" + F3Screen.NUMBER_FORMAT.format(this.visibleBufferCount + this.culledBufferCount);
		}
		return "VBO Render Count: [" + countText + "]";
	}
	public String getShadowPassRenderDebugMenuString()
	{
		boolean hasIrisShaders = (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isShaderPackInUse());
		if (!hasIrisShaders)
		{
			return null;
		}
		
		String countText = F3Screen.NUMBER_FORMAT.format(this.shadowVisibleBufferCount);
		if (!Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.get())
		{
			countText += "/" + F3Screen.NUMBER_FORMAT.format(this.shadowVisibleBufferCount + this.shadowCulledBufferCount);
		}
		return "Shadow VBO Render Count: [" + countText + "]";
	}
	
	//endregion
	
	
	
	//=========//
	// cleanup //
	//=========//
	//region
	
	@Override
	public synchronized void close()
    {
        if (this.closed) return;
        this.closed = true;
        this.lodQuadTree.close();
        RenderThreadTaskHandler.INSTANCE.queueRunningOnRenderThread("Terrain presentation close", () ->
        {
            this.presentation.close();
            this.loadedNearToFarBuffers.clear();
            this.presentedBuffers.clear();
            this.fallbackBuffers.clear();
            this.desiredCoverage.reset(0, 0, 0, 0);
        });
    }
	
	//endregion
	
	
	
}

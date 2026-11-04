/* This file is part of Distant Horizons, licensed under the GNU LGPL v3. */
package com.seibel.distanthorizons.core.dataObjects.render;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

/** Client mesh data only. Plants never enter terrain reduction, persistence or occlusion. */
public final class VegetationRenderData
{
	private final LongArrayList positions = new LongArrayList();
	private final LongArrayList points = new LongArrayList();
	private final ShortArrayList textures = new ShortArrayList();

	public static boolean includesColumn(int blockX, int blockZ, int percent)
	{
		// Stable across reloads and plant halves, including negative world coordinates.
		int hash = blockX * 0x1f1f1f1f ^ blockZ * 0x6c8e9cf5;
		hash ^= hash >>> 16;
		hash *= 0x45d9f3b;
		hash ^= hash >>> 16;
		return Math.floorMod(hash, 100) < percent;
	}

	public static boolean enabled(byte kind)
	{
		return kind == 1 ? Config.Client.Advanced.Graphics.Foliage.lodGrass.get()
			: kind == 2 && Config.Client.Advanced.Graphics.Foliage.lodFlowers.get();
	}

	public void add(int x, int z, long point, short textureSet)
	{
		this.positions.add((x & 63) | ((z & 63) << 6));
		this.points.add(point);
		this.textures.add(textureSet);
	}

	public int size() { return this.points.size(); }

	public void addQuads(LodQuadBuilder builder)
	{
		for (int i = 0; i < this.points.size(); i++)
		{
			long position = this.positions.getLong(i), point = this.points.getLong(i);
			short[] faces = BlockTextureRegistry.INSTANCE.getFaceTileIds(this.textures.getShort(i) & 0xffff);
			if (faces == null) continue; // no usable texture: omit the plant, never draw a solid box
			short texture = faces[EDhDirection.NORTH.faceIndex];
			if (texture == 0) continue;
			builder.addCrossQuads((short) (position & 63), RenderDataPointUtil.getYMin(point),
				(short) ((position >>> 6) & 63), (short) (RenderDataPointUtil.getYMax(point) - RenderDataPointUtil.getYMin(point)),
				RenderDataPointUtil.getColor(point), texture, RenderDataPointUtil.getBlockMaterialId(point),
				RenderDataPointUtil.getLightSky(point), RenderDataPointUtil.getLightBlock(point));
		}
	}
}

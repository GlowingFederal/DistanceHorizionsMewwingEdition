/* This file is part of Distant Horizons, licensed under the GNU LGPL v3. */
package com.seibel.distanthorizons.core.render;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;

/**
 * Render-thread ownership of completed 1.7.10 chunk meshes (including known air).
 * Loaded world data alone does not prove that a replacement mesh is available.
 * No world queries, neighbor scans, or chunk rebuilds are needed to update this mask.
 */
public final class VanillaSectionCoverage
{
	public static final VanillaSectionCoverage INSTANCE = new VanillaSectionCoverage();
	private final Long2ObjectOpenHashMap<Object> owners = new Long2ObjectOpenHashMap<>();
	private final Long2IntOpenHashMap columns = new Long2IntOpenHashMap();
	private long revision;
	private LongOpenHashSet visibleColumns = new LongOpenHashSet();
	private LongOpenHashSet nextVisibleColumns = new LongOpenHashSet();

	private static long columnKey(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }
	private static long sectionKey(int x, int y, int z)
	{ return ((long)(x & 0x3ffffff) << 30) | ((long)(z & 0x3ffffff) << 4) | y; }

	public void set(Object owner, int x, int y, int z, boolean ready)
	{
		if (y < 0 || y >= 16) return;
		long section = sectionKey(x, y, z), column = columnKey(x, z);
		Object previous = this.owners.get(section);
		if (ready)
		{
			this.owners.put(section, owner);
			if (previous != null) return;
			this.columns.put(column, this.columns.get(column) | (1 << y));
		}
		else
		{
			// A retired renderer must not revoke a newer renderer's mesh at the same position.
			if (previous != owner) return;
			this.owners.remove(section);
			int mask = this.columns.get(column) & ~(1 << y);
			if (mask == 0) this.columns.remove(column);
			else this.columns.put(column, mask);
		}
		this.revision++;
	}

	public int getMask(int x, int z) { return this.columns.get(columnKey(x, z)); }
	public long getRevision() { return this.revision; }

	/** Only columns in the current normal renderer's draw list may suppress a LOD. */
	public void beginVisibility() { this.nextVisibleColumns.clear(); }
	public void markVisible(int x, int z) { this.nextVisibleColumns.add(columnKey(x, z)); }
	public void endVisibility()
	{
		if (this.visibleColumns.equals(this.nextVisibleColumns)) return;
		LongOpenHashSet old = this.visibleColumns;
		this.visibleColumns = this.nextVisibleColumns;
		this.nextVisibleColumns = old;
		this.revision++;
	}
	public void clear()
	{
		this.owners.clear();
		this.columns.clear();
		this.visibleColumns.clear();
		this.nextVisibleColumns.clear();
		this.revision++;
	}

	/** RG contains the sixteen section bits; upload only when coverage or the window changes. */
	public void writeWindow(ByteBuffer target, int minX, int minZ, int width)
	{
		target.clear();
		for (int z = 0; z < width; z++)
		for (int x = 0; x < width; x++)
		{
			long column = columnKey(minX + x, minZ + z);
			int mask = this.visibleColumns.contains(column) ? this.columns.get(column) : 0;
			target.put((byte)mask).put((byte)(mask >>> 8)).put((byte)0).put((byte)0);
		}
		target.flip();
	}
}

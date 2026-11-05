package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.dataObjects.render.LodCoverage;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** Coverage of a non-overlapping render frontier, clipped to the current render bounds. */
public final class LodCoverageIndex
{
    private final Long2ObjectOpenHashMap<LodCoverage> sections = new Long2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap completeArea = new Long2LongOpenHashMap();
    private final LongOpenHashSet ancestors = new LongOpenHashSet();
    private int minX, minZ, maxX, maxZ;

    public void reset(int minX, int minZ, int maxX, int maxZ)
    {
        this.sections.clear();
        this.completeArea.clear();
        this.ancestors.clear();
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public void add(long pos, LodCoverage coverage)
    {
        this.sections.put(pos, coverage);
        long area = this.area(pos);
        for (long ancestor = pos; DhSectionPos.getDetailLevel(ancestor) <= 30; ancestor = DhSectionPos.getParentPos(ancestor))
        {
            this.ancestors.add(ancestor);
            if (coverage.isComplete()) this.completeArea.addTo(ancestor, area);
        }
    }

    public boolean covers(long pos, LodCoverage previous)
    {
        LodCoverage exact = this.sections.get(pos);
        if (exact != null && exact.contains(previous)) return true;
        if (this.coversRegion(pos)) return true;
        if (previous.isComplete()) return false;
        // Unknown columns of an outgoing mesh do not need replacements.
        int detail = DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL;
        int baseX = DhSectionPos.getMinCornerBlockX(pos) >> detail;
        int baseZ = DhSectionPos.getMinCornerBlockZ(pos) >> detail;
        for (int x = 0; x < 64; x++)
            for (int z = 0; z < 64; z++)
                if (previous.hasColumn(x, z) && !this.coversRegion(DhSectionPos.encode((byte) detail, baseX + x, baseZ + z)))
                    return false;
        return true;
    }

    private boolean coversRegion(long region)
    {
        long area = this.area(region);
        if (area == 0 || this.completeArea.get(region) >= area) return true;
        byte detail = DhSectionPos.getDetailLevel(region);
        for (long pos = DhSectionPos.convertToDetailLevel(region, (byte) Math.max(detail, DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL));
             DhSectionPos.getDetailLevel(pos) <= 30; pos = DhSectionPos.getParentPos(pos))
        {
            LodCoverage coverage = this.sections.get(pos);
            if (coverage == null) continue;
            int cellDetail = DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL;
            long x = DhSectionPos.getMinCornerBlockX(region), z = DhSectionPos.getMinCornerBlockZ(region);
            long width = DhSectionPos.getBlockWidth(region);
            int originX = DhSectionPos.getMinCornerBlockX(pos), originZ = DhSectionPos.getMinCornerBlockZ(pos);
            int startX = (int) ((Math.max(x, this.minX) - originX) >> cellDetail);
            int startZ = (int) ((Math.max(z, this.minZ) - originZ) >> cellDetail);
            int endX = (int) ((Math.min(x + width, this.maxX) - 1 - originX) >> cellDetail);
            int endZ = (int) ((Math.min(z + width, this.maxZ) - 1 - originZ) >> cellDetail);
            for (int cx = startX; cx <= endX; cx++)
                for (int cz = startZ; cz <= endZ; cz++)
                    if (!coverage.hasColumn(cx, cz)) return false;
            return true;
        }
        // No smaller render section can cover a sub-section region without a containing source.
        if (detail <= DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL || !this.ancestors.contains(region)) return false;
        for (int i = 0; i < 4; i++)
            if (!this.coversRegion(DhSectionPos.getChildByIndex(region, i))) return false;
        return true;
    }

    private long area(long pos)
    {
        long x = DhSectionPos.getMinCornerBlockX(pos), z = DhSectionPos.getMinCornerBlockZ(pos);
        long width = DhSectionPos.getBlockWidth(pos);
        return Math.max(0L, Math.min(x + width, this.maxX) - Math.max(x, this.minX))
            * Math.max(0L, Math.min(z + width, this.maxZ) - Math.max(z, this.minZ));
    }
}

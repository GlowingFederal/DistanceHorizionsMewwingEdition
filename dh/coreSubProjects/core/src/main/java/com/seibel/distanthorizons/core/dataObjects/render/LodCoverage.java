package com.seibel.distanthorizons.core.dataObjects.render;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import java.util.BitSet;

/** Immutable horizontal data coverage, including known air. Unknown columns are never replacements. */
public final class LodCoverage
{
    public static final LodCoverage EMPTY = new LodCoverage(new BitSet());
    private final BitSet columns;
    public LodCoverage(BitSet columns) { this.columns = (BitSet) columns.clone(); }
    public static LodCoverage from(FullDataSourceV2 source)
    {
        BitSet columns = new BitSet(FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH);
        for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
            for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
                if (source.getColumnAtRelPos(x, z) != null && !source.getColumnAtRelPos(x, z).isEmpty())
                    columns.set(x * FullDataSourceV2.WIDTH + z);
        return new LodCoverage(columns);
    }
    public boolean isEmpty() { return this.columns.isEmpty(); }
    public boolean isComplete() { return this.columns.cardinality() == FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH; }
    public boolean contains(LodCoverage previous)
    {
        for (int i = previous.columns.nextSetBit(0); i >= 0; i = previous.columns.nextSetBit(i + 1))
            if (!this.columns.get(i)) return false;
        return true;
    }
}

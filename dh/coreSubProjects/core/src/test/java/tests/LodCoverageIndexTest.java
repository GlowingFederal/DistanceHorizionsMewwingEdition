package tests;

import com.seibel.distanthorizons.core.dataObjects.render.LodCoverage;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.LodCoverageIndex;
import org.junit.Test;
import java.util.BitSet;
import java.util.Random;
import static org.junit.Assert.*;

public class LodCoverageIndexTest
{
    private static final LodCoverage FULL = coverage(0, 64, 0, 64);
    private static final long PARENT = DhSectionPos.encode((byte) 7, 0, 0);

    @Test public void shrinkingDistanceDoesNotWaitForChildrenOutsideTheNewBounds()
    {
        LodCoverageIndex index = new LodCoverageIndex();
        index.reset(0, 0, 63, 63);
        index.add(DhSectionPos.encode((byte) 6, 0, 0), FULL);
        assertTrue(index.covers(PARENT, FULL));
        index.reset(0, 0, 65, 63);
        index.add(DhSectionPos.encode((byte) 6, 0, 0), FULL);
        assertFalse(index.covers(PARENT, FULL));
    }

    @Test public void partialFinerSourcesReplaceOnlyTheKnownOutgoingFootprint()
    {
        LodCoverageIndex index = new LodCoverageIndex();
        index.reset(0, 0, 128, 128);
        index.add(DhSectionPos.encode((byte) 6, 0, 0), coverage(0, 32, 0, 32));
        assertTrue(index.covers(PARENT, coverage(0, 16, 0, 16)));
        assertFalse(index.covers(PARENT, coverage(0, 17, 0, 16)));
        assertFalse(index.covers(PARENT, FULL));
    }

    @Test public void missingInRangeTerrainKeepsTheOldMeshUntilItsReplacementArrives()
    {
        LodCoverageIndex index = new LodCoverageIndex();
        index.reset(0, 0, 128, 128);
        for (int i = 0; i < 3; i++) index.add(DhSectionPos.getChildByIndex(PARENT, i), FULL);
        assertFalse(index.covers(PARENT, FULL));
        index.add(DhSectionPos.getChildByIndex(PARENT, 3), FULL);
        assertTrue(index.covers(PARENT, FULL));
    }

    @Test public void negativeCoordinatesAndUnalignedMovingBoundsAreClipped()
    {
        LodCoverageIndex index = new LodCoverageIndex();
        long parent = DhSectionPos.encode((byte) 8, -1, -1);
        index.reset(-77, -71, -4, -3);
        for (int x = -2; x < 0; x++) for (int z = -2; z < 0; z++)
            index.add(DhSectionPos.encode((byte) 6, x, z), FULL);
        assertTrue(index.covers(parent, FULL));
        index.reset(-129, -71, -4, -3);
        for (int x = -2; x < 0; x++) for (int z = -2; z < 0; z++)
            index.add(DhSectionPos.encode((byte) 6, x, z), FULL);
        assertFalse(index.covers(parent, FULL));
    }

    @Test public void partialCoarserSourceCanReplaceDetailedKnownAirOrTerrain()
    {
        LodCoverageIndex index = new LodCoverageIndex();
        index.reset(0, 0, 128, 128);
        index.add(PARENT, coverage(0, 32, 0, 32));
        assertTrue(index.covers(DhSectionPos.encode((byte) 6, 0, 0), FULL));
        assertFalse(index.covers(DhSectionPos.encode((byte) 6, 1, 0), FULL));
    }

    @Test public void sectionReloadsDoNotAccumulateCoverageFromOldFrontiers()
    {
        LodCoverageIndex index = new LodCoverageIndex();
        index.reset(0, 0, 128, 128);
        index.add(PARENT, FULL);
        assertTrue(index.covers(PARENT, FULL));
        index.reset(0, 0, 128, 128);
        assertFalse(index.covers(PARENT, FULL));
    }

    @Test public void partialBoundaryColumnsOnlyNeedTheirInRangePortion()
    {
        LodCoverageIndex index = new LodCoverageIndex();
        index.reset(1, 1, 3, 3);
        index.add(DhSectionPos.encode((byte) 6, 0, 0), coverage(1, 3, 1, 3));
        assertTrue(index.covers(PARENT, coverage(0, 2, 0, 2)));
    }

    @Test public void mixedPartialFrontiersMatchTheirActualBlockFootprints()
    {
        Random random = new Random(319771L);
        LodCoverageIndex index = new LodCoverageIndex();
        for (int sample = 0; sample < 40; sample++)
        {
            int minX = random.nextInt(64), minZ = random.nextInt(64);
            int maxX = minX + 1 + random.nextInt(128 - minX);
            int maxZ = minZ + 1 + random.nextInt(128 - minZ);
            index.reset(minX, minZ, maxX, maxZ);
            boolean[][] blocks = new boolean[128][128];
            for (int sx = 0; sx < 2; sx++) for (int sz = 0; sz < 2; sz++)
            {
                BitSet columns = new BitSet(4096);
                for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++)
                    if (random.nextInt(20) != 0)
                    {
                        columns.set(x * 64 + z);
                        blocks[sx * 64 + x][sz * 64 + z] = true;
                    }
                index.add(DhSectionPos.encode((byte) 6, sx, sz), new LodCoverage(columns));
            }
            for (int request = 0; request < 16; request++)
            {
                int x = random.nextInt(64), z = random.nextInt(64);
                LodCoverage outgoing = coverage(x, x + 1, z, z + 1);
                boolean covered = true;
                for (int bx = Math.max(minX, x * 2); bx < Math.min(maxX, x * 2 + 2); bx++)
                    for (int bz = Math.max(minZ, z * 2); bz < Math.min(maxZ, z * 2 + 2); bz++)
                        covered &= blocks[bx][bz];
                assertEquals("sample " + sample + ", request " + request, covered, index.covers(PARENT, outgoing));
            }
        }
    }

    private static LodCoverage coverage(int minX, int maxX, int minZ, int maxZ)
    {
        BitSet bits = new BitSet(4096);
        for (int x = minX; x < maxX; x++) for (int z = minZ; z < maxZ; z++) bits.set(x * 64 + z);
        return new LodCoverage(bits);
    }
}

package tests;

import com.seibel.distanthorizons.core.render.LodPresentation;
import com.seibel.distanthorizons.core.dataObjects.render.LodCoverage;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TerrainPresentationTest
{
    @Test public void uploadedFrontierKeepsOutgoingLeaseUntilPresentationCompletes()
    {
        List<String> released = new ArrayList<>();
        LodPresentation<String> state = new LodPresentation<>(released::add);
        state.update(Arrays.asList("coarse"), 0L, 0);
        state.update(Arrays.asList("fine0", "fine1", "fine2", "fine3"), 100L, 350);
        assertEquals(Arrays.asList("coarse"), state.outgoing());
        assertFalse(released.contains("coarse"));
        // A newer frontier cannot interrupt a half-presented handoff or grow retained history.
        state.update(Arrays.asList("newer"), 175_000_100L, 350);
        assertEquals(0.5f, state.progress(), 0.00001f);
        assertEquals(Arrays.asList("newer"), released);
        state.update(Arrays.asList("fine0", "fine1", "fine2", "fine3"), 350_000_100L, 350);
        assertTrue(state.outgoing().isEmpty());
        assertEquals(1, Collections.frequency(released, "coarse"));
        assertFalse(state.isIncoming("fine0"));
        state.close();
        assertEquals(2, Collections.frequency(released, "fine0")); // redundant snapshot + active lease
    }
    @Test public void disablingAnimationAndClosingReleaseBothFrontiers()
    {
        List<String> released = new ArrayList<>();
        LodPresentation<String> state = new LodPresentation<>(released::add);
        state.update(Arrays.asList("old"), 0L, 0);
        state.update(Arrays.asList("new"), 10L, 1000);
        state.update(Arrays.asList("new"), 11L, 0);
        assertTrue(state.outgoing().isEmpty());
        assertEquals(1.0f, state.progress(), 0.0f);
        state.close();
        assertEquals(1, Collections.frequency(released, "old"));
        assertEquals(2, Collections.frequency(released, "new"));
    }
    @Test public void progressIsMonotonicAndIndependentOfFrameCount()
    {
        assertEquals(0, LodPresentation.progress(0, 1, 350), 0);
        assertEquals(0.5f, LodPresentation.progress(175_000_000L, 0, 350), 0.00001f);
        assertEquals(1, LodPresentation.progress(10_000_000_000L, 0, 350), 0);
        assertEquals(1, LodPresentation.progress(0, 0, 0), 0);
    }
    @Test public void missingColumnsCannotReplaceKnownAirOrTerrain() throws Exception
    {
        try (FullDataSourceV2 source = FullDataSourceV2.createEmpty(DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, 0, 0)))
        {
            assertTrue(LodCoverage.from(source).isEmpty());
            // A real encoded column counts as data even if its mapped block is air.
            source.dataPoints[0] = new LongArrayList(new long[] { FullDataPointUtil.encode(0, 1, 0, (byte)15, (byte)0) });
            LodCoverage partial = LodCoverage.from(source);
            assertFalse(partial.isEmpty());
            assertFalse(partial.isComplete());
            assertFalse(LodCoverage.EMPTY.contains(partial));
            assertTrue(partial.contains(LodCoverage.EMPTY));
            for (int i = 1; i < source.dataPoints.length; i++) source.dataPoints[i] = new LongArrayList(source.dataPoints[0]);
            LodCoverage complete = LodCoverage.from(source);
            assertTrue(complete.isComplete());
            assertTrue(complete.contains(partial));
            assertFalse(partial.contains(complete));
        }
    }
}

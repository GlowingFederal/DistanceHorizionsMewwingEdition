package tests;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Test;
import testItems.wrappers.TestBiomeWrapper;
import testItems.wrappers.TestBlockStateWrapper;

import static org.junit.Assert.*;

public class FullDataRefinementTest
{
	@Test public void coarseFallbackDoesNotDowngradeExistingDetailedColumns() throws Exception
	{
		try (FullDataSourceV2 parent = source((byte)7); FullDataSourceV2 child = source((byte)6)) {
			fillParent(parent);
			LongArrayList detailed = column(70);
			child.setSingleColumn(detailed, 0, 0, EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.VISUALLY_EQUAL);
			assertTrue(child.downsampleFromOneAboveDetailLevel(parent, new int[]{0}));
			assertEquals(detailed, child.getColumnAtRelPos(0, 0));
			assertEquals(EDhApiWorldGenerationStep.LIGHT.value, child.columnGenerationSteps.getByte(0));
			assertEquals(EDhApiWorldCompressionMode.VISUALLY_EQUAL.value, child.columnWorldCompressionMode.getByte(0));
		}
	}

	@Test public void fallbackCompressionFollowsTheSampledParentColumn() throws Exception
	{
		try (FullDataSourceV2 parent = source((byte)7); FullDataSourceV2 child = source((byte)6)) {
			fillParent(parent);
			// Child (0,2) reads parent (0,1), not parent (0,2).
			parent.setSingleColumn(column(42), 0, 1, EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.VISUALLY_EQUAL);
			child.downsampleFromOneAboveDetailLevel(parent, new int[]{0});
			assertEquals(EDhApiWorldCompressionMode.VISUALLY_EQUAL.value, child.columnWorldCompressionMode.getByte(2));
			assertEquals(EDhApiWorldGenerationStep.DOWN_SAMPLED.value, child.columnGenerationSteps.getByte(2));
			assertEquals(42, FullDataPointUtil.getHeight(child.getColumnAtRelPos(0, 2).getLong(0)));
		}
	}

	@Test public void detailedArrivalReplacesFallbackAndSubsequentFallbackCannotRegressIt() throws Exception
	{
		try (FullDataSourceV2 parent = source((byte)7); FullDataSourceV2 child = source((byte)6);
			FullDataSourceV2 detailed = source((byte)6)) {
			fillParent(parent);
			child.downsampleFromOneAboveDetailLevel(parent, new int[]{0});
			detailed.setSingleColumn(column(70), 0, 0, EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.VISUALLY_EQUAL);
			assertTrue(child.updateFromDataSource(detailed));
			assertEquals(70, FullDataPointUtil.getHeight(child.getColumnAtRelPos(0, 0).getLong(0)));
			assertFalse(child.downsampleFromOneAboveDetailLevel(parent, new int[]{0}));
			assertEquals(EDhApiWorldGenerationStep.LIGHT.value, child.columnGenerationSteps.getByte(0));
		}
	}

	private static FullDataSourceV2 source(byte detail)
	{
		FullDataSourceV2 source = FullDataSourceV2.createEmpty(DhSectionPos.encode(detail, 0, 0));
		source.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("plains"), new TestBlockStateWrapper("refinement_test:stone"));
		return source;
	}

	private static void fillParent(FullDataSourceV2 parent) throws Exception
	{
		for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
			for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
				parent.setSingleColumn(column(64), x, z, EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
	}

	private static LongArrayList column(int height) throws Exception
	{
		return new LongArrayList(new long[]{FullDataPointUtil.encode(0, height, 0, (byte)0, (byte)15)});
	}
}

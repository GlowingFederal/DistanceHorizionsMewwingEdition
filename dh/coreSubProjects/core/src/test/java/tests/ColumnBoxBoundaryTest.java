package tests;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnBox;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class ColumnBoxBoundaryTest
{
	private static final IClientLevelWrapper LEVEL = (IClientLevelWrapper) Proxy.newProxyInstance(
		IClientLevelWrapper.class.getClassLoader(), new Class<?>[]{IClientLevelWrapper.class},
		(proxy, method, args) -> {
			if (method.getName().equals("getShade")) return 1.0f;
			throw new AssertionError("Unexpected level access: " + method);
		});

	@Test
	public void unknownNeighborsDoNotInventCliffs() throws Exception
	{
		assertEquals(0, sideCount(0, 0, 0));
		assertEquals(0, sideCount(1, 0, 0));
	}

	@Test
	public void confirmedAirKeepsIslandSides() throws Exception
	{
		long air = RenderDataPointUtil.createDataPoint(256, 0, 0, 15, 0, EDhApiBlockMaterial.AIR.index);
		assertEquals(1, sideCount(1, 0, air));
	}

	@Test
	public void realTerrainStillOccludesAndExposesCliffs() throws Exception
	{
		long levelGround = RenderDataPointUtil.createDataPoint(64, 0, 0xff808080, 15, 0, 2);
		long lowerGround = RenderDataPointUtil.createDataPoint(32, 0, 0xff808080, 15, 0, 2);
		assertEquals(0, sideCount(1, levelGround, 0));
		assertEquals(1, sideCount(1, lowerGround, 0));
	}

	private static int sideCount(int size, long point, long voidPoint) throws Exception
	{
		LongArrayList data = new LongArrayList(new long[]{point});
		LongArrayList voidData = new LongArrayList(new long[]{voidPoint});
		try (ColumnRenderView column = ColumnRenderView.getPooled(data, voidData, size, 0, 0, 1);
			LodQuadBuilder builder = LodQuadBuilder.getBuilder(false, LEVEL);
			PhantomArrayListCheckout arrays = LodQuadBuilder.ARRAY_LIST_POOL.checkoutLongArrays(2))
		{
			Method method = ColumnBox.class.getDeclaredMethod("makeAdjVerticalQuad",
				LodQuadBuilder.class, PhantomArrayListCheckout.class, IClientLevelWrapper.class,
				ColumnRenderView.class, short[].class, boolean.class, int.class, EDhDirection.class,
				short.class, short.class, short.class, short.class, short.class,
				int.class, byte.class, byte.class);
			method.setAccessible(true);
			method.invoke(null, builder, arrays, LEVEL, column, null, true, 0, EDhDirection.NORTH,
				(short)8, (short)0, (short)8, (short)1, (short)64,
				0xff808080, (byte)2, (byte)0);
			return builder.getCurrentOpaqueQuadsCount() + builder.getCurrentTransparentQuadsCount();
		}
	}
}

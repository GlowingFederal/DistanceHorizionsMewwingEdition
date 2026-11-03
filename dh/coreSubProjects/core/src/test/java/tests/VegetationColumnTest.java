package tests;

import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.*;
import testItems.wrappers.TestBiomeWrapper;
import testItems.wrappers.TestBlockStateWrapper;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** Exercises the production full-data transformer, including its packed column format. */
public class VegetationColumnTest
{
	private static final TestBiomeWrapper BIOME = new TestBiomeWrapper("plains");
	private static final IBlockStateWrapper TERRAIN = block("stone", true, false, 16, EDhApiBlockMaterial.STONE.index);
	private static final IBlockStateWrapper FLOWER = block("flower", false, false, 0, EDhApiBlockMaterial.UNKNOWN.index);
	private static final IBlockStateWrapper AIR = block("air", false, true, 0, EDhApiBlockMaterial.AIR.index);
	private static final IBlockStateWrapper LEAVES = block("leaves", true, false, 16, EDhApiBlockMaterial.LEAVES.index);
	private static final IBlockStateWrapper SAME_COLOR_TERRAIN = block("same_color_terrain", true, false, 16, EDhApiBlockMaterial.STONE.index);
	private static final int STONE_COLOR = 0xff808080, FLOWER_COLOR = 0xffff2020, LEAF_COLOR = 0xff408020;
	private static final IClientLevelWrapper LEVEL = (IClientLevelWrapper) Proxy.newProxyInstance(
		IClientLevelWrapper.class.getClassLoader(), new Class<?>[]{IClientLevelWrapper.class}, (proxy, method, args) -> {
			if (method.getName().equals("getMinHeight")) return 0;
			if (method.getName().equals("getBlockColor")) {
				Object block = args[3];
				return block == FLOWER || block == SAME_COLOR_TERRAIN ? FLOWER_COLOR : block == LEAVES ? LEAF_COLOR : block == AIR ? 0 : STONE_COLOR;
			}
			throw new AssertionError("Unexpected level access: " + method);
		});
	private static IWrapperFactory oldFactory;
	private boolean oldTint, oldCulling;
	private EDhApiBlocksToAvoid oldAvoid;

	@BeforeClass public static void bindFactory()
	{
		oldFactory = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
		IWrapperFactory factory = (IWrapperFactory) Proxy.newProxyInstance(IWrapperFactory.class.getClassLoader(),
			new Class<?>[]{IWrapperFactory.class, IBindable.class}, (proxy, method, args) -> {
				String name = method.getName();
				if (name.equals("getDelayedSetupComplete")) return true;
				if (name.equals("getRendererIgnoredBlocks") || name.equals("getRendererIgnoredCaveBlocks"))
					return new ObjectOpenHashSet<>(new IBlockStateWrapper[]{AIR});
				if (name.startsWith("getWater") && name.endsWith("Blocks")) return new ObjectOpenHashSet<IBlockStateWrapper>();
				if (name.equals("getWaterBlockStateWrapper") || name.startsWith("deserializeBlockState")) return AIR;
				throw new AssertionError("Unexpected factory access: " + method);
			});
		SingletonInjector.INSTANCE.replaceBinding(IWrapperFactory.class, factory);
	}

	@AfterClass public static void restoreFactory()
	{
		if (oldFactory != null) SingletonInjector.INSTANCE.replaceBinding(IWrapperFactory.class, oldFactory);
		else SingletonInjector.INSTANCE.unbindAll(IWrapperFactory.class);
	}

	@Before public void config()
	{
		oldTint = Config.Client.Advanced.Graphics.Culling.tintWithAvoidedBlocks.get();
		oldCulling = Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get();
		oldAvoid = Config.Client.Advanced.Graphics.Culling.blocksToIgnore.get();
		Config.Client.Advanced.Graphics.Culling.tintWithAvoidedBlocks.setWithoutFiringEvents(true);
		Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(false);
		Config.Client.Advanced.Graphics.Culling.blocksToIgnore.setWithoutFiringEvents(EDhApiBlocksToAvoid.NON_COLLIDING);
	}

	@After public void restoreConfig()
	{
		Config.Client.Advanced.Graphics.Culling.tintWithAvoidedBlocks.setWithoutFiringEvents(oldTint);
		Config.Client.Advanced.Graphics.Culling.enableCaveCulling.setWithoutFiringEvents(oldCulling);
		Config.Client.Advanced.Graphics.Culling.blocksToIgnore.setWithoutFiringEvents(oldAvoid);
	}

	@Test public void flowerTintsSurfaceButNotTallCliff() throws Exception
	{
		long[] result = transform((byte) 0, true, FLOWER, TERRAIN);
		assertPoint(result[0], 63, 64, FLOWER_COLOR, FLOWER.getMaterialId());
		assertPoint(result[1], 0, 63, STONE_COLOR, TERRAIN.getMaterialId());
	}

	@Test public void coarseColumnsKeepTerrainColor() throws Exception
	{
		long[] result = transform((byte) 2, true, FLOWER, TERRAIN);
		assertPoint(result[0], 0, 64, STONE_COLOR, TERRAIN.getMaterialId());
		assertEquals(0, result[1]);
	}

	@Test public void equalColorsDoNotSpreadDecorativeMaterialDownCliffs() throws Exception
	{
		long[] result = transform((byte) 0, true, FLOWER, SAME_COLOR_TERRAIN);
		assertPoint(result[0], 63, 64, FLOWER_COLOR, FLOWER.getMaterialId());
		assertPoint(result[1], 0, 63, FLOWER_COLOR, TERRAIN.getMaterialId());
	}

	@Test public void verticalReductionDoesNotSpreadFlowerColor() throws Exception
	{
		long[] result = transform((byte) 0, false, FLOWER, TERRAIN);
		assertPoint(result[0], 0, 64, STONE_COLOR, TERRAIN.getMaterialId());
	}

	@Test public void tintSettingStillDisablesTransfer() throws Exception
	{
		Config.Client.Advanced.Graphics.Culling.tintWithAvoidedBlocks.setWithoutFiringEvents(false);
		assertPoint(transform((byte) 0, true, FLOWER, TERRAIN)[0], 0, 64, STONE_COLOR, TERRAIN.getMaterialId());
	}

	@Test public void includeAllBlocksStillKeepsFlowerGeometry() throws Exception
	{
		Config.Client.Advanced.Graphics.Culling.blocksToIgnore.setWithoutFiringEvents(EDhApiBlocksToAvoid.NONE);
		long[] result = transform((byte) 0, true, FLOWER, TERRAIN);
		assertPoint(result[0], 64, 65, FLOWER_COLOR, FLOWER.getMaterialId());
		assertPoint(result[1], 0, 64, STONE_COLOR, TERRAIN.getMaterialId());
	}

	@Test public void leavesRemainOpaqueAndKeepTheirMaterial() throws Exception
	{
		long[] result = transform((byte) 0, true, LEAVES, TERRAIN);
		assertPoint(result[0], 64, 65, LEAF_COLOR, LEAVES.getMaterialId());
		assertPoint(result[1], 0, 64, STONE_COLOR, TERRAIN.getMaterialId());
	}

	@Test public void airGapStopsTintTransfer() throws Exception
	{
		long[] result = transform((byte) 0, true, FLOWER, AIR, TERRAIN);
		assertPoint(result[0], 0, 64, STONE_COLOR, TERRAIN.getMaterialId());
	}

	private static long[] transform(byte detail, boolean preserveTint, IBlockStateWrapper... blocks) throws Exception
	{
		try (FullDataSourceV2 source = FullDataSourceV2.createEmpty(DhSectionPos.encode((byte) (6 + detail), 0, 0))) {
			LongArrayList column = new LongArrayList();
			for (int i = 0; i < blocks.length; i++) {
				int id = source.mapping.addIfNotPresentAndGetId(BIOME, blocks[i]);
				int bottom = i == blocks.length - 1 ? 0 : 64 + blocks.length - 2 - i;
				column.add(FullDataPointUtil.encode(id, i == blocks.length - 1 ? 64 : 1, bottom, (byte)0, (byte)15));
			}
			LongArrayList data = new LongArrayList(new long[blocks.length]);
			try (ColumnRenderView view = ColumnRenderView.getPooled(data, new LongArrayList(new long[1]), blocks.length, 0, 0, blocks.length)) {
				Method method = FullDataToRenderDataTransformer.class.getDeclaredMethod("setRenderColumnView",
					IClientLevelWrapper.class, FullDataSourceV2.class, int.class, int.class, ColumnRenderView.class,
					LongArrayList.class, DhBlockPosMutable.class, boolean.class);
				method.setAccessible(true);
				method.invoke(null, LEVEL, source, 0, 0, view, column, new DhBlockPosMutable(), preserveTint);
				return data.toLongArray();
			}
		}
	}

	private static void assertPoint(long point, int min, int max, int color, byte material)
	{
		assertEquals(min, RenderDataPointUtil.getYMin(point));
		assertEquals(max, RenderDataPointUtil.getYMax(point));
		assertEquals(color, RenderDataPointUtil.getColor(point));
		assertEquals(material, RenderDataPointUtil.getBlockMaterialId(point));
	}

	private static IBlockStateWrapper block(String name, boolean solid, boolean air, int opacity, byte material)
	{
		return new TestBlockStateWrapper("vegetation_test:" + name) {
			@Override public boolean isSolid() { return solid; }
			@Override public boolean isAir() { return air; }
			@Override public int getOpacity() { return opacity; }
			@Override public byte getMaterialId() { return material; }
		};
	}
}

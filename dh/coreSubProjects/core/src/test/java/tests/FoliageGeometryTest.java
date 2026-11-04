package tests;

import com.seibel.distanthorizons.core.dataObjects.render.VegetationRenderData;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.BufferQuad;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.BufferMergeDirectionEnum;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockFaceTexture;
import com.seibel.distanthorizons.core.dataObjects.render.textures.BlockTextureRegistry;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout;
import org.junit.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class FoliageGeometryTest
{
	private static final IClientLevelWrapper LEVEL = (IClientLevelWrapper) Proxy.newProxyInstance(
		IClientLevelWrapper.class.getClassLoader(), new Class<?>[]{IClientLevelWrapper.class}, (proxy, method, args) -> {
			if (method.getName().equals("getShade")) return 1.0f;
			throw new AssertionError("Unexpected level access: " + method);
		});

	@Test public void crossesAreOpaqueDoubleSidedAndPooled() throws Exception
	{
		try (LodQuadBuilder builder = LodQuadBuilder.getBuilder(true, LEVEL);
			PhantomArrayListCheckout arrays = LodQuadBuilder.ARRAY_LIST_POOL.checkoutLongArrays(0)) {
			builder.addCrossQuads((short)2, (short)70, (short)3, (short)1, 0xff408020, (short)11, (byte)0, (byte)15, (byte)0);
			builder.mergeQuads();
			assertEquals(4, builder.getCurrentOpaqueQuadsCount());
			assertEquals(0, builder.getCurrentTransparentQuadsCount());
			ArrayList<ByteBuffer> buffers = builder.makeOpaqueVertexBuffers(arrays);
			assertEquals(1, buffers.size());
			ByteBuffer buffer = buffers.get(0).duplicate().order(ByteOrder.nativeOrder());
			assertEquals(4 * LodQuadBuilder.BYTES_PER_QUAD, buffer.remaining());
			int normal6 = 0, normal7 = 0;
			for (int i = 0; i < 16; i++) {
				int offset = i * 16;
				assertTrue(buffer.getShort(offset) == 2 || buffer.getShort(offset) == 3);
				assertTrue(buffer.getShort(offset + 2) == 70 || buffer.getShort(offset + 2) == 71);
				assertTrue(buffer.getShort(offset + 4) == 3 || buffer.getShort(offset + 4) == 4);
				int normal = buffer.get(offset + 13) & 255;
				if (normal == 6) normal6++; else if (normal == 7) normal7++; else fail("Invalid cross normal");
				assertEquals(11, buffer.getShort(offset + 14));
			}
			assertEquals(8, normal6); assertEquals(8, normal7);
		}
		try (LodQuadBuilder builder = LodQuadBuilder.getBuilder(false, LEVEL)) {
			assertEquals(0, builder.getCurrentOpaqueQuadsCount());
		}
	}

	@Test public void leafCutoutsCannotMergeIntoOpaqueFaces()
	{
		BufferQuad leaf = new BufferQuad(), solid = new BufferQuad();
		leaf.set((short)0,(short)1,(short)0,(short)1,(short)1,0xff408020,(short)1,(byte)1,(byte)15,(byte)0,EDhDirection.UP);
		solid.set((short)1,(short)1,(short)0,(short)1,(short)1,0xff408020,(short)1,(byte)1,(byte)15,(byte)0,EDhDirection.UP);
		leaf.seeThrough = true;
		assertFalse(leaf.tryMerge(solid, BufferMergeDirectionEnum.EastWest));
	}

	@Test public void densityIsStableAndIncludesNegativeCoordinates()
	{
		int selected = 0;
		for (int x = -128; x < 128; x++) for (int z = -128; z < 128; z++) {
			assertFalse(VegetationRenderData.includesColumn(x,z,0));
			assertTrue(VegetationRenderData.includesColumn(x,z,100));
			if (VegetationRenderData.includesColumn(x,z,50)) selected++;
		}
		assertTrue(selected > 32000 && selected < 33500);
	}

	@Test public void singleColorCutoutsRetainTheirMask() throws Exception
	{
		int[] pixels = new int[]{0xff408020,0,0xff408020,0};
		Method method = BlockTextureRegistry.class.getDeclaredMethod("convertColorsToDifferenceRatios", BlockFaceTexture.class);
		method.setAccessible(true);
		byte[] texture = (byte[]) method.invoke(null, BlockFaceTexture.createTexture(2,2,pixels));
		assertNotNull(texture);
		boolean gap = false, solid = false;
		for (int i = 3; i < texture.length; i += 4) { gap |= texture[i] == 0; solid |= (texture[i] & 255) == 255; }
		assertTrue(gap && solid);
	}
}

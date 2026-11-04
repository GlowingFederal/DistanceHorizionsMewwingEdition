package com.seibel.distanthorizons.common.backports;

#if MC_VER <= MC_1_7_10

import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Used by {@link ClientBlockStateColorCache}
 * in order to handle some world related operations.
 */
public class FakeWorld implements IBlockAccess
{
	private IBlockAccess realWorld;
	private BiomeGenBase biome;
    private com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2 fullDataSource;
	
	private int blockX;
	private int blockY;
	private int blockZ;
	
	private FakeBlockState blockState;
	
	
	
	//========//
	// update //
	//========//
	//region
	
	public void update(
		IBlockAccess realWorld, BiomeGenBase biome, 
		int blockX, int blockY, int blockZ, 
		FakeBlockState blockState,
        com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2 fullDataSource)
	{
        this.fullDataSource = fullDataSource;
		this.realWorld = realWorld;
		this.biome = biome;
		
		this.blockX = blockX;
		this.blockY = blockY;
		this.blockZ = blockZ;
		
		this.blockState = blockState;
		if (this.biome == null)
		{
			this.biome = BiomeGenBase.plains; // Fallback to prevent null pointers
		}
	}
	
	public void clear() { this.fullDataSource = null; this.realWorld = null; this.biome = null; this.blockState = null; }
    //endregion
	
	
	
	//===========//
	// overrides //
	//===========//
	//region
	
	@Override
	public Block getBlock(int x, int y, int z)
	{
		if (x == this.blockX 
			&& y == this.blockY 
			&& z == this.blockZ)
		{
			return this.blockState.block;
		}
		
		return Blocks.air;
	}

	@Override
	public TileEntity getTileEntity(int x, int y, int z) { return null; }

	@Override
	public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) { return 0; }

	@Override
	public int getBlockMetadata(int x, int y, int z)
	{
		if (x == this.blockX 
			&& y == this.blockY 
			&& z == this.blockZ)
		{
			return this.blockState.meta;
		}
		else if (this.blockState.block instanceof BlockDoublePlant)
		{
			/*
			 * Double tall grass on 1.7.10 has
			 * top block = meta 8
			 * bottom block = meta 2
			 * To get the color of the top block, BlockDoublePlant uses the meta of the bottom block.
			 * The render transformer carries the bottom's type in the transient top state,
			 * so flower tops retain their color and grass/fern tops receive biome tint.
			 */
			return this.blockState.meta & 7;
		}
		
		return 0;
	}

	@Override
	public int isBlockProvidingPowerTo(int x, int y, int z, int directionIn) { return 0; }

	@Override
	public boolean isAirBlock(int x, int y, int z) { return false; }

	@Override
	public BiomeGenBase getBiomeGenForCoords(int x, int z)
	{
		// Legacy biomes are horizontal. Sampling at the leaf/grass Y can land in air;
        // the top recorded column still carries the authoritative biome for this X/Z.
        if (this.fullDataSource != null)
        {
            long pos = this.fullDataSource.getPos();
            int detail = this.fullDataSource.getDataDetailLevel();
            int relX = (x - com.seibel.distanthorizons.core.pos.DhSectionPos.getMinCornerBlockX(pos)) >> detail;
            int relZ = (z - com.seibel.distanthorizons.core.pos.DhSectionPos.getMinCornerBlockZ(pos)) >> detail;
            it.unimi.dsi.fastutil.longs.LongArrayList column = this.fullDataSource.tryGetColumnAtRelPos(relX, relZ);
            if (column != null && !column.isEmpty())
            {
                int id = com.seibel.distanthorizons.core.util.FullDataPointUtil.getId(column.getLong(0));
                BiomeGenBase sampled = ((com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper)
                    this.fullDataSource.mapping.getBiomeWrapper(id)).biome;
                if (sampled != null) return sampled;
            }
        }
        return this.biome;
	}

	@Override
	public int getHeight() { return this.realWorld.getHeight(); }

	@Override
	public boolean extendedLevelsInChunkCache() { return this.realWorld.extendedLevelsInChunkCache(); }

	@Override
	public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) { throw new UnsupportedOperationException("isSideSolid() isn't implemented"); }
	
	//endregion
	
	
	
}

#endif

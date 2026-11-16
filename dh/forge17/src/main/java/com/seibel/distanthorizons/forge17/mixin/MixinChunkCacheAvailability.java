package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.forge17.ChunkAvailability;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkCache.class)
public abstract class MixinChunkCacheAvailability implements ChunkAvailability
{
	@Shadow private int chunkX, chunkZ;
	@Shadow private Chunk[][] chunkArray;

	@Override public boolean dh$isChunkMissing(int chunkX, int chunkZ)
	{
		int x = chunkX - this.chunkX, z = chunkZ - this.chunkZ;
		return x < 0 || x >= this.chunkArray.length || z < 0 || z >= this.chunkArray[x].length
			|| this.chunkArray[x][z] == null || this.chunkArray[x][z] instanceof EmptyChunk;
	}
}

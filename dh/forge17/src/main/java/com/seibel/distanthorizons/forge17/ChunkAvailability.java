package com.seibel.distanthorizons.forge17;

/** Availability captured with a chunk mesher's block data, including known air. */
public interface ChunkAvailability
{
	boolean dh$isChunkMissing(int chunkX, int chunkZ);
}

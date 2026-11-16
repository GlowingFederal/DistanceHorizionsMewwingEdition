package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.forge17.ChunkAvailability;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** UAB's source-water view retains its underlying mesher snapshot's availability. */
@Pseudo
@Mixin(targets = "ganymedes01.etfuturum.client.renderer.block.ContainedWaterAccess", remap = false)
public abstract class MixinContainedWaterAvailability implements ChunkAvailability
{
	@Shadow @Final private IBlockAccess delegate;

	@Override public boolean dh$isChunkMissing(int chunkX, int chunkZ)
	{
		return this.delegate instanceof ChunkAvailability
			&& ((ChunkAvailability) this.delegate).dh$isChunkMissing(chunkX, chunkZ);
	}
}

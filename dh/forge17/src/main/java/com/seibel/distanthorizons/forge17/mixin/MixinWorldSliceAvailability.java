package com.seibel.distanthorizons.forge17.mixin;

import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;
import com.seibel.distanthorizons.forge17.ChunkAvailability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice", remap = false)
public abstract class MixinWorldSliceAvailability implements ChunkAvailability
{
	@Shadow private int baseX, baseZ;
	@Shadow private ClonedChunkSection[] sections;
	@Shadow public static int getLocalSectionIndex(int x, int y, int z) { throw new AssertionError(); }

	@Override public boolean dh$isChunkMissing(int chunkX, int chunkZ)
	{
		int x = chunkX - (this.baseX >> 4), z = chunkZ - (this.baseZ >> 4);
		if (x < 0 || x >= 3 || z < 0 || z >= 3) return true;
		ClonedChunkSection section = this.sections[getLocalSectionIndex(x, 1, z)];
		return section != null && ((ChunkAvailability) (Object) section).dh$isChunkMissing(chunkX, chunkZ);
	}
}

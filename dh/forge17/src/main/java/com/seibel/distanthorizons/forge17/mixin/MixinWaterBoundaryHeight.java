package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.forge17.ChunkAvailability;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep absent chunks out of the native water corner-height average. */
@Mixin(RenderBlocks.class)
public abstract class MixinWaterBoundaryHeight
{
	@Shadow public IBlockAccess blockAccess;

	@Inject(method = "getLiquidHeight", at = @At("HEAD"), cancellable = true)
	private void dh$waterBoundaryHeight(int x, int y, int z, Material material, CallbackInfoReturnable<Float> cir)
	{
		if (material != Material.water || !(this.blockAccess instanceof ChunkAvailability)
			|| !Config.Client.quickEnableRendering.get()) return;
		ChunkAvailability availability = (ChunkAvailability) this.blockAccess;
		int missing = 0;
		for (int i = 0; i < 4; i++)
			if (availability.dh$isChunkMissing((x - (i & 1)) >> 4, (z - (i >> 1)) >> 4)) missing |= 1 << i;
		if (missing == 0) return;

		// Use vanilla's weighting for every known sample, including real shoreline
		// air. Missing neighbors are unknown, rather than air that lowers the edge.
		float total = 0.0f;
		int samples = 0;
		boolean water = false;
		for (int i = 0; i < 4; i++)
		{
			if ((missing & (1 << i)) != 0) continue;
			int sx = x - (i & 1), sz = z - (i >> 1);
			if (this.blockAccess.getBlock(sx, y + 1, sz).getMaterial() == material)
			{
				cir.setReturnValue(1.0f);
				return;
			}
			Block block = this.blockAccess.getBlock(sx, y, sz);
			if (block.getMaterial() == material)
			{
				water = true;
				int meta = block instanceof BlockLiquid ? this.blockAccess.getBlockMetadata(sx, y, sz) : 0;
				float height = BlockLiquid.getLiquidHeightPercent(meta);
				if (meta >= 8 || meta == 0) { total += height * 10.0f; samples += 10; }
				total += height;
				samples++;
			}
			else if (!block.getMaterial().isSolid()) { total += 1.0f; samples++; }
		}
		if (water && samples > 0) cir.setReturnValue(1.0f - total / samples);
	}
}

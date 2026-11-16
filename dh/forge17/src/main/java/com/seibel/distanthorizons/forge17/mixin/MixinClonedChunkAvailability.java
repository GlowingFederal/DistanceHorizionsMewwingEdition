package com.seibel.distanthorizons.forge17.mixin;

import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.seibel.distanthorizons.forge17.ChunkAvailability;
import net.minecraft.world.World;
import net.minecraft.world.chunk.EmptyChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection", remap = false)
public abstract class MixinClonedChunkAvailability implements ChunkAvailability
{
	@Shadow @Final private World world;
	@Unique private boolean dh$missing;

	// init runs while Angelica captures the section on the client thread. Workers
	// subsequently read this snapshot, never the live client's chunk provider.
	@Inject(method = "init", at = @At("HEAD"))
	private void dh$captureAvailability(ChunkSectionPos pos, CallbackInfo ci)
	{
		this.dh$missing = this.world.getChunkFromChunkCoords(pos.x, pos.z) instanceof EmptyChunk;
	}

	@Override public boolean dh$isChunkMissing(int chunkX, int chunkZ) { return this.dh$missing; }
}

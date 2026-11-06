package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.core.render.VanillaSectionCoverage;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRendererCoverage
{
	@Shadow public int posX, posY, posZ;
	@Shadow public World worldObj;
	@Shadow private boolean isInitialized;

	@Inject(method = "updateRenderer", at = @At("RETURN"))
	private void dh$completed(EntityLivingBase entity, CallbackInfo ci)
	{
		// EmptyChunk is a loading placeholder, never evidence of completed air geometry.
		boolean ready = this.isInitialized && this.worldObj != null
			&& !this.worldObj.getChunkFromBlockCoords(this.posX, this.posZ).isEmpty();
		VanillaSectionCoverage.INSTANCE.set(this, this.posX >> 4, this.posY >> 4, this.posZ >> 4, ready);
	}

	@Inject(method = "setDontDraw", at = @At("HEAD"))
	private void dh$retired(CallbackInfo ci)
	{ VanillaSectionCoverage.INSTANCE.set(this, this.posX >> 4, this.posY >> 4, this.posZ >> 4, false); }
}

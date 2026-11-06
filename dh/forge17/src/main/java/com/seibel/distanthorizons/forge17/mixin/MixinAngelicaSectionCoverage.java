package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.core.render.VanillaSectionCoverage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Angelica publishes setInfo only when a mesh is uploaded, or a section is known empty. */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.RenderSection", remap = false)
public abstract class MixinAngelicaSectionCoverage
{
	@Inject(method = "setInfo", at = @At("RETURN"))
	private void dh$uploaded(CallbackInfoReturnable<Boolean> cir)
	{
		RenderSection section = (RenderSection)(Object)this;
		World world = Minecraft.getMinecraft().theWorld;
		boolean ready = section.isBuilt() && !section.isDisposed() && world != null
			&& !world.getChunkFromChunkCoords(section.getChunkX(), section.getChunkZ()).isEmpty();
		VanillaSectionCoverage.INSTANCE.set(this, section.getChunkX(), section.getChunkY(), section.getChunkZ(), ready);
	}

	@Inject(method = "delete", at = @At("HEAD"))
	private void dh$deleted(CallbackInfo ci)
	{
		RenderSection section = (RenderSection)(Object)this;
		VanillaSectionCoverage.INSTANCE.set(this, section.getChunkX(), section.getChunkY(), section.getChunkZ(), false);
	}
}

/* Adapted from R-Besson/DistantHorizonsRetro's MixinTransformPatcher, GNU LGPL v3. */
package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.forge17.modAccessor.ShaderPackFoliage;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

@Pseudo
@Mixin(targets = "net.coderbot.iris.pipeline.transform.TransformPatcher", remap = false)
public class MixinFoliageTransformPatcher
{
	@Inject(method = "patchDHTerrain", at = @At("RETURN"), cancellable = true, require = 0)
	private static void dh$foliage(CallbackInfoReturnable<Map<PatchShaderType, String>> cir)
	{
		cir.setReturnValue(com.seibel.distanthorizons.forge17.modAccessor.ShaderPackLodPresentation.patch(ShaderPackFoliage.patch(cir.getReturnValue())));
	}
}

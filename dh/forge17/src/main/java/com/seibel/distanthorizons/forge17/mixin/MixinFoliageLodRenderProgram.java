/* Foliage uniform hook adapted from R-Besson/DistantHorizonsRetro, GNU LGPL v3. */
package com.seibel.distanthorizons.forge17.mixin;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.forge17.modAccessor.ShaderPackFoliage;
import com.seibel.distanthorizons.common.render.openGl.terrain.GlVanillaSectionCoverage;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.llamalad7.mixinextras.sugar.Local;
import net.coderbot.iris.gl.program.ProgramSamplers;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.coderbot.iris.compat.dh.IrisLodRenderProgram", remap = false)
public abstract class MixinFoliageLodRenderProgram
{
	@Shadow @Final private int id;
	@Unique private boolean dh$sampler;
	@Unique private boolean dh$coverageSampler, dh$shadow;
	@Unique private int dh$coverageOffset = -1, dh$coverageEnabled = -1;
	@Unique private int dh$distance = -1;
	@Unique private int dh$enabled = -1;
    @Unique private int dh$presentationMin = -1, dh$presentationMax = -1, dh$clip = -1;

	@Redirect(method = "<init>", at = @At(value = "INVOKE",
		target = "Lnet/coderbot/iris/gl/program/ProgramSamplers$Builder;build()Lnet/coderbot/iris/gl/program/ProgramSamplers;"), require = 0)
	private ProgramSamplers dh$registerSampler(ProgramSamplers.Builder builder)
	{
		this.dh$sampler = ShaderPackFoliage.registerSampler(builder);
		this.dh$coverageSampler = ShaderPackFoliage.registerSampler(builder, "dhc_Sections", GlVanillaSectionCoverage.INSTANCE::getTextureId);
		return builder.build();
	}

	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void dh$findUniforms(CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) boolean isShadowPass)
	{
		this.dh$shadow = isShadowPass;
		this.dh$coverageOffset = GL20.glGetUniformLocation(this.id, "dhc_Offset");
		this.dh$coverageEnabled = GL20.glGetUniformLocation(this.id, "dhc_Enabled");
		this.dh$presentationMin = GL20.glGetUniformLocation(this.id, "dhp_Min");
        this.dh$presentationMax = GL20.glGetUniformLocation(this.id, "dhp_Max");
        this.dh$clip = GL20.glGetUniformLocation(this.id, "clipDistance");
        this.dh$distance = GL20.glGetUniformLocation(this.id, "dhf_Distance");
		this.dh$enabled = GL20.glGetUniformLocation(this.id, "dhf_Enabled");
	}

	@Inject(method = "fillUniformData", at = @At("HEAD"), require = 0)
	private void dh$prepareCoverage(CallbackInfo ci)
	{
		// Update before Iris resolves the dynamic sampler, including the first frame.
		if (!this.dh$shadow) com.seibel.distanthorizons.forge17.RenderHelper.updateVanillaSectionVisibility();
		GlVanillaSectionCoverage.INSTANCE.update(MinecraftRenderWrapper.INSTANCE.getCameraExactPosition());
	}

	@Inject(method = "fillUniformData", at = @At("TAIL"), require = 0)
	private void dh$fillFoliage(CallbackInfo ci)
	{
		GlVanillaSectionCoverage coverage = GlVanillaSectionCoverage.INSTANCE;
		boolean coverageAvailable = this.dh$coverageSampler && this.dh$coverageEnabled >= 0;
		GL20.glUniform1i(this.dh$coverageEnabled, coverageAvailable && !this.dh$shadow && !Config.Client.Advanced.Debugging.lodOnlyMode.get() ? 1 : 0);
		GL20.glUniform3f(this.dh$coverageOffset, coverage.offsetX, coverage.offsetY, coverage.offsetZ);
		// Programs without the ownership varying retain Iris's normal near clipping.
		if (coverageAvailable) GL20.glUniform1f(this.dh$clip, 0.0f);
        GL20.glUniform1f(this.dh$distance, Config.Client.Advanced.Graphics.Foliage.vegetationRenderDistance.get() * 16.0f);
		GL20.glUniform1i(this.dh$enabled, this.dh$sampler && Config.Client.Advanced.Graphics.Texture.enableTexturedLods.get()
			&& DhApiRenderProxy.getDhBlockRatioAtlasTextureGlId > 0 ? 1 : 0);
	}
    @Inject(method = "setModelPos", at = @At("TAIL"), require = 0)
    private void dh$present(CallbackInfo ci)
    {
        GL20.glUniform1f(this.dh$presentationMin, com.seibel.distanthorizons.core.render.LodPresentation.shaderMin);
        GL20.glUniform1f(this.dh$presentationMax, com.seibel.distanthorizons.core.render.LodPresentation.shaderMax);
    }

}

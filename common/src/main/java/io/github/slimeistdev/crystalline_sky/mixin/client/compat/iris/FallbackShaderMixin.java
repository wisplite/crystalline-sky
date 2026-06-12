package io.github.slimeistdev.crystalline_sky.mixin.client.compat.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.slimeistdev.crystalline_sky.annotation.mixin.ConditionalMixin;
import io.github.slimeistdev.crystalline_sky.compat.Mods;
import io.github.slimeistdev.crystalline_sky.util.SharedRenderVariables;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.programs.FallbackShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@ConditionalMixin(mods = Mods.IRIS)
@Mixin(FallbackShader.class)
public class FallbackShaderMixin {
	@WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;bind()V"))
	private void skipBind(GlFramebuffer instance, Operation<Void> original) {
		if (!SharedRenderVariables.shouldBlockIrisShaderFramebufferBind()) {
			original.call(instance);
		}
	}
}

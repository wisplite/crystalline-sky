package io.github.slimeistdev.crystalline_sky.compat.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.slimeistdev.crystalline_sky.mixin.client.compat.iris.IrisRenderingPipelineAccessor;
import io.github.slimeistdev.crystalline_sky.mixin_ducks.client.IrisRenderingPipeline_Duck;
import io.github.slimeistdev.crystalline_sky.mixin_ducks.client.RenderTarget_Duck;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.vertices.ImmediateState;

@Environment(EnvType.CLIENT)
public class IrisHelpers {
	public static boolean isShaderpackPipelineActive() {
		return Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline;
	}

	public static void pushVanillaShaders() {
		ImmediateState.bypass = true;
	}

	public static void popVanillaShaders() {
		ImmediateState.bypass = false;
	}

	public static void resetRenderingPhase() {
		var pipeline = Iris.getPipelineManager().getPipelineNullable();
		if (pipeline instanceof IrisRenderingPipeline irisPipeline) {
			irisPipeline.setPhase(WorldRenderingPhase.NONE);
		}
	}

	public static boolean copySkyToBuffer(RenderTarget skyBuffer) {
		var pipeline = Iris.getPipelineManager().getPipelineNullable();
		if (!(pipeline instanceof IrisRenderingPipeline irisPipeline)) return false;

		IrisRenderingPipelineAccessor pipelineAccessor = ((IrisRenderingPipelineAccessor) irisPipeline);
		RenderTargets targets = pipelineAccessor.crystalline_sky$getRenderTargets();
		var defaultTarget = targets.get(pipelineAccessor.crystalline_sky$getPackDirectives().getFallbackTex());
		((RenderTarget_Duck) skyBuffer).crystalline_sky$copyColorFrom(
			((IrisRenderingPipeline_Duck) irisPipeline)::crystalline_sky$bindDefaultForRead,
			irisPipeline::bindDefault,
			defaultTarget.getWidth(), defaultTarget.getHeight()
		);

		return true;
	}
}

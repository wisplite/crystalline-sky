package io.github.slimeistdev.crystalline_sky.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.slimeistdev.crystalline_sky.compat.Mods;
import io.github.slimeistdev.crystalline_sky.compat.iris.IrisHelpers;
import io.github.slimeistdev.crystalline_sky.mixin_ducks.client.LevelRenderer_Duck;
import io.github.slimeistdev.crystalline_sky.mixin_ducks.client.RenderTarget_Duck;
import io.github.slimeistdev.crystalline_sky.registry.client.CrystallineRenderTypes;
import io.github.slimeistdev.crystalline_sky.util.SharedRenderVariables;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, remap = false)
public abstract class LevelRendererMixin implements LevelRenderer_Duck {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract boolean doesMobEffectBlockSky(Camera camera);

	@Shadow
	protected abstract void renderSectionLayer(RenderType renderLayer, double x, double y, double z, Matrix4f matrix4f, Matrix4f positionMatrix);

	@Shadow
	private @Nullable RenderTarget cloudsTarget;

	@Shadow
	public abstract void renderClouds(PoseStack matrices, Matrix4f matrix4f, Matrix4f matrix4f2, float tickDelta, double cameraX, double cameraY, double cameraZ);

	@Shadow
	public abstract void renderSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup);

	@Unique
	@Nullable
	private RenderTarget crystalline_sky$skyBuffer;

	@Override
	public RenderTarget crystalline_sky$getSkyFramebuffer() {
		if (crystalline_sky$skyBuffer == null) {
			var fb = minecraft.getMainRenderTarget();
			crystalline_sky$skyBuffer = new TextureTarget(fb.width, fb.height, true, Minecraft.ON_OSX);
			crystalline_sky$configureSkyBufferTexture();
		}
		return crystalline_sky$skyBuffer;
	}

	@Inject(
		method = "renderLevel",
		at = @At(
			value = "INVOKE_STRING",
			target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
			args = "ldc=clear",
			shift = At.Shift.AFTER
		)
	)
	private void setupSkyBuffer(DeltaTracker tickCounter, boolean renderBlockOutline, Camera camera,
								GameRenderer gameRenderer, LightTexture lightmapTextureManager,
								Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
		crystalline_sky$getSkyFramebuffer();
	}

	@Inject(method = "resize", at = @At("RETURN"))
	private void resizeSkyBuffer(int width, int height, CallbackInfo ci) {
		if (crystalline_sky$skyBuffer != null) {
			crystalline_sky$skyBuffer.resize(width, height, Minecraft.ON_OSX);
			crystalline_sky$configureSkyBufferTexture();
		}
	}

	@Inject(method = "close", at = @At("RETURN"))
	private void closeSkyBuffer(CallbackInfo ci) {
		if (crystalline_sky$skyBuffer != null) {
			crystalline_sky$skyBuffer.destroyBuffers();
			crystalline_sky$skyBuffer = null;
		}
	}

	@Inject(
		method = "renderLevel",
		at = @At(
			value = "INVOKE_STRING",
			target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
			args = "ldc=terrain_setup"
		)
	)
	private void copyAfterSky(DeltaTracker tickCounter, boolean renderBlockOutline, Camera camera,
							  GameRenderer gameRenderer, LightTexture lightmapTextureManager,
							  Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
		float partialTick = tickCounter.getGameTimeDeltaPartialTick(false);
		if (crystalline_sky$isBelowHorizon(partialTick)
			|| Mods.IRIS.runIfInstalled(() -> IrisHelpers::isShaderpackPipelineActive).orElse(false)) {
			var skyBuffer = crystalline_sky$getSkyFramebuffer();
			skyBuffer.clear(Minecraft.ON_OSX);
			crystalline_sky$renderSkyBuffer(skyBuffer, tickCounter, camera, gameRenderer, matrix4f, matrix4f2);
		} else {
			var skyBuffer = crystalline_sky$getSkyFramebuffer();
			skyBuffer.clear(Minecraft.ON_OSX);
			((RenderTarget_Duck) skyBuffer).crystalline_sky$copyColorFrom(minecraft.getMainRenderTarget());
		}

		// Iris shader packs replace cloud rendering; baking them into the sky buffer causes artifacts.
		boolean irisShaderpackActive = Mods.IRIS.runIfInstalled(() -> IrisHelpers::isShaderpackPipelineActive).orElse(false);

		// render clouds
		CloudStatus cloudRenderMode = this.minecraft.options.getCloudsType();
		if (cloudRenderMode != CloudStatus.OFF && !irisShaderpackActive && !this.doesMobEffectBlockSky(camera)) {
			float f = tickCounter.getGameTimeDeltaPartialTick(false);
			Vec3 vec3d = camera.getPosition();
			double cx = vec3d.x();
			double cy = vec3d.y();
			double cz = vec3d.z();

			var skyBuffer = crystalline_sky$getSkyFramebuffer();
			var cloudsFramebuffer0 = this.cloudsTarget;
			this.cloudsTarget = skyBuffer;
			cloudsTarget.bindWrite(false);
			SharedRenderVariables.pushBlockIrisShaderFramebufferBind();
			this.renderClouds(new PoseStack(), matrix4f, matrix4f2, f, cx, cy, cz);
			SharedRenderVariables.popBlockIrisShaderFramebufferBind();
			minecraft.getMainRenderTarget().bindWrite(false);
			this.cloudsTarget = cloudsFramebuffer0;
		}
	}

	// after solid, cutoutMipped, and cutout
	@WrapOperation(
		method = "renderLevel",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
			ordinal = 2
		)
	)
	private void renderCrystallineSky(LevelRenderer instance, RenderType renderLayer, double x, double y, double z,
									  Matrix4f matrix4f, Matrix4f positionMatrix, Operation<Void> original) {
		original.call(instance, renderLayer, x, y, z, matrix4f, positionMatrix);
		renderSectionLayer(CrystallineRenderTypes.SKY, x, y, z, matrix4f, positionMatrix);
		renderSectionLayer(CrystallineRenderTypes.SKYBOX, x, y, z, matrix4f, positionMatrix);
	}

	@Unique
	private void crystalline_sky$configureSkyBufferTexture() {
		if (crystalline_sky$skyBuffer == null) {
			return;
		}

		RenderSystem.assertOnRenderThreadOrInit();
		GlStateManager._bindTexture(crystalline_sky$skyBuffer.getColorTextureId());
		GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_WRAP_S, GlConst.GL_CLAMP_TO_EDGE);
		GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_WRAP_T, GlConst.GL_CLAMP_TO_EDGE);
	}

	@Unique
	private boolean crystalline_sky$isBelowHorizon(float partialTick) {
		var level = minecraft.level;
		var player = minecraft.player;
		if (level == null || player == null) {
			return false;
		}

		double eyeY = player.getEyePosition(partialTick).y;
		double horizon = level.getLevelData().getHorizonHeight(level);
		return eyeY < horizon;
	}

	@Unique
	private void crystalline_sky$renderSkyBuffer(
		RenderTarget skyBuffer,
		DeltaTracker tickCounter,
		Camera camera,
		GameRenderer gameRenderer,
		Matrix4f frustumMatrix,
		Matrix4f projectionMatrix
	) {
		float partialTick = tickCounter.getGameTimeDeltaPartialTick(false);
		Vec3 cameraPos = camera.getPosition();
		float renderDistance = gameRenderer.getRenderDistance();
		boolean isFoggy = minecraft.level.effects().isFoggyAt(Mth.floor(cameraPos.x), Mth.floor(cameraPos.y))
			|| minecraft.gui.getBossOverlay().shouldCreateWorldFog();

		skyBuffer.bindWrite(false);
		RenderSystem.viewport(0, 0, skyBuffer.width, skyBuffer.height);
		SharedRenderVariables.pushCapturingSkyBuffer();
		boolean belowHorizon = crystalline_sky$isBelowHorizon(partialTick);
		if (belowHorizon) {
			SharedRenderVariables.pushSkipVoidSky();
		}
		SharedRenderVariables.pushBlockIrisShaderFramebufferBind();
		Mods.IRIS.executeIfInstalled(() -> IrisHelpers::pushVanillaShaders);
		try {
			FogRenderer.setupColor(
				camera,
				partialTick,
				minecraft.level,
				minecraft.options.getEffectiveRenderDistance(),
				gameRenderer.getDarkenWorldAmount(partialTick)
			);
			FogRenderer.levelFogColor();
			RenderSystem.clear(16640, Minecraft.ON_OSX);
			FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, renderDistance, isFoggy, partialTick);
			RenderSystem.setShader(GameRenderer::getPositionShader);
			this.renderSky(
				frustumMatrix,
				projectionMatrix,
				partialTick,
				camera,
				isFoggy,
				() -> FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, renderDistance, isFoggy, partialTick)
			);
		} finally {
			Mods.IRIS.executeIfInstalled(() -> IrisHelpers::popVanillaShaders);
			SharedRenderVariables.popBlockIrisShaderFramebufferBind();
			if (belowHorizon) {
				SharedRenderVariables.popSkipVoidSky();
			}
			SharedRenderVariables.popCapturingSkyBuffer();
			Mods.IRIS.executeIfInstalled(() -> IrisHelpers::resetRenderingPhase);
			var main = minecraft.getMainRenderTarget();
			main.bindWrite(false);
			RenderSystem.viewport(0, 0, main.width, main.height);
		}
	}
}

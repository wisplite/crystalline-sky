package io.github.slimeistdev.crystalline_sky.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.slimeistdev.crystalline_sky.util.SharedRenderVariables;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LevelRenderer.class, remap = false)
public class LevelRendererRenderSkyMixin {
	@WrapOperation(
		method = "renderSky",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel$ClientLevelData;getHorizonHeight(Lnet/minecraft/world/level/LevelHeightAccessor;)D"
		)
	)
	private double crystalline_sky$skipVoidSkyWhenCapturing(
		ClientLevel.ClientLevelData levelData,
		LevelHeightAccessor level,
		Operation<Double> original
	) {
		if (SharedRenderVariables.shouldSkipVoidSky()) {
			return Double.NEGATIVE_INFINITY;
		}

		return original.call(levelData, level);
	}
}

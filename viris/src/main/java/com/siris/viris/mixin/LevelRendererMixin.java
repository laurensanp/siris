package com.siris.viris.mixin;

import com.siris.viris.VoxyFrame;
import com.siris.viris.Viris;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Voxy's LODs go into the main target right before the opaque world pass ("Solid"/"Main") opens.
 * Applied after Voxy's LevelRenderer mixins (higher priority) so that
 * {@link com.siris.viris.VirisMixinPlugin#postApply} also sees Voxy's merged Iris hook.
 */
@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {

    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"))
    private void viris$drawLods(CallbackInfo ci, @Local RenderTarget mainTarget) {
        if (Viris.isVulkan()) {
            VoxyFrame.beforeOpaquePass(mainTarget);
        }
    }
}

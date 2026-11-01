package com.siris.voxyvulkan.mixin;

import com.siris.voxyvulkan.VoxyFrame;
import com.siris.voxyvulkan.VoxyVulkan;
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
 * {@link com.siris.voxyvulkan.VoxyVulkanMixinPlugin#postApply} also sees Voxy's merged Iris hook.
 */
@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {

    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"))
    private void voxyvulkan$drawLods(CallbackInfo ci, @Local RenderTarget mainTarget) {
        if (VoxyVulkan.isVulkan()) {
            VoxyFrame.beforeOpaquePass(mainTarget);
        }
    }
}

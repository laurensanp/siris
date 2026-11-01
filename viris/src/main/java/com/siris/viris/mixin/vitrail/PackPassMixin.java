package com.siris.viris.mixin.vitrail;

import com.siris.viris.VoxyFrame;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The pack's full screen passes read Voxy's depth through vxDepthTexOpaque / vxDepthTexTrans. */
@Mixin(targets = "dev.vitrail.render.PackPass", remap = false)
public class PackPassMixin {

    @WrapOperation(method = "bindSamplers", at = @At(value = "INVOKE",
        target = "Ldev/vitrail/render/GraphicsApi;bindTexture(Lcom/mojang/renderpearl/api/commands/RenderPass;Ljava/lang/String;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;)V"))
    private void viris$voxyDepth(RenderPass pass, String name, GpuTextureView view, GpuSampler sampler, Operation<Void> original) {
        original.call(pass, name, name.startsWith("vxDepthTex") ? VoxyFrame.vxDepthView(name) : view, sampler);
    }
}

package com.siris.voxyvulkan.mixin;

import com.siris.voxyvulkan.GlContext;
import com.siris.voxyvulkan.Interop;
import com.siris.voxyvulkan.VoxyVulkan;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On Vulkan the OpenGL context for Voxy is made current before Voxy's own start (its RenderSystem hook,
 * order 900), which then queries GL capabilities and creates its buffers on it as usual.
 */
@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Inject(method = "initRenderer", order = 800, remap = false, at = @At("RETURN"))
    private static void voxyvulkan$createGlContext(GpuDevice device, CallbackInfo ci) {
        if (!VoxyVulkan.isVulkan()) {
            return;
        }
        if (!Interop.supported()) {
            VoxyVulkan.LOGGER.error("Voxy Vulkan: the Vulkan device can't share memory with OpenGL, Voxy stays off");
            return;
        }
        GlContext.create();
    }
}

package com.siris.voxyvulkan.mixin;

import com.siris.voxyvulkan.VoxyFrame;
import com.siris.voxyvulkan.VoxyVulkan;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** On Vulkan Voxy reads the lightmap from a shared copy the game fills every frame. */
@Mixin(value = LightMapHelper.class, remap = false)
public class LightMapHelperMixin {

    @Inject(method = "getLightmapTextureId", at = @At("HEAD"), cancellable = true)
    private static void voxyvulkan$sharedLightmap(CallbackInfoReturnable<Integer> cir) {
        if (VoxyVulkan.isVulkan()) {
            cir.setReturnValue(VoxyFrame.lightmapGlTexture());
        }
    }
}

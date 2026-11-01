package com.siris.viris.mixin;

import com.siris.viris.VoxyFrame;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The frame's last use of Voxy's shared depth is done: OpenGL may reuse the images once this submit ran. */
@Mixin(VulkanCommandEncoder.class)
public class VulkanCommandEncoderMixin {

    @Inject(method = "submit", at = @At("HEAD"))
    private void viris$releaseShared(CallbackInfo ci) {
        VoxyFrame.beforeSubmit();
    }
}

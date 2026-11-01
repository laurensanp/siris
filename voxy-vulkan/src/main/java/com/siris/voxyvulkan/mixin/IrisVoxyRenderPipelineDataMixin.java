package com.siris.voxyvulkan.mixin;

import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty: lets {@link com.siris.voxyvulkan.VoxyVulkanMixinPlugin#postApply} take the Iris-only builder methods
 * out of Voxy's pack pipeline data class when it loads.
 */
@Mixin(value = IrisVoxyRenderPipelineData.class, remap = false)
public class IrisVoxyRenderPipelineDataMixin {
}

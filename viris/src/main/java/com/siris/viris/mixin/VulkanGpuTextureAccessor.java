package com.siris.viris.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VulkanGpuTexture.class)
public interface VulkanGpuTextureAccessor {

    @Accessor("vmaAllocation")
    long viris$vmaAllocation();
}

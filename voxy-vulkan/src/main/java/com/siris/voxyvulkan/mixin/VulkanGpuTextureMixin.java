package com.siris.voxyvulkan.mixin;

import com.siris.voxyvulkan.Interop;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shared textures ({@link Interop#createTexture}) get exportable dedicated memory. */
@Mixin(VulkanGpuTexture.class)
public class VulkanGpuTextureMixin {

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/util/vma/Vma;vmaCreateImage(JLorg/lwjgl/vulkan/VkImageCreateInfo;Lorg/lwjgl/util/vma/VmaAllocationCreateInfo;Ljava/nio/LongBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/util/vma/VmaAllocationInfo;)I"))
    private static int voxyvulkan$exportable(long vma, VkImageCreateInfo image, VmaAllocationCreateInfo allocation,
        LongBuffer imageHandle, PointerBuffer allocationHandle, VmaAllocationInfo info, Operation<Integer> original) {
        if (!Interop.exportingNext()) {
            return original.call(vma, image, allocation, imageHandle, allocationHandle, info);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Interop.prepareExport(vma, image, allocation, stack);
            return original.call(vma, image, allocation, imageHandle, allocationHandle, info);
        }
    }
}

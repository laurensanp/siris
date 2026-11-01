package com.siris.viris.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Empty: applied after Voxy's DefaultChunkRenderer mixin (higher priority) so that
 * {@link com.siris.viris.VirisMixinPlugin#postApply} sees Voxy's merged draw hook, which reads
 * the game's targets as GL textures; on Vulkan {@link com.siris.viris.VoxyFrame} draws instead.
 */
@Mixin(value = DefaultChunkRenderer.class, priority = 2000, remap = false)
public class DefaultChunkRendererMixin {
}

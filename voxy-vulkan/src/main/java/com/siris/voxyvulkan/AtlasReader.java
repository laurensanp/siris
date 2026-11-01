package com.siris.voxyvulkan;

import com.siris.voxyvulkan.mixin.SpriteContentsAccessor;
import com.siris.voxyvulkan.mixin.TextureAtlasAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.system.MemoryUtil;

/**
 * The block atlas as Voxy reads it back from OpenGL (glGetTextureImage, RGBA bytes into an int array),
 * put together on the CPU from the sprites' own images: on Vulkan the atlas isn't a GL texture.
 * Animated sprites give their first frame.
 */
public final class AtlasReader {

    private static boolean logged;

    private AtlasReader() {
    }

    public static int[] read(int width, int height) {
        int[] pixels = new int[width * height];
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        int copied = 0;
        int missing = 0;
        for (TextureAtlasSprite sprite : ((TextureAtlasAccessor) atlas).voxyvulkan$sprites()) {
            SpriteContents contents = sprite.contents();
            NativeImage image = ((SpriteContentsAccessor) contents).voxyvulkan$getOriginalImage();
            if (image == null || image.getPointer() == 0L) {
                missing++;
                continue;
            }
            copied++;
            int spriteWidth = Math.min(contents.width(), image.getWidth());
            int spriteHeight = Math.min(contents.height(), image.getHeight());
            // The texels start after the sprite's padding (getX/getY include it): where u0/v0 point.
            int left = Math.round(sprite.getU0() * width);
            int top = Math.round(sprite.getV0() * height);
            for (int y = 0; y < spriteHeight; y++) {
                int atlasY = top + y;
                if (atlasY < 0 || atlasY >= height) {
                    continue;
                }
                for (int x = 0; x < spriteWidth; x++) {
                    int atlasX = left + x;
                    if (atlasX < 0 || atlasX >= width) {
                        continue;
                    }
                    // RGBA bytes read as a little-endian int, like glGetTextureImage into int[].
                    pixels[atlasX + atlasY * width] = MemoryUtil.memGetInt(image.getPointer() + 4L * (x + (long) y * image.getWidth()));
                }
            }
        }
        if (!logged) {
            logged = true;
            VoxyVulkan.LOGGER.info("Voxy Vulkan: block atlas {}x{} from {} sprites ({} without image)", width, height,
                copied, missing);
        }
        return pixels;
    }
}

package com.siris.voxyvulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import java.util.Optional;
import java.util.OptionalDouble;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.GL45C;

/**
 * One frame of Voxy on Vulkan. Right before the game's opaque world pass, Voxy's own renderer runs on
 * the OpenGL context into two shared images (colour with alpha, depth in the game's projection), as it
 * would into the game's targets on OpenGL, but over an empty picture: the game's opaque world isn't drawn
 * yet. Vulkan waits for it and writes the LODs into the main target, colour blended the way Voxy's final
 * blit blends and depth written; the terrain and everything after it then draw over them by depth, which
 * is where Voxy's stencil would have let the LODs through.
 *
 * <p>Order across the two APIs, with binary semaphores per image set: GL draws the frame and signals
 * glDone; Vulkan waits for it, composites, copies the game's lightmap into the set (Voxy reads it the next
 * time it draws into this set) and signals vkDone; GL waits for vkDone before it draws into the set again.
 * The texture lists of GL's waits and signals hand the images between the APIs; without them (a GL fence
 * and a CPU wait instead) the driver serialised the two and hung with DLSS. Timeline semaphores didn't
 * work either: NVIDIA 617 passed only GL's first signal to Vulkan.
 */
public final class VoxyFrame {

    private static final String NAMESPACE = "voxy-vulkan";
    private static final int LIGHTMAP_SIZE = 16;
    /** Dev option -Dvoxyvulkan.timing=true: logs the render thread's time in Voxy and a GL finish (stalls the GPU queue). */
    private static final boolean TIMING = Boolean.getBoolean("voxyvulkan.timing");
    private static int timingFrames;
    private static long timingCpu;
    private static long timingGpu;

    private static final String VERTEX_SHADER = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        void main() {
            vec2 p = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    // LodDepth is the clear value where Voxy drew nothing; there the pixel stays as it is. RenderPearl's
    // Vulkan images keep OpenGL's row order (bottom row first), so pixels are read where GL wrote them.
    private static final String COMPOSITE_SHADER = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        uniform sampler2D LodColor;
        uniform sampler2D LodDepth;
        layout(location = 0) out vec4 fragColor;
        void main() {
            ivec2 p = ivec2(gl_FragCoord.xy);
            float depth = texelFetch(LodDepth, p, 0).r;
            if (depth == CLEAR_DEPTH) {
                discard;
            }
            fragColor = texelFetch(LodColor, p, 0);
            gl_FragDepth = depth;
        }
        """;

    private static final String LIGHTMAP_SHADER = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        uniform sampler2D Lightmap;
        layout(location = 0) out vec4 fragColor;
        void main() {
            ivec2 p = ivec2(gl_FragCoord.xy);
            fragColor = texelFetch(Lightmap, p, 0);
        }
        """;

    private static final ShaderSource SHADERS = new ShaderSource() {
        @Override
        public String getShader(Identifier id, ShaderType type) {
            return switch (id.toString()) {
                case NAMESPACE + ":fullscreen" -> VERTEX_SHADER;
                case NAMESPACE + ":composite" -> COMPOSITE_SHADER;
                case NAMESPACE + ":lightmap" -> LIGHTMAP_SHADER;
                default -> null;
            };
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            return null;
        }

        @Override
        public void close() {
        }
    };

    // Sodium's matrices, camera and fog of this frame (SodiumWorldRendererMixin), as Voxy takes them.
    private static Matrix4fc projection;
    private static Matrix4fc modelView;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static FogParameters fog;

    /** Image sets used in turn, so GL draws frame N while Vulkan may still read frames N-1 and N-2. */
    private static final int SETS = 3;
    private static final ImageSet[] sets = new ImageSet[SETS];
    private static int frame;
    private static ImageSet current;
    private static CompiledRenderPipeline compositePipeline;
    private static GpuFormat compositeFormat;
    private static CompiledRenderPipeline lightmapPipeline;
    private static boolean failed;
    private static boolean loggedFirstFrame;

    private VoxyFrame() {
    }

    public static void capture(Matrix4fc projectionMatrix, Matrix4fc modelViewMatrix, double x, double y, double z,
        FogParameters fogParameters) {
        projection = new Matrix4f(projectionMatrix);
        modelView = new Matrix4f(modelViewMatrix);
        cameraX = x;
        cameraY = y;
        cameraZ = z;
        fog = fogParameters;
    }

    /** Shared images of one frame. */
    private static final class ImageSet {
        Interop.SharedTexture color;
        Interop.SharedTexture depth;
        /** The game's lightmap, copied in when this set was last composited; Voxy reads it when it next draws into the set. */
        final Interop.SharedTexture lightmap = Interop.createTexture("Voxy lightmap", GpuTexture.USAGE_RENDER_ATTACHMENT
            | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, GL45C.GL_RGBA8, LIGHTMAP_SIZE, LIGHTMAP_SIZE);
        /** GL signals it once Voxy drew into the set, Vulkan waits for it before the composite. */
        final long[] glDone = Interop.createSemaphore();
        /** Vulkan signals it after the composite, GL waits for it before it draws into the set again. */
        final long[] vkDone = Interop.createSemaphore();
        /** Vulkan signalled vkDone and GL hasn't waited for it yet. */
        boolean vkDonePending;

        int[] glTextures() {
            return new int[] {color.glTexture(), depth.glTexture(), lightmap.glTexture()};
        }

        void waitForVulkan() {
            if (vkDonePending) {
                EXTSemaphore.glWaitSemaphoreEXT((int) vkDone[1], new int[0], glTextures(), LAYOUTS);
                vkDonePending = false;
            }
        }

        void resize(int width, int height) {
            if (color != null && color.texture().getWidth(0) == width && color.texture().getHeight(0) == height) {
                return;
            }
            if (color != null) {
                // GL is done with the old images before they go; Vulkan frames still in flight keep them
                // until RenderPearl destroys them.
                waitForVulkan();
                GL45C.glFinish();
                color.close();
                depth.close();
            }
            int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
            color = Interop.createTexture("Voxy colour", usage, GpuFormat.RGBA8_UNORM, GL45C.GL_RGBA8, width, height);
            depth = Interop.createTexture("Voxy depth", usage, GpuFormat.D32_FLOAT, GL45C.GL_DEPTH_COMPONENT32F, width, height);
        }
    }

    private static final long ALL_COMMANDS = 0x10000L;
    private static final int[] LAYOUTS = {EXTSemaphore.GL_LAYOUT_GENERAL_EXT, EXTSemaphore.GL_LAYOUT_GENERAL_EXT,
        EXTSemaphore.GL_LAYOUT_GENERAL_EXT};

    /** Voxy's lightmap texture on the GL side (LightMapHelperMixin): the game's lightmap of SETS frames ago. */
    public static int lightmapGlTexture() {
        if (current == null) {
            current = new ImageSet();
            sets[0] = current;
        }
        return current.lightmap.glTexture();
    }

    /** Called right before the game opens its opaque world pass on {@code main}. */
    public static void beforeOpaquePass(RenderTarget main) {
        if (failed || !GlContext.ready() || projection == null || ShaderPacks.active()) {
            return;
        }
        VoxyRenderSystem system = IVoxyRenderSystemHolder.getNullable();
        if (system == null) {
            return;
        }
        try {
            render(system, main);
        } catch (RuntimeException e) {
            failed = true;
            VoxyVulkan.LOGGER.error("Voxy Vulkan: drawing Voxy failed, LODs stay off for this session", e);
        }
    }

    private static void render(VoxyRenderSystem system, RenderTarget main) {
        int width = main.width;
        int height = main.height;
        ensureResources(width, height, main.getColorTexture().getFormat());
        ImageSet set = current;
        set.waitForVulkan();
        GL45C.glClearTexImage(set.color.glTexture(), 0, GL45C.GL_RGBA, GL45C.GL_FLOAT, new float[] {0, 0, 0, 0});
        GL45C.glClearTexImage(set.depth.glTexture(), 0, GL45C.GL_DEPTH_COMPONENT, GL45C.GL_FLOAT, new float[] {clearDepth()});
        long start = System.nanoTime();
        Viewport<?> viewport = system.setupViewport(projection, modelView, fog, width, height, cameraX, cameraY, cameraZ);
        if (viewport != null) {
            system.renderOpaque(viewport, set.depth.glTexture(), set.color.glTexture());
        }
        long drawn = System.nanoTime();
        EXTSemaphore.glSignalSemaphoreEXT((int) set.glDone[1], new int[0], set.glTextures(), LAYOUTS);
        GL45C.glFlush();
        if (TIMING) {
            GL45C.glFinish();
            long finished = System.nanoTime();
            timingFrames++;
            timingCpu += drawn - start;
            timingGpu += finished - drawn;
            if (timingFrames == 50) {
                // GL_NVX_gpu_memory_info: free video memory (kB) and evictions so far.
                VoxyVulkan.LOGGER.info("Voxy Vulkan timing: Voxy calls {} ms, GL finish {} ms per frame, VRAM free {} MB, evictions {}",
                    String.format("%.2f", timingCpu / 50e6), String.format("%.2f", timingGpu / 50e6),
                    GL45C.glGetInteger(0x9049) / 1024, GL45C.glGetInteger(0x904A));
                timingFrames = 0;
                timingCpu = 0;
                timingGpu = 0;
            }
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        VulkanCommandEncoder vulkan = (VulkanCommandEncoder) ((FrontendCommandEncoder) encoder).backend();
        vulkan.waitSemaphore(set.glDone[0], 0L, ALL_COMMANDS);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = encoder.createRenderPass(() -> "Voxy LODs", main.getColorTextureView(), Optional.empty(),
            main.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(compositePipeline);
            pass.setUniform("LodColor", set.color.view(), nearest);
            pass.setUniform("LodDepth", set.depth.view(), nearest);
            pass.draw(3, 1, 0, 0);
        }
        try (RenderPass pass = encoder.createRenderPass(() -> "Voxy lightmap", set.lightmap.view(), Optional.empty())) {
            pass.setPipeline(lightmapPipeline);
            pass.setUniform("Lightmap", Minecraft.getInstance().gameRenderer.levelLightmap(), nearest);
            pass.draw(3, 1, 0, 0);
        }
        vulkan.signalSemaphore(set.vkDone[0], 0L, ALL_COMMANDS);
        set.vkDonePending = true;
        frame = (frame + 1) % SETS;
        if (sets[frame] == null) {
            sets[frame] = new ImageSet();
        }
        current = sets[frame];
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            VoxyVulkan.LOGGER.info("Voxy Vulkan: Voxy draws through OpenGL into shared images ({}x{})", width, height);
        }
    }

    /** Reverse-Z (the game's default on 26.3) clears depth to 0, else 1; Voxy decides the same way. */
    private static float clearDepth() {
        return DepthStencilState.DEFAULT.depthTest() == CompareOp.GREATER_THAN_OR_EQUAL ? 0.0F : 1.0F;
    }

    private static void ensureResources(int width, int height, GpuFormat targetFormat) {
        lightmapGlTexture();
        current.resize(width, height);
        if (compositePipeline == null || compositeFormat != targetFormat) {
            compositeFormat = targetFormat;
            RenderPipeline.Builder composite = fullscreen("composite",
                BindGroupLayout.builder().withUniform("LodColor", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("LodDepth", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withShaderDefine("CLEAR_DEPTH", clearDepth())
                .withColorTargetState(0, new ColorTargetState(
                    Optional.of(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)), targetFormat,
                    ColorTargetState.WRITE_ALL))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true));
            compositePipeline = compile(composite.build());
        }
        if (lightmapPipeline == null) {
            RenderPipeline.Builder copy = fullscreen("lightmap",
                BindGroupLayout.builder().withUniform("Lightmap", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withColorTargetState(0, new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM,
                    ColorTargetState.WRITE_ALL));
            lightmapPipeline = compile(copy.build());
        }
    }

    private static RenderPipeline.Builder fullscreen(String name, BindGroupLayout layout) {
        return RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/" + name))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "fullscreen"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, name))
            .withBindGroupLayout(layout)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false);
    }

    private static CompiledRenderPipeline compile(RenderPipeline pipeline) {
        return RenderSystem.getDevice().compilePipeline(pipeline, SHADERS, Runnable::run).join().finishCompile();
    }
}

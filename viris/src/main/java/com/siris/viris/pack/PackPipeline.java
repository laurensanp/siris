package com.siris.viris.pack;

import com.siris.viris.VoxyFrame;
import com.siris.viris.Viris;
import dev.vitrail.uniform.UniformCatalog;
import dev.vitrail.uniform.UniformShape;
import dev.vitrail.uniform.UniformSource;
import dev.vitrail.uniform.Val;
import dev.vitrail.uniform.WorldState;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

/**
 * Voxy's shader pack pipeline (its IrisVoxyRenderPipeline, unchanged) fed from Vitrail instead of Iris:
 * the pack's patch code, a uniform block filled from Vitrail's uniform values, the pack textures the patch
 * samples, its blending, and GL textures for the colour targets the patch writes. Those textures are
 * GL-only; {@link VoxyFrame} copies them into images Vulkan reads after each frame.
 */
public final class PackPipeline {

    private static final Unsafe UNSAFE = unsafe();

    public final PackPatch patch;
    public final VitrailBridge.Pack pack;
    /** Distinct colour targets the patch writes (opaque and translucent), in order. */
    public final int[] targets;
    /** GL texture per entry of {@link #targets}, at the viewport size. */
    public final int[] targetTextures;
    public final int width;
    public final int height;
    /** Voxy's depth for the patch's own vxDepthTex samplers, converted to the depth packs expect (1 = far). */
    public final int patchDepth;
    private final IrisVoxyRenderPipelineData data;
    private final List<Integer> dummies = new ArrayList<>();

    private PackPipeline(PackPatch patch, VitrailBridge.Pack pack, int width, int height) {
        this.patch = patch;
        this.pack = pack;
        this.width = width;
        this.height = height;
        Set<Integer> all = new LinkedHashSet<>();
        for (int target : patch.opaqueTargets()) {
            all.add(target);
        }
        for (int target : patch.translucentTargets()) {
            all.add(target);
        }
        this.targets = all.stream().mapToInt(Integer::intValue).toArray();
        this.targetTextures = new int[targets.length];
        for (int i = 0; i < targets.length; i++) {
            targetTextures[i] = texture(GL45C.GL_RGBA16F, width, height);
        }
        this.patchDepth = texture(GL45C.GL_R32F, width, height);
        this.data = buildData();
    }

    /** The pipeline for the pack being drawn, or null when it has no Voxy patch. */
    public static PackPipeline create(VitrailBridge.Pack pack, int width, int height) {
        return PackPatch.load(pack).map(patch -> new PackPipeline(patch, pack, width, height)).orElse(null);
    }

    public IrisVoxyRenderPipelineData data() {
        return data;
    }

    public int glTextureFor(int target) {
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == target) {
                return targetTextures[i];
            }
        }
        throw new IllegalArgumentException("target " + target);
    }

    private int[] texturesOf(int[] indices) {
        int[] out = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            out[i] = glTextureFor(indices[i]);
        }
        return out;
    }

    private IrisVoxyRenderPipelineData buildData() {
        try {
            IrisVoxyRenderPipelineData built = (IrisVoxyRenderPipelineData) UNSAFE.allocateInstance(IrisVoxyRenderPipelineData.class);
            set(built, "opaqueDrawTargets", texturesOf(patch.opaqueTargets()));
            set(built, "translucentDrawTargets", texturesOf(patch.translucentTargets()));
            set(built, "opaquePatch", reverseZ(patch.opaque()));
            set(built, "translucentPatch", reverseZ(patch.translucent()));
            set(built, "uniforms", uniformLayout());
            set(built, "blendingSetup", blending());
            set(built, "imageSet", imageSet());
            set(built, "ssboSet", null);
            set(built, "renderToVanillaDepth", !patch.excludeFromVanillaDepth());
            set(built, "resolutionScale", patch.renderScale());
            set(built, "TAA", patch.taa());
            set(built, "useViewportDims", patch.useViewportDims());
            set(built, "deferTranslucency", false);
            set(built, "skipShaderDepthHackFix", patch.skipShaderDepthHackFix());
            set(built, "useDynamicFarPlane", patch.useDynamicFarPlane());
            return built;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Voxy's pack pipeline data can't be built", e);
        }
    }

    /**
     * Voxy draws reverse-Z, pack code expects depth with 1 = far (Voxy rewrites it the same way under Iris):
     * gl_FragCoord.z is flipped here, depth samplers get converted depth ({@link #patchDepth}).
     */
    private static String reverseZ(String source) {
        if (source == null) {
            return null;
        }
        return source.replaceAll("(?<!\\w)gl_FragCoord(?!\\w)", "(vec4(gl_FragCoord.xy,1.0-gl_FragCoord.z,gl_FragCoord.w))");
    }

    // --- uniforms: a std140 block of the patch's uniform names, values read from Vitrail each frame ---

    private IrisVoxyRenderPipelineData.StructLayout uniformLayout() {
        UniformCatalog catalog = pack.values().catalog();
        List<String> names = new ArrayList<>();
        List<UniformShape> shapes = new ArrayList<>();
        List<UniformSource> sources = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String name : patch.uniforms()) {
            UniformSource source = catalog.source(name);
            UniformShape shape = catalog.natural(name);
            if (source == null || shape == null || shape == UniformShape.FOG) {
                missing.add(name);
                continue;
            }
            names.add(name);
            shapes.add(shape);
            sources.add(source);
        }
        if (!missing.isEmpty()) {
            Viris.LOGGER.warn("VIRIS: the pack's Voxy patch asks for uniforms Vitrail doesn't have: {}", missing);
        }
        if (names.isEmpty()) {
            return null;
        }
        StringBuilder layout = new StringBuilder("{\n");
        int[] offsets = new int[names.size()];
        int offset = 0;
        for (int i = 0; i < names.size(); i++) {
            UniformShape shape = shapes.get(i);
            int align = align(shape);
            offset = (offset + align - 1) / align * align;
            offsets[i] = offset;
            offset += size(shape);
            layout.append('\t').append(glslType(shape)).append(' ').append(names.get(i)).append(";\n");
        }
        layout.append('}');
        int total = (offset + 15) / 16 * 16;
        Val val = new Val();
        LongConsumer updater = pointer -> {
            WorldState world = pack.values().world();
            for (int i = 0; i < offsets.length; i++) {
                write(sources.get(i), shapes.get(i), world, val, pointer + offsets[i]);
            }
        };
        return new IrisVoxyRenderPipelineData.StructLayout(total, layout.toString(), updater);
    }

    private static void write(UniformSource source, UniformShape shape, WorldState world, Val val, long at) {
        try {
            source.read(world, val);
        } catch (RuntimeException e) {
            return;
        }
        switch (shape) {
            case FLOAT -> MemoryUtil.memPutFloat(at, val.f(0));
            case INT -> MemoryUtil.memPutInt(at, val.integral() ? val.i(0) : (int) val.f(0));
            case VEC2, VEC3, VEC4 -> {
                for (int c = 0; c < shape.ordinal() - UniformShape.VEC2.ordinal() + 2; c++) {
                    MemoryUtil.memPutFloat(at + 4L * c, val.f(c));
                }
            }
            case IVEC2, IVEC3, IVEC4 -> {
                for (int c = 0; c < shape.ordinal() - UniformShape.IVEC2.ordinal() + 2; c++) {
                    MemoryUtil.memPutInt(at + 4L * c, val.integral() ? val.i(c) : (int) val.f(c));
                }
            }
            case MAT4 -> {
                Matrix4fc matrix = val.mat4();
                if (matrix != null) {
                    matrix.getToAddress(at);
                }
            }
            case MAT3 -> {
                Matrix3fc matrix = val.mat3();
                if (matrix != null) {
                    for (int column = 0; column < 3; column++) {
                        for (int row = 0; row < 3; row++) {
                            MemoryUtil.memPutFloat(at + 16L * column + 4L * row, matrix.get(column, row));
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    private static int align(UniformShape shape) {
        return switch (shape) {
            case FLOAT, INT -> 4;
            case VEC2, IVEC2 -> 8;
            default -> 16;
        };
    }

    private static int size(UniformShape shape) {
        return switch (shape) {
            case FLOAT, INT -> 4;
            case VEC2, IVEC2 -> 8;
            case VEC3, IVEC3 -> 12;
            case VEC4, IVEC4 -> 16;
            case MAT3 -> 48;
            case MAT4 -> 64;
            default -> 0;
        };
    }

    private static String glslType(UniformShape shape) {
        return switch (shape) {
            case FLOAT -> "float";
            case INT -> "int";
            case VEC2 -> "vec2";
            case VEC3 -> "vec3";
            case VEC4 -> "vec4";
            case IVEC2 -> "ivec2";
            case IVEC3 -> "ivec3";
            case IVEC4 -> "ivec4";
            case MAT3 -> "mat3";
            default -> "mat4";
        };
    }

    // --- samplers the patch reads ---

    private IrisVoxyRenderPipelineData.ImageSet imageSet() {
        Map<String, String> samplers = patch.samplers();
        if (samplers.isEmpty()) {
            return null;
        }
        StringBuilder layout = new StringBuilder();
        List<java.util.function.IntSupplier> textures = new ArrayList<>();
        List<Integer> compare = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> entry : samplers.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue();
            layout.append("layout(binding=(BASE_SAMPLER_BINDING_INDEX+").append(i).append(")) uniform ")
                .append(type).append(' ').append(name).append(";\n");
            textures.add(textureFor(name, type));
            compare.add(type.contains("Shadow") ? 1 : 0);
            i++;
        }
        int shadowSampler = GL45C.glCreateSamplers();
        GL45C.glSamplerParameteri(shadowSampler, GL45C.GL_TEXTURE_COMPARE_MODE, GL45C.GL_COMPARE_REF_TO_TEXTURE);
        GL45C.glSamplerParameteri(shadowSampler, GL45C.GL_TEXTURE_COMPARE_FUNC, GL45C.GL_LEQUAL);
        GL45C.glSamplerParameteri(shadowSampler, GL45C.GL_TEXTURE_MIN_FILTER, GL45C.GL_LINEAR);
        GL45C.glSamplerParameteri(shadowSampler, GL45C.GL_TEXTURE_MAG_FILTER, GL45C.GL_LINEAR);
        IntConsumer binding = base -> {
            for (int j = 0; j < textures.size(); j++) {
                GL45C.glBindTextureUnit(base + j, textures.get(j).getAsInt());
                GL45C.glBindSampler(base + j, compare.get(j) == 1 ? shadowSampler : 0);
            }
        };
        return new IrisVoxyRenderPipelineData.ImageSet(layout.toString(), binding);
    }

    /** Pack textures aren't shared with GL yet: depth reads see the far plane, shadows see lit, colours black. */
    private java.util.function.IntSupplier textureFor(String name, String type) {
        if (name.startsWith("vxDepthTex")) {
            return () -> patchDepth;
        }
        if (name.equals("lightmap")) {
            return VoxyFrame::lightmapGlTexture;
        }
        if (type.contains("Shadow")) {
            int depth = dummyDepth(1.0F);
            return () -> depth;
        }
        if (name.startsWith("depthtex") || name.startsWith("shadowtex")) {
            int far = dummy(GL45C.GL_R32F, new float[] {1, 1, 1, 1});
            return () -> far;
        }
        if (name.startsWith("shadowcolor")) {
            int white = dummy(GL45C.GL_RGBA8, new float[] {1, 1, 1, 1});
            return () -> white;
        }
        int black = dummy(GL45C.GL_RGBA8, new float[] {0, 0, 0, 0});
        return () -> black;
    }

    private int dummy(int format, float[] value) {
        int texture = texture(format, 1, 1);
        GL45C.glClearTexImage(texture, 0, GL45C.GL_RGBA, GL45C.GL_FLOAT, value);
        dummies.add(texture);
        return texture;
    }

    private int dummyDepth(float value) {
        int texture = texture(GL45C.GL_DEPTH_COMPONENT32F, 1, 1);
        GL45C.glClearTexImage(texture, 0, GL45C.GL_DEPTH_COMPONENT, GL45C.GL_FLOAT, new float[] {value});
        dummies.add(texture);
        return texture;
    }

    private static int texture(int format, int width, int height) {
        int texture = GL45C.glCreateTextures(GL45C.GL_TEXTURE_2D);
        GL45C.glTextureStorage2D(texture, 1, format, width, height);
        GL45C.glTextureParameteri(texture, GL45C.GL_TEXTURE_MIN_FILTER, GL45C.GL_NEAREST);
        GL45C.glTextureParameteri(texture, GL45C.GL_TEXTURE_MAG_FILTER, GL45C.GL_NEAREST);
        return texture;
    }

    // --- blending of the translucent patch ---

    private Runnable blending() {
        Map<Integer, String> states = new LinkedHashMap<>(patch.blending());
        if (states.isEmpty()) {
            return () -> {
            };
        }
        return () -> {
            String all = states.get(-1);
            if (all != null) {
                int[] factors = factors(all);
                if (factors == null) {
                    GL45C.glDisable(GL45C.GL_BLEND);
                } else {
                    GL45C.glEnable(GL45C.GL_BLEND);
                    GL45C.glBlendFuncSeparate(factors[0], factors[1], factors[2], factors[3]);
                }
            }
            for (Map.Entry<Integer, String> state : states.entrySet()) {
                int buffer = state.getKey();
                if (buffer < 0) {
                    continue;
                }
                int[] factors = factors(state.getValue());
                if (factors == null) {
                    GL45C.glDisablei(GL45C.GL_BLEND, buffer);
                } else {
                    GL45C.glEnablei(GL45C.GL_BLEND, buffer);
                    ARBDrawBuffersBlend.glBlendFuncSeparateiARB(buffer, factors[0], factors[1], factors[2], factors[3]);
                }
            }
        };
    }

    private static int[] factors(String state) {
        if (state.trim().equalsIgnoreCase("off")) {
            return null;
        }
        String[] parts = state.trim().split("\\s+");
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = factor(parts[Math.min(i, parts.length - 1)]);
        }
        return out;
    }

    private static int factor(String name) {
        String key = name.toUpperCase();
        if (!key.startsWith("GL_")) {
            key = "GL_" + key;
        }
        return switch (key) {
            case "GL_ZERO" -> GL45C.GL_ZERO;
            case "GL_ONE" -> GL45C.GL_ONE;
            case "GL_SRC_COLOR" -> GL45C.GL_SRC_COLOR;
            case "GL_ONE_MINUS_SRC_COLOR" -> GL45C.GL_ONE_MINUS_SRC_COLOR;
            case "GL_SRC_ALPHA" -> GL45C.GL_SRC_ALPHA;
            case "GL_ONE_MINUS_SRC_ALPHA" -> GL45C.GL_ONE_MINUS_SRC_ALPHA;
            case "GL_DST_ALPHA" -> GL45C.GL_DST_ALPHA;
            case "GL_ONE_MINUS_DST_ALPHA" -> GL45C.GL_ONE_MINUS_DST_ALPHA;
            case "GL_DST_COLOR" -> GL45C.GL_DST_COLOR;
            case "GL_ONE_MINUS_DST_COLOR" -> GL45C.GL_ONE_MINUS_DST_COLOR;
            case "GL_SRC_ALPHA_SATURATE" -> GL45C.GL_SRC_ALPHA_SATURATE;
            default -> GL45C.GL_ONE;
        };
    }

    // --- per frame ---

    /** Before Voxy draws: the targets start empty, as the pack's own targets are at the start of a frame. */
    public void clearTargets() {
        for (int texture : targetTextures) {
            GL45C.glClearTexImage(texture, 0, GL45C.GL_RGBA, GL45C.GL_FLOAT, new float[] {0, 0, 0, 0});
        }
        GL45C.glClearTexImage(patchDepth, 0, GL45C.GL_RED, GL45C.GL_FLOAT, new float[] {1, 1, 1, 1});
    }

    /** Voxy's opaque and translucent depth (its framebuffers inside the running pipeline), or 0. */
    public static int[] voxyDepths(IrisVoxyRenderPipeline pipeline) {
        if (pipeline == null || pipeline.fb.getDepthTex() == null || pipeline.fbTranslucent.getDepthTex() == null) {
            return null;
        }
        return new int[] {pipeline.fb.getDepthTex().id, pipeline.fbTranslucent.getDepthTex().id};
    }

    public void close() {
        for (int texture : targetTextures) {
            GL45C.glDeleteTextures(texture);
        }
        GL45C.glDeleteTextures(patchDepth);
        dummies.forEach(GL45C::glDeleteTextures);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = IrisVoxyRenderPipelineData.class.getDeclaredField(name);
        long offset = UNSAFE.objectFieldOffset(field);
        if (field.getType() == boolean.class) {
            UNSAFE.putBoolean(target, offset, (Boolean) value);
        } else {
            UNSAFE.putObject(target, offset, value);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

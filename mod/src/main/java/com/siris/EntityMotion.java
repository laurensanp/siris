package com.siris;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Per-entity motion for the motion vectors: every visible entity's rendered position (and body yaw
 * for living ones) is kept from frame to frame. The motion shader moves world pixels that lie inside
 * a moving entity's box with that entity (translation plus turn about its vertical axis) instead of
 * treating them as static. Inside that box, pixels on one of the entity's model cubes (legs, arms, head,
 * as posed by its animation) move with that cube instead (ModelFeatureRendererMixin records them).
 */
public final class EntityMotion {

    /** Entities handed to the shader per frame (the nearest moving ones). */
    public static final int MAX = 32;
    /** vec4s per entity in the shader's array. */
    public static final int VEC4S = 3;
    /** Model cubes handed to the shader per frame (all entities together). */
    public static final int MAX_CUBES = 256;
    /** vec4s per cube: inverse current matrix rows, previous matrix rows, local min, local max. */
    public static final int CUBE_VEC4S = 8;
    private static final int MAX_CUBES_PER_ENTITY = 64;
    /** Floats per recorded cube: column-major pose matrix, then local min / max (blocks, padded). */
    private static final int CUBE_FLOATS = 22;
    /** Depth-reconstructed points lie on the cube's faces: widen a little so they count as inside. */
    private static final float CUBE_PAD = 1.0F / 32.0F;
    private static final boolean NO_PART_MOTION = Boolean.getBoolean("siris.noPartMotion");

    private static final class Pose {
        double x;
        double y;
        double z;
        float yaw;
        double prevX;
        double prevY;
        double prevZ;
        float prevYaw;
        float width;
        float height;
        double distanceSq;
        int frame = -1;
        int prevFrame = -1;
        float[] cubes = new float[MAX_CUBES_PER_ENTITY * CUBE_FLOATS];
        float[] prevCubes = new float[MAX_CUBES_PER_ENTITY * CUBE_FLOATS];
        int cubeCount;
        int prevCubeCount;
        int cubeFrame = -1;
        int prevCubeFrame = -1;
        Object model;
        Object prevModel;
    }

    private static final Int2ObjectOpenHashMap<Pose> POSES = new Int2ObjectOpenHashMap<>();
    private static final Pose[] candidates = new Pose[256];
    private static final int[] firstCube = new int[MAX];
    private static final int[] cubeCounts = new int[MAX];
    private static final Comparator<Pose> NEAREST = Comparator.comparingDouble(p -> p.distanceSq);
    /** This frame's render states to their entity (EntityRenderState carries no id). */
    private static final IdentityHashMap<EntityRenderState, Pose> STATES = new IdentityHashMap<>();
    private static final Matrix4f scratch = new Matrix4f();
    private static int frame;

    private EntityMotion() {
    }

    /** Frame start: positions extracted from now on belong to the next frame. */
    public static void frameStart() {
        frame++;
        STATES.clear();
        if ((frame & 63) == 0) {
            for (Iterator<Int2ObjectMap.Entry<Pose>> it = POSES.int2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                if (frame - it.next().getValue().frame > 4) {
                    it.remove();
                }
            }
        }
    }

    /** LevelExtractor, each visible entity: the state holds the position it is drawn at this frame. */
    public static void extracted(Entity entity, EntityRenderState state) {
        Pose pose = POSES.get(entity.getId());
        if (pose == null) {
            pose = new Pose();
            POSES.put(entity.getId(), pose);
        }
        if (pose.frame != frame) {
            // First extraction this frame (a shadow pass may extract the same entity again).
            pose.prevX = pose.x;
            pose.prevY = pose.y;
            pose.prevZ = pose.z;
            pose.prevYaw = pose.yaw;
            pose.prevFrame = pose.frame;
            pose.frame = frame;
            float[] swap = pose.prevCubes;
            pose.prevCubes = pose.cubes;
            pose.cubes = swap;
            pose.prevCubeCount = pose.cubeCount;
            pose.prevCubeFrame = pose.cubeFrame;
            pose.prevModel = pose.model;
        }
        STATES.put(state, pose);
        pose.x = state.x;
        pose.y = state.y;
        pose.z = state.z;
        pose.yaw = state instanceof LivingEntityRenderState living ? living.bodyRot : Float.NaN;
        pose.width = state.boundingBoxWidth;
        pose.height = state.boundingBoxHeight;
        pose.distanceSq = state.distanceToCameraSq;
    }

    /**
     * ModelFeatureRenderer.prepareModel, after setupAnim: the posed cubes of an entity's model (camera-relative,
     * like the motion shader's points). Only the first model per entity and frame (layers share its skeleton; a
     * shadow pass may draw it again).
     */
    public static void recordModel(Object state, Model<?> model, PoseStack poseStack) {
        Pose pose = NO_PART_MOTION ? null : STATES.get(state);
        if (pose == null || pose.cubeFrame == frame) {
            return;
        }
        // Must be drawn where the world pass draws it (camera-relative), not in some other pass's space.
        Matrix4f root = poseStack.last().pose();
        Vec3 camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos;
        float reach = pose.width + pose.height + 2.0F;
        if (Math.abs(root.m30() - (pose.x - camera.x)) > reach || Math.abs(root.m31() - (pose.y - camera.y)) > reach
            || Math.abs(root.m32() - (pose.z - camera.z)) > reach) {
            return;
        }
        pose.cubeFrame = frame;
        pose.model = model;
        pose.cubeCount = 0;
        float[] out = pose.cubes;
        model.root().visit(poseStack, (part, path, index, cube) -> {
            int n = pose.cubeCount++;
            if (n >= MAX_CUBES_PER_ENTITY) {
                return;
            }
            int o = n * CUBE_FLOATS;
            part.pose().get(out, o);
            out[o + 16] = Math.min(cube.minX, cube.maxX) / 16.0F - CUBE_PAD;
            out[o + 17] = Math.min(cube.minY, cube.maxY) / 16.0F - CUBE_PAD;
            out[o + 18] = Math.min(cube.minZ, cube.maxZ) / 16.0F - CUBE_PAD;
            out[o + 19] = Math.max(cube.minX, cube.maxX) / 16.0F + CUBE_PAD;
            out[o + 20] = Math.max(cube.minY, cube.maxY) / 16.0F + CUBE_PAD;
            out[o + 21] = Math.max(cube.minZ, cube.maxZ) / 16.0F + CUBE_PAD;
        });
        if (pose.cubeCount > MAX_CUBES_PER_ENTITY) {
            pose.cubeCount = 0;
        }
    }

    /**
     * Writes the entity count and the entities' vec4 array (MAX * VEC4S, padded with zeros), camera-relative
     * to cameraPos: center + yaw change (radians, current minus previous), box half size + first cube, world
     * movement + cube count; then the cube array (MAX_CUBES * CUBE_VEC4S). cameraDelta (current minus previous
     * camera position) is taken out of the previous cube matrices, the shader adds it back.
     */
    public static Std140Builder write(Std140Builder data, Vec3 cameraPos, Vec3 cameraDelta) {
        int count = 0;
        for (Pose pose : POSES.values()) {
            if (pose.frame != frame || pose.prevFrame != frame - 1 || count == candidates.length) {
                continue;
            }
            double dx = pose.x - pose.prevX;
            double dy = pose.y - pose.prevY;
            double dz = pose.z - pose.prevZ;
            double moved = dx * dx + dy * dy + dz * dz;
            float turned = yawDelta(pose);
            // Standing still, or teleported (no connection to last frame's picture).
            if (moved < 1e-8 && Math.abs(turned) < 1e-4F || moved > 8.0 * 8.0) {
                continue;
            }
            candidates[count++] = pose;
        }
        int total = count;
        if (count > MAX) {
            Arrays.sort(candidates, 0, count, NEAREST);
            count = MAX;
        }
        data.putInt(count);
        int cubes = 0;
        for (int i = 0; i < count; i++) {
            Pose pose = candidates[i];
            // Same model and cube count as last frame, else cube n need not be last frame's cube n: box only.
            boolean matched = pose.cubeFrame == frame && pose.prevCubeFrame == frame - 1 && pose.model == pose.prevModel
                && pose.cubeCount > 0 && pose.cubeCount == pose.prevCubeCount && cubes + pose.cubeCount <= MAX_CUBES;
            firstCube[i] = cubes;
            cubeCounts[i] = matched ? pose.cubeCount : 0;
            cubes += cubeCounts[i];
        }
        for (int i = 0; i < MAX; i++) {
            if (i >= count) {
                data.putVec4(0.0F, 0.0F, 0.0F, 0.0F).putVec4(0.0F, 0.0F, 0.0F, 0.0F).putVec4(0.0F, 0.0F, 0.0F, 0.0F);
                continue;
            }
            Pose pose = candidates[i];
            // Box a bit wider than the hitbox (arms, tails, held items); its bottom just above the
            // feet so the ground the entity stands on keeps the camera's motion.
            float half = pose.width * 0.5F + 0.3F;
            double bottom = pose.y + 0.03;
            double top = pose.y + pose.height + 0.15;
            data.putVec4((float) (pose.x - cameraPos.x), (float) ((bottom + top) * 0.5 - cameraPos.y),
                (float) (pose.z - cameraPos.z), yawDelta(pose));
            data.putVec4(half, (float) ((top - bottom) * 0.5), half, firstCube[i]);
            data.putVec4((float) (pose.x - pose.prevX), (float) (pose.y - pose.prevY), (float) (pose.z - pose.prevZ),
                cubeCounts[i]);
        }
        float dx = (float) cameraDelta.x;
        float dy = (float) cameraDelta.y;
        float dz = (float) cameraDelta.z;
        for (int i = 0; i < count; i++) {
            Pose pose = candidates[i];
            float[] cur = pose.cubes;
            float[] prev = pose.prevCubes;
            for (int c = 0; c < cubeCounts[i]; c++) {
                int o = c * CUBE_FLOATS;
                // Rows of the affine matrices (the shader dots them with vec4(point, 1)).
                scratch.set(cur, o).invertAffine();
                data.putVec4(scratch.m00(), scratch.m10(), scratch.m20(), scratch.m30());
                data.putVec4(scratch.m01(), scratch.m11(), scratch.m21(), scratch.m31());
                data.putVec4(scratch.m02(), scratch.m12(), scratch.m22(), scratch.m32());
                data.putVec4(prev[o], prev[o + 4], prev[o + 8], prev[o + 12] - dx);
                data.putVec4(prev[o + 1], prev[o + 5], prev[o + 9], prev[o + 13] - dy);
                data.putVec4(prev[o + 2], prev[o + 6], prev[o + 10], prev[o + 14] - dz);
                data.putVec4(cur[o + 16], cur[o + 17], cur[o + 18], 0.0F);
                data.putVec4(cur[o + 19], cur[o + 20], cur[o + 21], 0.0F);
            }
        }
        for (int i = cubes * CUBE_VEC4S; i < MAX_CUBES * CUBE_VEC4S; i++) {
            data.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
        }
        Arrays.fill(candidates, 0, total, null);
        return data;
    }

    /** Body turn since last frame in radians; renderers rotate living models by 180 - bodyRot about +Y. */
    private static float yawDelta(Pose pose) {
        if (Float.isNaN(pose.yaw) || Float.isNaN(pose.prevYaw)) {
            return 0.0F;
        }
        return Mth.wrapDegrees(pose.yaw - pose.prevYaw) * Mth.DEG_TO_RAD;
    }
}

package com.siris.viris.pack;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.vitrail.pack.option.OptionValue;
import dev.vitrail.render.PackChain;
import dev.vitrail.render.PackValues;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What VIRIS reads out of Vitrail (LGPL, installed by the player, never packed): the pack that is
 * drawing, its settings, uniform values, block ids and colour targets. Vitrail keeps most of these package
 * private, so they are read by reflection; every call answers null when Vitrail isn't there or changed.
 */
public final class VitrailBridge {

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("vitrail");
    private static boolean resolved;
    private static boolean usable;
    private static Field active;
    private static Field packPath;
    private static Field chosen;
    private static Field profile;
    private static Field world;
    private static Field values;
    private static Field targets;
    private static Field blockTable;
    private static Method schedule;
    private static Method surface;
    private static Method surfaceView;
    private static Method steps;
    private static Method stepRead;
    private static Method stepProgram;
    private static Method stepFullscreen;
    private static Class<?> sideType;

    private VitrailBridge() {
    }

    /** The pack Vitrail draws right now, as far as VIRIS needs it. */
    public record Pack(Path path, Map<String, OptionValue> chosen, String profile, String world, PackValues values) {
    }

    private static boolean resolve() {
        if (resolved) {
            return usable;
        }
        resolved = true;
        if (!PRESENT) {
            return false;
        }
        try {
            Class<?> chain = PackChain.class;
            active = field(chain, "active");
            packPath = field(chain, "packPath");
            chosen = field(chain, "chosen");
            profile = field(chain, "profile");
            world = field(chain, "world");
            values = field(chain, "values");
            targets = field(chain, "targets");
            Class<?> colorTargets = Class.forName("dev.vitrail.render.ColorTargets");
            Class<?> targetSchedule = Class.forName("dev.vitrail.pack.target.TargetSchedule");
            sideType = Class.forName("dev.vitrail.pack.target.TargetSchedule$Side");
            Class<?> bound = Class.forName("dev.vitrail.pack.target.TargetSchedule$Bound");
            Class<?> targetSurface = Class.forName("dev.vitrail.render.TargetSurface");
            schedule = method(colorTargets, "schedule");
            surface = method(colorTargets, "surface", int.class, sideType);
            surfaceView = method(targetSurface, "view");
            steps = method(targetSchedule, "steps");
            stepRead = method(bound, "read", int.class);
            stepProgram = method(bound, "program");
            stepFullscreen = method(bound, "fullscreen");
            blockTable = field(Class.forName("dev.vitrail.render.BlockStateIds"), "table");
            usable = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            com.siris.viris.Viris.LOGGER.warn("VIRIS: this Vitrail build can't be read, shader packs draw no LODs", e);
        }
        return usable;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    /** The pack being drawn, or null (no Vitrail, no pack, or a pack that is switched off). */
    @SuppressWarnings("unchecked")
    public static Pack pack() {
        if (!resolve() || !PackChain.drawingPack()) {
            return null;
        }
        try {
            Object chain = active.get(null);
            if (chain == null) {
                return null;
            }
            return new Pack((Path) packPath.get(chain), (Map<String, OptionValue>) chosen.get(chain), (String) profile.get(chain),
                (String) world.get(chain), (PackValues) values.get(chain));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * The colour target {@code index} as the pack's gbuffer programs leave it for the first full screen pass
     * (main or alternate image, by the pack's flip schedule).
     */
    public static GpuTextureView gbufferTarget(int index) {
        if (!resolve()) {
            return null;
        }
        try {
            Object chain = active.get(null);
            if (chain == null) {
                return null;
            }
            Object colorTargets = targets.get(chain);
            Object side = sideType.getEnumConstants()[0];
            Object first = null;
            for (Object step : (List<?>) steps.invoke(schedule.invoke(colorTargets))) {
                if (!(boolean) stepFullscreen.invoke(step)) {
                    continue;
                }
                String program = (String) stepProgram.invoke(step);
                if (first == null) {
                    first = step;
                }
                if (program.startsWith("deferred")) {
                    first = step;
                    break;
                }
            }
            if (first != null) {
                side = stepRead.invoke(first, index);
            }
            Object found = surface.invoke(colorTargets, index, side);
            return found == null ? null : (GpuTextureView) surfaceView.invoke(found);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** block.properties ids per block state, as Iris hands them to Voxy. */
    @SuppressWarnings("unchecked")
    public static Object2IntMap<BlockState> blockIds() {
        if (!resolve()) {
            return null;
        }
        try {
            return (Object2IntMap<BlockState>) blockTable.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static boolean present() {
        return PRESENT;
    }
}

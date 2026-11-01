# SIRIS

One repository (github.com/laurensanp/siris, MIT; was laurensanp/iris-dlss, then pyraxis-render, old URLs redirect): a Fabric mod for Minecraft 26.3 that runs DLSS Super
Resolution + Frame Generation inside the game's own Vulkan renderer (RenderPearl). Shader packs come from
**Vitrail** (separate mod, modrinth `vitrail-shaders`, github avpbynf/Vitrail-Shaders, LGPL-3.0, not ours,
never commit or ship it). `README.md` lists everything a player or developer needs and where it comes from.

Goal: shader packs (Solas) + DLSS SR/FG on Vulkan, in the user's Lunar Client. Voxy LODs: **VIRIS** (`viris/`, was voxy-vulkan, 1:1 port via GL interop).

| Path | What |
|---|---|
| `dlss-bridge/` | native C++ bridge (`siris.dll`): NGX, DLSS SR, presenter + DLSS-G, `dlss_probe.exe` |
| `mod/` | the Fabric mod (Java), packs the bridge + NVIDIA DLLs into its jar |
| `viris/` | VIRIS (was voxy-vulkan): separate mod, Voxy's own renderer on Vulkan via GL interop |
| `testpacks/` | own small GLSL test packs (from the old Iris port) |
| `libs/` (ignored) | compile-only jars: `sodium-fabric-0.9.2+mc26.3.jar`, `voxy-0.2.20-beta.jar` (Modrinth) |
| `mod/run/` (ignored) | test game dir: Sodium, Fabric API, Vitrail in `mods/`, packs, `saves/vulkan-test`, `vitrail/pack.txt` |

## History

- Until 2026-10-27 shader packs ran through an own Vulkan port of Iris (github.com/laurensanp/iris-vulkan,
  LGPL, milestones M1-M3 done: gbuffers, shadows, compute, SSBOs, custom images, geometry/tessellation).
  Replaced by Vitrail on 2026-10-27 (5-10 % faster, same picture, maintained by others); the fork's GitHub repo
  is archived, the local clone and the submodule are gone. Releases iris-dlss v0.5.0/v0.6.0 and iris-vulkan
  vulkan-v0.1.0/v0.2.0 belong to that era.
- Names: Iris DLSS Bridge (`iris-dlss`, up to 0.7.x) -> PYRAXIS (display name only, 1.1.0/1.1.1; code, jar, config
  and logs stayed `iris-dlss`) -> **SIRIS** (2026-10-31, user's wish, everywhere): mod id `siris`, package
  `com.siris` (main class `Siris`), `siris.mixins.json`, `assets/siris/icon.png`, `.github/assets/siris-icon.*`,
  `siris.dll` (exports `siris_*`, JNI `Java_com_siris_*`, macros `SIRIS_*`), `config/siris.properties`
  (`DlssConfig.configFile()` moves an old `iris-dlss.properties` over once), game-dir folder `siris/`, dev
  properties `-Dsiris.*`, log prefix `SIRIS:`, jar `siris-<version>`. Social preview: title rebuilt from the
  PYRAXIS image's own S/I/R glyphs. Old releases/jars keep their names. The bridge must be rebuilt
  (`build.bat`) for the new DLL/JNI names. Lunar: remove `iris-dlss-*.jar` (other file name, else both load).
- History of iris-dlss keeps Kiwious' Claude co-author lines (user decided not to rewrite).

## Status (2026-10-30)

- **DLSS with Vitrail works** (Vitrail 0.13.0-beta, Solas 3.8, verified with screenshots, an FG capture and a
  benchmark): Vitrail draws the world into the main target (reverse-Z depth kept), takes the projection the game
  draws with (our jitter included) and runs composite/final before our upscale at `FogRenderer.endFrame`.
  SIRIS changes for it: `VitrailRenderScaleMixin` (`@Pseudo`, Vitrail's own FSR "Render Scale" reads 100 %
  while DLSS runs; logged once), `ShaderPackTweaks` reads `vitrail/pack.txt` (`pack=`, `enabled=`,
  `renderscale=`, `shadowdistance=`, `shadowmapscale=`; old files: one bare word, `none` = off) and the shared
  `shaderpacks/<pack>.txt`. Pack screen: key `I`.
- Vitrail drops the whole pack when one program doesn't compile (it doesn't remove unused functions like Iris):
  Solas with `SSAO=false` (deferred1, colortex5) or `DOF=true`+`BLOOM=false` (composite14,
  gbufferProjectionInverse). Log: `(Vitrail) <program> did not compile, nothing of this pack will be drawn`.
  Vitrail's hand is too bright at night. Could be reported upstream (ask the user first).
- Benchmark 2026-10-27, 1080p, Solas, render distance 8, static view, 4060 Ti, rendered fps: SR+FG 122 (245 shown),
  Quality 176, Performance 217, DLAA 123, off 146 (old fork in the same scene: 115/161/196/114/133).
- VIRIS (2026-11-01, user's wish: voxy-vulkan renamed for 0.3.0, blue theme): mod id `viris`, package
  `com.siris.viris` (main class `Viris`), `viris.mixins.json`, `assets/viris/icon.png`, `.github/assets/viris-icon.*`
  (SIRIS frame stack in blue: ground `#0F1522`, `#4F5B6E`, `#7CC4FF`, `#3D8BFF`, 2x2 voxel grid instead of the plus),
  log prefix `VIRIS:`, dev property `-Dviris.timing`, jar `viris-<version>`, Modrinth `viris` (own project). Lunar:
  remove `voxy-vulkan-*.jar` (other mod id, else both load). Theme colour for VIRIS: `#3D8BFF`.
- VIRIS in Lunar (2026-11-01): `DLSS Optimized` has viris-0.3.0 instead of voxy-vulkan-0.2.0 (backup
  `mods-backup-2026-11-01-viris/`), Voxy 0.2.20, siris-1.1.2, Complementary on: at render distance 8 the LODs reach the
  horizon with the pack's own lighting, 275/137 fps (FG x2), no errors. Lunar's game log shows none of VIRIS' INFO
  lines (only `Enabling optional FeatureSet [VIRIS OpenGL interop]`); check with F3 (voxy line) and screenshots.
  surv/dlss-test untouched (user: only DLSS Optimized). Voxy Server Side (`lss` 0.19.0, in surv) loads fine with
  VIRIS (dev): it feeds Voxy's own ingest (`VoxyCompat` raw ingest bridge), VIRIS only replaces drawing; in
  singleplayer it waits for LAN ("LOD request processing deferred until LAN"), server not tested.
- voxy-vulkan 0.2.0 (2026-11-01, user asked for a 1:1 port; the own LodRenderer of 0.1.x is gone): Voxy's own
  GL renderer runs unchanged on a hidden SDL OpenGL 4.6 context (`GlContext`, made current at
  `RenderSystem.initRenderer` RETURN order 800, before Voxy's own init at 900). Voxy's only draw entry is
  `VoxyRenderSystem.setupViewport(proj, modelView, fog, w, h, camXYZ)` + `renderOpaque(viewport, depthGlTex,
  colorGlTex)`; `VoxyFrame` calls it right before the game's "Solid" pass (`LevelRendererMixin` on
  `lambda$addMainPass$0`, matrices from `SodiumWorldRenderer.prepareChunkRendering`) into shared images over an
  empty picture, then a Vulkan pass writes colour (blend ONE, 1-srcA) and depth where Voxy drew; terrain draws
  over them by depth (= Voxy's stencil). Shared images: RenderPearl textures from a VMA pool with exportable
  dedicated memory (`VulkanGpuTextureMixin`, `Interop`), imported with GL_EXT_memory_object_win32; device gets
  VK_KHR_external_memory/semaphore_win32 as an optional FeatureSet. Three image sets in turn, binary semaphores per
  set (glDone/vkDone with texture lists). Lightmap: shared 16x16 copy per set. Atlas: `AtlasReader` rebuilds the
  block atlas from sprite images (texels start at u0/v0 = x + padding, not getX). Rows: RenderPearl's Vulkan images
  keep GL's row order, no flip. Patched Voxy hooks: device check, Iris hook, Sodium draw hook (`postApply`),
  `LightMapHelper.getLightmapTextureId`, `SoftwareModelTextureBakery.setupTexture`.
  Verified 2026-11-01: day and night look like Voxy on OpenGL; with DLSS Quality + FG x2 at 1080p, render distance
  8: Voxy ~0.85 ms render thread + ~0.7 ms GL per frame, 200 vs 230 rendered fps (Voxy rendering off). Lunar
  (`DLSS Optimized`, siris-1.1.2, backup `mods-backup-2026-11-01-voxy/`): Voxy 0.2.20 + voxy-vulkan 0.2.0 run, LODs
  seamless past render distance 8, FG ~357/180 fps (shaders off; the profile has Complementary on, so LODs are off
  there by default). Open: two thin dark horizontal lines over LOD water only with DLSS upscaling (not in the raw
  frame, DLAA bypass) - probably LOD depth/motion vectors. Hangs seen earlier were VRAM: with Lunar's game open
  (~4 GB) GL frames took up to 1.4 s with 474 evictions and RenderPearl's 5 s submit timeout hit; close Lunar's game
  before dev tests. SIRIS side note: closing Lunar's window once wrote a watchdog crash report
  (`DlssNative.destroyPresenter` hung on shutdown). Tried and dropped: timeline
  semaphores (NVIDIA 617 passed only GL's first signal to Vulkan), GL fence + CPU wait before submit (hung with
  DLSS), reading back a shared texture on the GL side (glGetTextureImage, hung). `-Dviris.timing=true` logs
  Voxy CPU/GL time, free VRAM and evictions. Without Voxy support in the pack the LODs are skipped while it is on.
  Shader packs (2026-11-01, milestone 1): packs with Voxy support (`shaders/<world>/voxy.json` + `voxy_opaque.glsl`/
  `voxy_translucent.glsl`: Complementary, BSL, Photon, Solas, MakeUp...) draw the LODs with their own code, as under
  Iris. `pack/PackPatch` reads the patch with Vitrail's IncludeExpander (place from `DimensionSet.place`,
  `minecraft:overworld` -> `world0`), `pack/PackPipeline` builds Voxy's `IrisVoxyRenderPipelineData` without Iris
  (Unsafe; `VoxyVulkanMixinPlugin` strips its Iris-only builder methods at load or the verifier fails), std140 uniform
  block filled from Vitrail's `PackValues.catalog()`, `RenderPipelineFactoryMixin` makes Voxy build its
  `IrisVoxyRenderPipeline`, block ids from Vitrail's `BlockStateIds.table`. Vitrail side (`mixin/vitrail/`): `VOXY 3`
  define, vx* uniforms in the engine catalog (`VoxyPackUniforms`, projection un-reversed like Voxy), vxDepthTex samplers
  bound in `PackPass.bindSamplers` to Voxy's depth converted to far = 1 (`DepthConvert`). Frame: GL draws at the main
  pass start, Vulkan waits there (after `GeometryHold.flushIdle` + `GraphicsApi.suspendLevelPass`, Vitrail keeps
  passes open), `PackChain.drawBeforeTranslucents` HEAD writes the LOD gbuffer targets into the pack's targets where
  the scene depth is clear (side = first deferred step's read side), vkDone with the frame's submit. Not yet: pack
  inputs for the patch (shadow maps, noisetex, colortex13/18/19, depthtex1 are dummies: LODs get no shadows), patch
  depth samplers during the translucent pass, renderToVanillaDepth packs. Exceptions in these hooks would stop Vitrail:
  they only switch the LODs off.
  Voxy is All-Rights-Reserved: never pack or modify its jar (runtime mixins only).
- **Solved (2026-10-27):** in Lunar the FPS dropped after some playtime and recovers later (old fork build and Vitrail alike).
  2026-10-27 finding: Lunar gives the game 3 GB heap ("Allocated Memory: 3072" in crash headers), runs ZGC; on a
  server with Solas the old generation grows ~10 MB/s to ~2.3 GB plus ~600 MB young, so the heap sits at the limit
  and ZGC allocation stalls show as Lunar's `Pause - Unknown` 50-90 ms hitches; rendered fps 86 -> 17, single frames
  up to 1 s. Fixed by the user setting 8 GB in Lunar's launcher (jstat then shows ~16 GB max): old gen stays
  ~560-740 MB, no `Pause - Unknown` after joining, 54-82 fps rendered / 130-165 shown on the server (~4 min watched).
  If it comes back: find the allocator with JFR (`-XX:StartFlightRecording` in Lunar's JVM arguments; Lunar sets
  -XX:+DisableAttachMechanism, but `jstat -gc <pid>` works). Not a drop: "1 fps shown, ~12-18 rendered, pacing
  1000 ms" = window in the background, throttled by Lunar's dynamic-fps mod.

- **Frame pacing without VSync fixed (2026-10-27, `fd1f0cc`)**: PresentMon (Intel, `../presentmon`, needs admin:
  `capture-lunar.bat` as administrator records javaw.exe 60 s) in Lunar, 2x FG, 165 Hz, no VSync: frames go out as
  "Hardware: Legacy Flip", none dropped, but generated/real came in 2.0 / 7.3 ms pairs (15 % visible only 0-2 ms).
  The presenter skipped the real frame's wait whenever the game's next frame was queued (always without VSync).
  Now it always waits 90 % of its slot: Lunar 5.5 / 6.0 ms pairs, 1 % below 2 ms. Two other approaches (minimum gap
  across frames, spacing by game work time) collapsed to ~15 fps (presenter wait feeds back into the game's frame
  interval), don't retry them.
- PresentMon in Lunar 2026-10-31 (server, Complementary, Custom 80 % + FG x2, no VSync, 165 Hz): user felt "not like 300".
  Mod log: 61-110 rendered / 132-212 shown. PresentMon: 164 presents/s, only 142 distinct frames/s displayed, 13 % dropped,
  ~10 % shown for two refreshes (12.1 ms), display latency ~20 ms, mode "Composed: Copy with GPU GDI" (DWM copy; on
  2026-10-27 it was "Hardware: Legacy Flip"). Suspects: the capture-lunar.bat console or an overlay in front (Discord, Game
  Bar). Next: re-measure with the game in front and no overlays; then consider a shown-fps cap just under 165. PresentMon
  can't tell generated from real frames for our Vulkan presenter (all "Application", swapchain 0x0).
- **Vertex buffer churn fixed (`abfbfc5`)**: `StagedVertexBufferPoolMixin` keeps recycled vanilla vertex buffers
  (GUI, entities, particles) up to 120 frames instead of closing them each frame (with the presenter the GPU is ~2
  frames behind, so fences rarely signalled in time): dev ~2400 buffers / 600 MB per 10 s -> 6 / 4 MB. Lunar's
  remaining FPS drops were full video memory: render distance 32 (~3.8 GB Sodium) + Discord Clips (+ its capture in
  dwm, ~1.5 GB); user turned Discord Clips off. `diagnosticRecording=true` logs GPU allocations by label plus VMA and
  process memory every 10 s (`GpuAllocationStats`).

## Lunar Client

- The user plays in Lunar, profile `~/.lunarclient/profiles/surv` (Fabric 26.3; Lunar's Sodium 0.9.3-beta.1,
  Fabric API, Mod Menu, ImmediatelyFast, entityculling, dynamic-fps, Xaero's maps, nofog, voxy-server-side).
- Install only while Lunar's game isn't running (check the java processes, the launcher may stay open). Back up
  the mods folder first (`mods-backup-<date>-<what>/`), then replace jars with the same names, no duplicates.
- Lunar's "Multi-Threaded Rendering" (`decoupledPresent` in `~/.lunarclient/settings/game/<profile>/performance.json`)
  acquires/presents the swapchain itself; the mod detects it and keeps the game's output (no FG). Must be off
  (`{"version":38,"decoupledPresent":false}`). Presenting from both swapchains stalls in fullscreen.
- Since 0.7.2 `LunarCompat` (mixin config plugin of SIRIS, `acceptTargets` = after all configs are prepared,
  before any mixin applies) does both Lunar fixes itself: sets `decoupledPresent=false` in the active profile
  (`profile_manager.json` `active`; Lunar reads the file ~10 s later, after `[LC] Starting Lunar client`) and
  removes the @Redirect from Vitrail's `VulkanCommandEncoderMixin.vitrail$afterPass` class node (MixinTransformer
  .processor -> configs -> MixinInfo.state.classNode, same fields in Lunar's Mixin 0.8.x in genesis.jar) when another
  mixin redirects the barrier or Lunar is detected (`mixins.ichor.*` configs / game dir in `.lunarclient`).
  `lunarCompat=false` turns it off. Dev test: `-Dsiris.forceLunarCompat=true -Dsiris.lunarDir=<fake dir>`
  via JAVA_TOOL_OPTIONS, `-Dmixin.debug.export=true` and check `run/.mixin.out/.../VulkanCommandEncoder.class`.
  Verified in dev and in Lunar 2026-10-30 (fresh settings profile `DLSS`: performance.json created with
  `decoupledPresent:false`, no Vitrail crash with the plain jar, presenter output, FG x2, Solas draws). Lunar
  drops INFO lines logged this early, so LunarCompat's messages don't show in Lunar's log.
- Lunar test profile `dlss-test` (launcher profile, isolated, 26.3 Fabric): only Fabric API, Sodium 0.9.2 (added by
  Lunar's Auto Fix), Vitrail (plain), iris-dlss (latest), Solas + `vitrail/pack.txt`, world `vulkan-test`. surv: see "Current Lunar install".
  Tested there 2026-10-30: no crash, no VSync FG x2 ~365/185 fps without shaders, Solas 140/72 (1280x720 window).
  surv keeps shaders off (`enabled=false`); its Vitrail toggle key is Insert (SendKeys can't send it, use
  keybd_event VK 0x2D scan 0x52 extended). Screen captures (CopyFromScreen) of the game are black without VSync
  (independent flip); take in-game F2 screenshots instead. Launcher can't be driven by CDP (main window hidden); clicking via
  user32 SetCursorPos/mouse_event + CopyFromScreen screenshots works.
- Lunar profile `DLSS Optimized` (folder `dlss-optimized`, created 2026-10-30 by inserting a row into
  `~/.lunarclient/db/profiles.db` table `profiles` while the launcher was closed, copied from dlss-test's row,
  allocated_memory 8192; selected via `settings/launcher.json` `settings.gameProfile`; backup in
  `~/.lunarclient/db-backup-2026-10-30/`). Mods: iris-dlss, Sodium 0.9.3-beta.1, Vitrail, Fabric API 0.165.0, Mod
  Menu, ImmediatelyFast, EntityCulling, FerriteCore, Dynamic FPS, Particle Core (+ Fabric Language Kotlin,
  fzzy_config), Alternate Current, zfastnoise (+ zconfig); no Voxy. DLSS Custom 80 % + sharpen 0.5 + FG x2,
  render distance 16 / simulation 8, mipmaps 4, Vitrail shadowdistance 12, Solas DOF off, shadowDistance 192,
  VL_SAMPLES 8. First run singleplayer: 176-209 fps shown / 92-105 rendered with Solas. Added 2026-10-30 (user
  approved downloads, sha512 checked against Modrinth): Lithium 0.26.3, More Culling 1.9.0 (+ Cloth Config from
  surv), Better Block Entities 1.4.0 (from surv, built for Sodium 0.9.3-beta.1): same world 185-199 shown /
  89-103 rendered, 1 % low 112-126 shown (was 98-108), worst join pause 446 ms (was 958/1658 ms), no errors.
  Not recommended for this setup: Nvidium (OpenGL only), AsyncParticles, Gnetum (HUD lag with FG), Ixeris,
  borderless-fullscreen mods (presenter), DH (LODs like Voxy).
- Lunar Turbo Engine (2026-10-31): Lunar's remote feature flags (`settings/game/metadata_fallback.json` TurboEntities/
  TurboBlockEntities) switch on "Turbo Entity Mode"/"Turbo Block Entity Mode" (not shown in its settings screen): resting
  entities and block entities get baked into Lunar pipelines (`lunar_turbo`, `lunar_armor_cutout_no_cull_triangles`) that
  Vitrail doesn't draw, so with shaders armor stands vanished after a moment and sign text was missing (mobs fine, mods
  irrelevant, DLSS irrelevant, not reproducible in dev). Since 0.7.7 `LunarCompat` writes `turboEntityMode:false`,
  `turboBlockEntityMode:false` into performance.json (same Lunar settings class as decoupledPresent) when Vitrail is
  installed. User confirmed in Lunar. "Leaves shaking" in Complementary = its WAVING_LEAVES wind (ground still, measured).
- The user's Solas settings: `shaderpacks/Solas Shader V3.8.zip.txt` there (DOF, VL, tonemap tweaks).
- Lunar history: 2026-10-27 switch to Vitrail (Solas `BLOOM=true` there: with DOF on Vitrail can't compile
  composite14 otherwise; voxy jars renamed `.disabled`; old files in `mods-backup-2026-10-27-vitrail/`). The first
  start crashed (Lunar's `lunar$maybeBarrier` and Vitrail's `vitrail$afterPass` both @Redirect `memoryBarrier` in
  `VulkanCommandEncoder.submitRenderPass`); until 0.7.2 that was worked around with `tools/PatchVitrail.java` (removed 2026-10-31) and a
  local `-lunarfix` Vitrail jar (`../vitrail-dl`, never publish), since 0.7.2 `LunarCompat` does it at runtime.
  Could be reported to Vitrail (use @WrapOperation instead of @Redirect); ask the user first.
- Current Lunar install (2026-10-31): surv, dlss-test and dlss-optimized have `iris-dlss-1.1.1+mc26.3.jar` (1.1.0 in `mods-backup-2026-10-31-110/`, 0.7.7 in `mods-backup-2026-10-31-077/`, 0.7.6 in `mods-backup-2026-10-31-076/`) + plain `vitrail-0.13.0-beta+mc26.3.jar`,
  `diagnosticRecording=false`; Lunar's active settings profile is `Default` (`DLSS` = empty test profile).

## Build & test

- Gradle needs **JDK 25**: `JAVA_HOME=~/.jdks/jdk-25.0.4.1+1`. Use `--no-daemon`; "Could not connect to the Gradle
  daemon" happens often, `gradlew --stop` and rerun.
- Bridge: `dlss-bridge/fetch-deps.ps1`, `dlss-bridge/build.bat` (MSVC 2022, Build Tools are enough). Mod:
  `cd mod && gradlew build` -> `mod/build/libs/siris-*.jar`. VIRIS: `cd viris && gradlew build`.
- Test loop: `cd mod && gradlew runClient -PtestWorld=vulkan-test` joins/creates the world, screenshot
  `mod/run/screenshots/dlss-test.png` after 200 ticks, quits (`DevTest`, `DevTestWorldMixin`). Options:
  `-PtestCommands="time set 1500;weather clear;tp @p -36 84 18 -90 20"` (tree/shadow view; `tp @p -36 82 18 180 8`
  = beach view), `-PtestPitch`, `-PtestTicks=600` (more for screenshots after loading), `-PwindowSize=1920x1080`,
  `-PtestHideGui=true` (F1, for gallery shots; overlay=off too), `-PdlssBenchmark` (logs `SIRIS benchmark:` lines), `-PdlssOff`, `-PdlssDebug=spin|walk` (FG capture into
  `mod/run/siris/captures`, only fires with Frame Generation). Look at the screenshot.
- Dev `options.txt`: `fullscreen:false` (else a focus change minimizes and caps at 10 fps),
  `inactivityFpsLimit:"minimized"` (else the 30 fps AFK limit hits benchmarks). Run benchmarks with nothing else
  loading the PC (±25 %). Logs written across midnight get lost (log roll), rerun.
- Voxy dev test: move `mod/run/mods-voxy/*` (Voxy) plus VIRIS' jar into `mod/run/mods`, no shader pack,
  one run at render distance 32 so Voxy ingests, then lower it. `-PtestTicks=400+` (meshing takes seconds).
- OpenGL reference shot: `enabled=false` in `config/siris.properties` + `preferredGraphicsBackend:"opengl"` in
  `options.txt` (Vitrail: `vitrail/graphics-api.txt`), then restore.

## DLSS notes

- Two main targets swapped in `GameRendererMixin`: world into a DLSS-sized one, GUI into a window-sized one.
  Jitter in `CameraMixin` -> `DlssUpscaler.applyJitter`; world depth copied before the hand pass; motion vectors
  from depth: camera plus moving entities (`EntityMotion`: positions from `LevelExtractorMixin`, nearest 32 moving
  ones as boxes in the motion shader's UBO, translation + body yaw). Depth is reverse-Z (`DepthConvention`).
  FG matrices are built in double precision (Matrix4d); F5 perspective change resets history. Measured
  2026-10-30 (spin capture, horizontal shift of generated vs real frames): generated frames land at 44-56 % between real ones.
- Pack noise: Solas/BSL/Complementary animate their dither only `#ifdef TAA`; with TAA off it was a fixed screen
  pattern that crawled under DLSS (user: "edges crawl standing still"). `VitrailDitherMixin` (IncludeExpander
  .logicalLines) + `ShaderPackTweaks.animateDither` turn on just those blocks (<= 4 statements using
  frameCounter/frameTimeCounter, no gl_Position/Jitter). User confirmed the jitter gone. Mipmaps / LOD bias
  barely changed the still-frame difference afterwards (0.27-0.29 at 1080p).
- Softness in Lunar (2026-10-30): `ShaderPackTweaks.selectedPack` skipped packs with `enabled=false`, and surv starts
  with shaders off, so Solas' TAA (on by default) ran on top of DLSS once toggled on in game. Now the pack's TAA is
  written off regardless, and the Vitrail hook also comments out `#define TAA` (the pack default) while DLSS runs.
  1080p test, Solas TAA on vs off: mean edge 9.9 vs 15.5. The user's Solas has DOF=true (foreground blur).
- Grid on leaves/sand (2026-10-30): the old CAS sharpener amplified DLSS's faint 3-px (= 2 render px at 1.5x)
  residue ~9x (stripe metric 3.21 vs 0.37 without sharpening). Now RCAS (FSR 1, noise term halves the lobe), slider
  mapped through sqrt (35 % -> 0.59): stripes 0.24, edge strength 16.3 (native 18.7, old CAS 21.6 incl. grid).
  Dither animation was ruled out (`-Dsiris.noDitherAnimation=true` gives the same grid with CAS).
- Jitter sign (2026-10-31): NGX got +jitterX; the picture shook ±0.25 px horizontally at a still camera (user's Lunar
  F2 burst, then dev Custom 80 %: 8 testShots, floor rms 0.7-2.3 with +x vs 0.42-0.50 and no shift with -x; y sign made
  no difference). `DlssUpscaler.reportedJitterX()` now reports -jitterX (SR and FG params); config jitterSign* stay a
  diagnostic on top (1.0 = correct). User confirmed in Lunar.
- Shaking for ~2 s after joining: history reset for 60 frames (new level) showed the raw jitter; jitter is now 0
  while `resetFrames > 0`.
- FG smear at tree/block edges while turning (2026-10-30): reproduced with `-Dsiris.debugSpin=6` (6 deg per
  rendered frame, ~100 fps): only generated frames smear (real frames clean); at 1.5 deg/frame generated frames are
  clean. Inputs checked: MVs match the measured image shift (7.27 vs 7.3 render px), clipToPrevClip's depth column
  is (0,0,1,0) for a pure turn, camera vectors orthonormal/right-handed, FOV/aspect match. Pale tree "copies" in
  slow turns are real fogged trees (same in `-Dsiris.bypass=true` raw frames, DLAA only). All presets alike.
- Dev: `-Dsiris.testShots=N` (N screenshots on consecutive ticks), `-Dsiris.bypass=true` (DLAA: raw frame
  instead of DLSS), `-Dsiris.debugSpin=deg` with `-PdlssDebug=still` (any spin speed), debugLog prints FG params.
- Join hitch (Lunar `Pause - Unknown` 1.5 s + ~20 x 50-800 ms after joining): JFR showed Vitrail's
  `PackChoice.reloadIfTheWorldMoved` re-reading/compiling the whole pack because the registry stamp changed (only
  needed for block tags `%ns:path` in block.properties). `VitrailJoinReloadMixin` skips it (PackDefines.settle()
  by reflection) when that is the only reason and the pack names no tag (`ShaderPackTweaks.selectedPackUsesBlockTags`);
  dimension change / DH / LabPBR still reload. Solas has no tags. Remaining join cost: Vitrail pipeline warm-up
  (`skipLevelWhilePackWarms`), chunk loading. Window resize recreates DLSS/FG (150-250 ms, expected).
- Presenter alone costs nothing (dev, FG off: presenter 133-135 fps vs presenter off 136); FG costs ~3.3 ms per
  rendered frame (benchmark 89.8 vs 128.8 rendered).
- wWaypoints (mod `wwaypoints`, ARR, in surv) icons lagged with FG (HUD = only on rendered frames). Its
  `WaypointOverlayLabelRenderer.render(GuiGraphics shim, DeltaTracker, WaypointManager, ModConfig)` is called from
  its own Hud.extractRenderState mixin; `WWaypointsOverlayMixin` (@Coerce, reflection) re-calls it with a shim over
  `WorldAnchoredHud`'s own GuiRenderState while FG runs; `GameRendererMixin` renders that state with a second
  GuiRenderer right before `GuiRenderer.render` and `DlssUpscaler.refreshHudless()` copies the picture into the
  hudless image again. 0.7.6: icons flickered (FG moved them with the background's motion); `patchIconMotion` writes
  each label rectangle (GuiRenderState element/text/item bounds) the screen motion of its nearest waypoint
  (`Waypoint.getPosition`, block centre) into the motion vectors. Strafe test: generated icon within 1.5-3.3 px of
  halfway (was ~6-12 px behind). wWaypoints' own placement drifts 3-10 px off its block away from the screen
  centre in real frames (own FOV math, e.g. flying FOV) = the "lag without presenter", not ours. Dev test:
  `wwp_add <x> <z> <y>` (client command; opens its list screen) or edit run/wwaypoints/.../minecraft_overworld.json
  `pos`; `-Dsiris.captureTick=N` + `-PtestRepeat="tp @p ~ ~ ~0.15"` (strafe). Never commit/ship its jar.
- `MemoryCheck`: toast when max heap < 4 GB or heap >= 90 % for 3 checks 10 s apart.
- Dev: `-PdlssDebug=still`, `-PcaptureAt=N`, `-PtestRepeat="cmd;cmd"` (every tick), JAVA_TOOL_OPTIONS
  `-Dsiris.captureFrames=12`. Dev `options.txt` has `pauseOnLostFocus:false` (else the pause menu stops FG and
  the capture). Kill old test mobs first (`kill @e[type=!player]`), they persist in `vulkan-test`.
- Benchmark costs (4060 Ti, 1080p): DLSS SR preset K ~2 ms, FG ~3 ms per rendered frame; E ~1 ms cheaper than K,
  L ~1.8 ms more, M ~0.9 ms more at Performance. Mode Custom (33-100 %) uses the closest NGX mode. Render-thread
  time goes to waiting in RenderPearl's `submit` (GPU-bound) and the presenter's `frameStart`.
- Settings UI: Sodium pages "Settings"/"Advanced" (`SodiumOptions`) and Mod Menu screen (`DlssSettingsScreen`);
  overlay in the HUD (`HudMixin`). Screenshot them with `-PdlssScreen=mod|sodium|sodium2` (`DevScreens`).
- Multi frame generation: 2x/3x/4x (`frameGenMultiplier`), limited to the GPU's `DLSSG.MultiFrameCountMax`
  (4060 Ti: 2x only). One DLSS-G evaluate per generated frame. Frames are paced evenly over the game's frame
  interval with and without VSync. 3x/4x verified in a friend's log (RTX 50, Lunar).
- Hardware: FG needs RTX 40/50; on the RTX 3070 dev machine FG fails with
  `NVSDK_NGX_Result_FAIL_UnableToInitializeFeature` (expected). `dlss-bridge/out/dlss_probe.exe` reports SR/FG.

## Releases

`"C:\Program Files\GitHub CLI\gh.exe" release create <tag> <jars> -R laurensanp/siris --title "SIRIS
<version> (Minecraft 26.3)" --latest` (gh is logged in as laurensanp; up to 0.7.1 they were `--prerelease`). v0.7.0 (2026-10-27, first with Vitrail): `iris-dlss-0.7.0+mc26.3.jar` +
`voxy-vulkan-0.1.1+mc26.3.jar`; Vitrail is never attached (link to Modrinth).
v0.7.1 (2026-10-27): pacing without VSync, vertex buffer reuse, diagnosticRecording (same voxy-vulkan 0.1.1
attached). v0.7.2 (2026-10-30): LunarCompat (plain Vitrail jar in Lunar, threaded present off); full release marked "Latest" (user asked). v0.7.3 (2026-10-30, Latest): `lunarCompat`
kept in `iris-dlss.properties` (0.7.2's `DlssConfig.save` dropped the key, so `lunarCompat=false` held one start). v0.7.4 (2026-10-30, Latest): pack TAA off also when shaders start disabled, animated pack dither, RCAS
sharpening, no jitter on join, entity motion vectors, FG matrices in double, memory notice (same voxy-vulkan 0.1.1).
viris-v0.3.0 (2026-11-01, Latest, title "VIRIS 0.3.0 + SIRIS 1.1.2 (Minecraft 26.3)"): `viris-0.3.0+mc26.3.jar` (rename,
shader pack LODs) + unchanged `siris-1.1.2+mc26.3.jar`.
Modrinth (token `MODRINTH_TOKEN` in `.env`, API v2 with header `Authorization: <token>`): `siris-dlss` (id NLzgnD4s,
1.1.2) and `viris` (id IHYZKsvL, 0.3.0, created 2026-11-01 by API: POST /v2/project as draft with icon, POST /v2/version
with dependencies Voxy/Sodium/Fabric API required, Vitrail/SIRIS optional, then PATCH /v3/version `environment:
client_only` (else "select_environment" blocks) and PATCH /v2/project `status: processing`). Both waiting for review.
New Modrinth versions: same POST /v2/version (version_type release, game_versions 26.3, loaders fabric).
v1.1.1 (2026-10-31): orange theme `#FF6A3D` (the icon's ember) for the settings screen headers, Sodium page theme
(`setBaseThemeRGB`) and README badges; icon in Sodium's list (`setNonTintedIcon(iris-dlss:icon.png)`). Notes in
`mod/build/release-notes-1.1.1.md`. Theme colour for anything new: `#FF6A3D` (amber `#FFB547`, ground `#17140F`).
v1.1.0 (2026-10-31, created by the user): first 1.x, PYRAXIS icon (frame stack: `.github/assets/pyraxis-icon.svg`, mod icon
`assets/iris-dlss/icon.png` rendered from it at 128 px), cleanup; notes in `mod/build/release-notes-1.1.0.md`.
v0.7.7 (2026-10-31, created by the user; auto mode blocks `gh release create`, give the PowerShell command
`& "C:\Program Files\GitHub CLI\gh.exe" release create ... --notes-file ...`): x jitter sign fixed (no shaking), Lunar Turbo
entity modes off with Vitrail (armor stands, sign text). Same voxy-vulkan 0.1.1.
v0.7.6 (2026-10-30): wWaypoints icons get their waypoint's motion vectors (no flicker with FG).
v0.7.5 (2026-10-30): wWaypoints icons move with FG, Vitrail join reload skipped for tagless packs.
v0.5.0/v0.6.0 notes start with an "Outdated, needs the archived fork" line. Next release: bump `version` in
`mod/build.gradle` (and `viris/build.gradle` if it changed), README notes, keep the asset names.

## Working with the user

- Verify every change with screenshots/frame captures as above.
- Ask before pushing (memory: verified work may be committed and pushed right away), and before downloading
  third-party code (shader packs too: Solas, Complementary are free to download but not ours to redistribute).

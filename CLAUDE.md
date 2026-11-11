# SIRIS

Repository github.com/laurensanp/siris (MIT; was laurensanp/iris-dlss, then pyraxis-render, old URLs redirect): a Fabric mod for Minecraft 26.3 that runs DLSS Super
Resolution + Frame Generation inside the game's own Vulkan renderer (RenderPearl). Shader packs come from
**Vitrail** (separate mod, modrinth `vitrail-shaders`, github avpbynf/Vitrail-Shaders, LGPL-3.0, not ours,
never commit or ship it). `README.md` lists everything a player or developer needs and where it comes from.

Goal: shader packs (Solas) + DLSS SR/FG on Vulkan, in the user's Lunar Client. Works with **VIRIS** (Voxy LODs on Vulkan), its own repo since 2026-11-02: github.com/laurensanp/viris, local clone `../viris`, notes in its CLAUDE.md. Neither mod needs the other (tested alone 2026-11-02); docs only mention each other as "works with". Both repos have the same layout (`mod/`, `libs/`, `.github/assets/`) and README sections in the same order: keep them that way.

| Path | What |
|---|---|
| `dlss-bridge/` | native C++ bridge (`siris.dll`): NGX, DLSS SR, presenter + DLSS-G, `dlss_probe.exe` |
| `mod/` | the Fabric mod (Java), packs the bridge + NVIDIA DLLs into its jar |
| `testpacks/` | own small GLSL test packs (from the old Iris port) |
| `libs/` (ignored) | compile-only jar: `sodium-fabric-0.9.2+mc26.3.jar` (Modrinth) |
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
- Split 2026-11-02 (user's wish): `viris/` moved to its own repo with its history (filter-branch, package now
  `com.viris`). Its releases moved too (`viris-v0.3.x` here -> `v0.3.x` there); old `voxy-vulkan-0.1.x` jars stay on
  the 0.6-1.1.1 releases here.
- History of iris-dlss keeps Kiwious' Claude co-author lines (user decided not to rewrite).

## Status (2026-11-02)

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
  Could be reported upstream (ask the user first).
- **Bright mobs and hand at night with Solas (fixed 2026-11-03, was ours):** Vitrail's first pack read comes before the
  entity mesh carries its wider format, so it leaves no entity programs (log: `The game draws the cutout with
  VertexFormat[...] and this engine decodes ...`, `went back to the game's own shader`); the join reload repairs it, and
  `VitrailJoinReloadMixin` skipped that reload for tagless packs. Mobs were drawn by the game's shader, lit by its
  lightmap (lavender at night), without shadows. Now the first join always reloads, later ones skip. Packs with block
  tags and setups with VIRIS always reloaded, so they never had this: the user's report with VIRIS installed isn't
  explained by this alone, recheck in Lunar.
- RTX 3060 "stuck in the main menu, no clicks" (friend's report 2026-11-03, no log, went away without the mods): not
  reproduced, cause unknown; FG can't be it (no FG inputs in the menu, so the path there is the same on every GPU), and
  clicks through the presenter work here (dev: click on Quit Game closes the game). Hardened what could hang the render
  thread: slot wait 5 s then `presenter took no frame` -> game's swapchain (tested with a forced stall), render thread
  answers messages sent to the child window while it waits, also at shutdown (`pumpSentMessages`; probably the old
  watchdog crash in `destroyPresenter`), acquire 100 ms instead of forever, present wait dropped after 3 timeouts (each
  cost 200 ms; status line says `present wait stuck`). Need that friend's `latest.log` to know more.
- FG pacing without VSync (2026-11-03): status line has `motion judder` (rms of shown interval minus the time step of the
  moment a frame pictures; frame starts, generated frames evenly between). Dev x2: 1.4-1.7 ms; simulated x4 (repeated
  images): 0.8-1.0 ms. Tried a third pacer, present at moment + delay (rising with the slowest frame, as FSR 3 does):
  x2 0.7-0.9 ms but rendered 121 -> 110, latency +2 ms, x4 worse (1.0-1.1 ms); capped at 1.9 frames 1.0-1.3 ms, same fps
  loss; spacing by each frame's real length instead of the average: 1.7-2.0 ms. All dropped: don't retry. Bug found on
  the way: both threads shared one waitable timer, each stalled the other up to 50 ms (VSync path); now one each. New
  option "Limit to monitor" (`fgMonitorLimit`, default off): shown fps at refresh - refresh^2/3600 like Reflex. Not judged
  on a real monitor yet: the 2026-11-03 tests ran on a virtual display (1842x1036, 60 Hz, user away), where VSync gives 13 fps
  with the game's own swapchain too. Next: user compares it and VSync at 165 Hz (270 shown on 165 Hz tear, suspected main
  cause of "blocks jitter on fast mouse").
- Benchmark 2026-10-27, 1080p, Solas, render distance 8, static view, 4060 Ti, rendered fps: SR+FG 122 (245 shown),
  Quality 176, Performance 217, DLAA 123, off 146 (old fork in the same scene: 115/161/196/114/133).
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
  fzzy_config), Alternate Current, zfastnoise (+ zconfig); Voxy + VIRIS + voxy-server-side added 2026-11-01. DLSS Custom 80 % + sharpen 0.5 + FG x2,
  render distance 16 / simulation 8, mipmaps 4, Vitrail shadowdistance 12, Solas DOF off, shadowDistance 192,
  VL_SAMPLES 8. First run singleplayer: 176-209 fps shown / 92-105 rendered with Solas. Added 2026-10-30 (user
  approved downloads, sha512 checked against Modrinth): Lithium 0.26.3, More Culling 1.9.0 (+ Cloth Config from
  surv), Better Block Entities 1.4.0 (from surv, built for Sodium 0.9.3-beta.1): same world 185-199 shown /
  89-103 rendered, 1 % low 112-126 shown (was 98-108), worst join pause 446 ms (was 958/1658 ms), no errors.
  Not recommended for this setup: Nvidium (OpenGL only), AsyncParticles, Gnetum (HUD lag with FG), Ixeris,
  borderless-fullscreen mods (presenter), DH (LODs like Voxy).
- Lunar profile `just-optimized` (2026-11-09): Voxygen 3.0.0 (`voxyworldgenv3`, LOD generator for Voxy) crashes the
  integrated server with VMP (Very Many Players): both @Redirect `ChunkMap.isChunkTracked` in
  `ChunkMap$TrackedEntity.updatePlayer`, Voxygen's fails ("Scanned 0 target(s)"). VMP is `.disabled` there. Without VMP
  Voxygen runs (Lunar 5 min, dev: LODs drawn), but logs ~85-300 "chunk x,z failed: ReportedException: Exception
  generating new chunk" per minute in dev (fresh world too, a feature asking for a chunk outside its region,
  `LevelReader.getChunk`): Voxygen's own, not ours. Voxy SeedGen 0.3.1 (ARR, newest for 26.2) can't be ported cheaply:
  26.3 rewrote density functions (`levelgen/densityfunction/`), 397 missing references in ~7400 lines.
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
- Current Lunar install (2026-11-03): all three profiles have `siris-1.1.6+mc26.3.jar` (config `preset=m`,
  `configVersion=2`) + plain `vitrail-0.13.0-beta+mc26.3.jar`; surv and dlss-optimized also `viris-0.3.6+mc26.3.jar`,
  Voxy 0.2.20 and voxy-server-side (surv keeps an old `voxy-vulkan-0.1.0` as `.disabled`). Backups per step in each
  profile's `mods-backup-2026-11-0x-*/`. The user plays DLSS Optimized now (Complementary, render distance 24,
  simulation 32, Custom 80 % + FG x2; its Unbound settings: clouds at 400/496, ATMOSPHERIC_FOG, BORDER_FOG off, LIGHTSHAFT_BEHAVIOUR=0, compiled in dev). `diagnosticRecording=false`; Lunar's active settings profile is `Default`. 2026-11-04: dlss-optimized only has an unreleased build under the same jar name (`0e968f8`, UI Recomposition + license metadata; backups `mods-backup-2026-11-04-ui-recomposition/`, `mods-backup-2026-11-04-license-metadata/`), not started in Lunar by me.

## Build & test

- Gradle needs **JDK 25**: `JAVA_HOME=~/.jdks/jdk-25.0.4.1+1`. Use `--no-daemon`; "Could not connect to the Gradle
  daemon" happens often, `gradlew --stop` and rerun.
- Bridge: `dlss-bridge/fetch-deps.ps1`, `dlss-bridge/build.bat` (MSVC 2022, Build Tools are enough). Mod:
  `cd mod && gradlew build` -> `mod/build/libs/siris-*.jar`.
- Windows on Arm (2026-11-11, contributor's RTX Spark N1X, driver 621.14): `build.bat` builds for the machine's architecture
  (Arm64 uses DLSS SDK v310.9.16). An IMMEDIATE present carrying `VkPresentIdKHR` crashed in `nvogla64.dll` once the window
  filled the screen (every dev run at 3270x2180); present ids and present wait are FIFO-only now. A jar carries only the bridge
  of the machine that built it. x64 check (4060 Ti, 1080p fullscreen, FG x2, 1 run each): VSync unchanged (165 shown, 82
  rendered); no VSync 283-307 shown / 140-158 rendered / 10.4-11.1 ms (main 273-280 / 134-142 / 11.8-12.2). Without VSync
  `motion judder` now uses the hand-over time, not the on-screen time: not comparable with older no-VSync figures.
- Test loop: `cd mod && gradlew runClient -PtestWorld=vulkan-test` joins/creates the world, screenshot
  `mod/run/screenshots/dlss-test.png` after 200 ticks, quits (`DevTest`, `DevTestWorldMixin`). Options:
  `-PtestCommands="time set 1500;weather clear;tp @p -36 84 18 -90 20"` (tree/shadow view; `tp @p -36 82 18 180 8`
  = beach view), `-PtestPitch`, `-PtestTicks=600` (more for screenshots after loading), `-PwindowSize=1920x1080`,
  `-PtestHideGui=true` (F1, for gallery shots; overlay=off too), `-PtestRejoin=N` (leave the world after N ticks, join
  again, then the normal test), `-PdlssBenchmark` (logs `SIRIS benchmark:` lines), `-PdlssOff`, `-PdlssDebug=spin|walk` (FG capture into
  `mod/run/siris/captures`, only fires with Frame Generation). Look at the screenshot.
- Dev `options.txt`: `fullscreen:false` (else a focus change minimizes and caps at 10 fps),
  `inactivityFpsLimit:"minimized"` (else the 30 fps AFK limit hits benchmarks). Run benchmarks with nothing else
  loading the PC (±25 %). Logs written across midnight get lost (log roll), rerun.
- Test together with VIRIS: move `mod/run/mods-voxy/*` (Voxy, VIRIS' jar from `../viris/mod/build/libs`) into `mod/run/mods`, no shader pack,
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
- Grid of fine dashes over fog / light shafts with shader packs (2026-11-01, user's server shot): DLSS model K turns the
  packs' animated noise (Complementary VL mainly, also fog) into a 2-render-px lattice. Same view (dev, sunrise, y 150,
  Complementary, Quality + FG): K grid; E grainy, no grid; M clean (~1 ms more, 121 vs 135 rendered); no DLSS clean;
  unchanged without Voxy/VIRIS, without sharpening, with DLAA, without jitter, with pack TAA on. Default model is now
  M (listed first); `configVersion=2` in siris.properties, older configs with `preset=k` move to M once.
- Shaking for ~2 s after joining: history reset for 60 frames (new level) showed the raw jitter; jitter is now 0
  while `resetFrames > 0`.
- FG smear at tree/block edges while turning (2026-10-30): reproduced with `-Dsiris.debugSpin=6` (6 deg per
  rendered frame, ~100 fps): only generated frames smear (real frames clean); at 1.5 deg/frame generated frames are
  clean. Inputs checked: MVs match the measured image shift (7.27 vs 7.3 render px), clipToPrevClip's depth column
  is (0,0,1,0) for a pure turn, camera vectors orthonormal/right-handed, FOV/aspect match. Pale tree "copies" in
  slow turns are real fogged trees (same in `-Dsiris.bypass=true` raw frames, DLAA only). All presets alike.
  **Fixed 2026-11-11** by DLSS-G `mvecScale` 1 (PR #2): 4060 Ti, full model, x2, Complementary, beach view `tp @p -36 82 18
  180 0`, spin 6 deg: generated frames sharp (main: trees/wall smeared), position 0.49-0.51 (main 0.47-0.49; 1.5 deg 0.50
  both), fps unchanged. Measured per 64 px block (median shift of generated vs real); a global shift fails on parallax.
- Dev: `-Dsiris.testShots=N` (N screenshots on consecutive ticks), `-Dsiris.bypass=true` (DLAA: raw frame
  instead of DLSS), `-Dsiris.debugSpin=deg` with `-PdlssDebug=still` (any spin speed), debugLog prints FG params.
- Join hitch (Lunar `Pause - Unknown` 1.5 s + ~20 x 50-800 ms after joining): JFR showed Vitrail's
  `PackChoice.reloadIfTheWorldMoved` re-reading/compiling the whole pack because the registry stamp changed (only
  needed for block tags `%ns:path` in block.properties). `VitrailJoinReloadMixin` skips it (PackDefines.settle()
  by reflection) when that is the only reason and the pack names no tag (`ShaderPackTweaks.selectedPackUsesBlockTags`);
  dimension change / DH / LabPBR still reload, and since 1.1.4 every join with `viris` loaded (else the LODs lost
  their colour on the second join). Solas has no tags. Remaining join cost: Vitrail pipeline warm-up
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
- Settings benchmark for `DLSS Optimized` (2026-11-03, dev, 1080p, its mods and settings, Unbound, RD 12, Voxy, FG x2, Quality, M, no VSync, y 110 static, 5 samples of 10 s, rendered fps): baseline 4 runs + 1 89-92, 1 % low 60-65. Inside the noise: shadowdistance 8, SHADOW_QUALITY 1, CLOUD_QUALITY 1, DETAIL_QUALITY 0, SSAO off, Voxy distance 12, only the base mods (Lunar's other mods cost nothing measurable). WATER_REFLECT_QUALITY 1: 95-97, but water loses terrain reflections (visible). DLSS Performance only 99-104, FG off 117 (GPU 94-99 %), so resolution isn't the main cost. Nothing applied: no look-neutral gain. Not measured: moving camera, chunk loading, servers. shadowres75 run had 3 samples at 60 (loading or a hitch): rerun before reading anything into it.
- Scenario benchmark of the same profile (2026-11-03, 800 ticks, fresh copy of the world each run; fly = `tp @p ~1 ~ ~` every tick over new chunks in singleplayer, spin = 6 deg per tick, 60 cows, rain). Baseline: fly 66-84 rendered, 1 % low 19-20, worst gap 28-408 ms; spin ~100/67; mobs ~89/57; rain ~88/59. No gain: Sodium defer ONE_FRAME, Voxy 3 threads, shadowdistance 8, ScalableLux 0.3.0-alpha (fly 69-73/18-21). C2ME 0.4.2-alpha: fly 92-94/41-44, worst gap ~25 ms. BadOptimizations 2.4.1: fly 73-82/20-24, worst gap ~60 ms. Both: fly 90-94/42-45, worst ~25 ms; mobs 86/52, rain 90/50, spin 98/67 (one run each, within noise), same picture. Both installed in `dlss-optimized` (backup `mods-backup-2026-11-03-c2me-badopt/`), not started in Lunar by me. C2ME works on chunk generation, so servers shouldn't gain from it; not measured on a server.
- A/B 2026-11-04 (dev, 4060 Ti, 1080p, Complementary Reimagined, Quality, M, FG x2, static tree view, 3 runs each, nothing kept). `DLSSG.LiteCandidateBlendingEnabled`: rendered 150 -> 162, shown 301 -> 328, latency 11.7 -> 10.6 ms (FG off: 244 rendered), but generated frames show visible double images and blur on edges and textures at 1 and 3 deg per rendered frame; don't use. LOD bias by NVIDIA's formula (`log2(render/output) - 1`, unclamped, -1.58 instead of -0.58): close, beach and distant views show no visible gain, edge metric -1 to +3 %, still-frame flicker within noise; moire while moving not measured. Left at `log2(render/output)`, at most -1.
- DLSS-G User Interface Recomposition (2026-11-04, on, not released yet): alpha of the window-sized target is cleared before the GUI (after `refreshHudless` too, so wWaypoints icons stay world), the GUI's blending leaves its opacity there, `frameGenImages(view)` copies it to `uiAlphaTexture` (RGBA8, red; capture `fg_NNN_e_ui_alpha.png`) as `pUIAlpha`. Screenshots stay opaque, swapchain is opaque. Dev, 3 deg/frame spin: overlay text and crosshair clean in generated frames, before ghosted; mask correct, hand and waypoint icon not in it. Cost: rendered 146.5 -> 143.6, shown 293.5 -> 286 (3 runs each). If creating DLSS-G with it fails, the bridge retries without it, no log line. `-Dsiris.noUiRecomposition=true` = old behaviour. A GUI pipeline that doesn't write alpha would count as world. Not tested in Lunar, on RTX 50, with VIRIS.
- Driver 617.42, 4060 Ti (2026-11-04): `VK_NV_present_metering` listed and feature `presentMetering=1`, `VK_NV_low_latency2` listed (its feature not queried). SIRIS uses neither. Metering tried and removed the same day (`VkSetPresentConfigNV` in the present chain on the first frame of each batch, feedback 1, present OK; dev, 165 Hz, FG x2, Quality, 1 deg/frame spin, 3-6 runs each): no VSync judder 2.17 ms off, 2.11 with the CPU spacing kept, 2.31 sending the batch at once (rendered 109 -> 121, spacing worse); VSync 0.98 off, 0.98 at once. No gain here; RTX 50 and real x3/x4 not tested, there it may differ. VSync halves the judder (0.98 vs 2.17 ms) at 22 vs 15.6 ms latency, rendered capped at 82.
- Settings UI: Sodium pages "Settings"/"Advanced" (`SodiumOptions`) and Mod Menu screen (`DlssSettingsScreen`);
  overlay in the HUD (`HudMixin`). Screenshot them with `-PdlssScreen=mod|sodium|sodium2` (`DevScreens`).
- Multi frame generation: 2x-6x (`frameGenMultiplier`), limited to the GPU's `DLSSG.MultiFrameCountMax`
  (4060 Ti: 2x only). One DLSS-G evaluate per generated frame. Frames are paced evenly over the game's frame
  interval with and without VSync. 3x/4x verified in a friend's log (RTX 50, Lunar), and on screen 2026-11-11 (RTX Spark N1X).
- DLSS-G motion vectors (2026-11-11, contributor's RTX Spark N1X: Windows on Arm, 3270x2180 @ 120 Hz, G-SYNC): `mvecScale` is
  1 (render pixels), not 1/width as NGX's header suggests. With 1/width DLSS-G saw almost no motion; its optical-flow model
  hid that, but FG Lite (driver DLSS-FG Preset C) only cross-faded the real frames: x3 generated frames at 0.01 / 0.95 of the way
  (120 shown moved like 40), now 0.35 / 0.70. Check with `-PdlssDebug=spin` captures (`fg_NNN_a2_generated.png` etc. for x3/x4)
  and the image shift between them. It also fixed the old "FG smear at tree edges while turning" (checked on the 4060 Ti, see there).
- NVIDIA's RTX Remix for Minecraft (2026-11-12, github.com/NVIDIAGameWorks/rtx-remix-minecraft, Apache-2.0, MC 1.21.1): a path
  tracer that replaces the renderer (scene capture -> Remix D3D9 runtime = dxvk-remix); no shader packs, nothing to port. Its DLSS-G
  code (dxvk-remix `rtx_dlfg.cpp`) uses mvec scale (1,1) like ours, up to 6x, Reflex out-of-band markers, GPU-timestamp pacing.
  Taken from it: 5x/6x (`MultiFrameCountMax` up to 5; untested, 4060 Ti has 2x only). Reflex-style frame start (sleep until the GPU
  is about to finish the previous frame) tried and dropped: no gain in dev (FG 12.8 vs 12.9 ms, SR 7.5 vs 7.8 ms, fps same),
  because RenderPearl already waits for the GPU between frames, before input (frame start ~5 ms after the previous present);
  latency is already CPU + GPU (+ FG hold). Don't retry unless a setup shows frames queuing. The `latency` stat now runs from
  frame start (input) to the real frame's present (was from `siris_present`, ~2-3 ms less); benchmark lines log latency/judder.
- Hardware: FG needs RTX 40/50; on the RTX 3070 dev machine FG fails with
  `NVSDK_NGX_Result_FAIL_UnableToInitializeFeature` (expected). `dlss-bridge/out/dlss_probe.exe` reports SR/FG.

## Releases

`"C:\Program Files\GitHub CLI\gh.exe" release create <tag> <jars> -R laurensanp/siris --title "SIRIS <version>
(Minecraft 26.3)" --latest --notes-file <file>` (gh is logged in as laurensanp). Auto mode may block it unless the user
approved that exact release; then ask, or give the user the PowerShell command (`& "C:\Program Files\GitHub CLI\gh.exe"
release create ...`). Vitrail and Voxy are never attached (link to Modrinth). Next release: bump `version` in
`mod/build.gradle`, keep the asset name, attach only this mod's jar.

Modrinth (token `MODRINTH_TOKEN` in `.env`, API v2, header `Authorization: <token>`): `siris-dlss` (id NLzgnD4s), still
waiting for review. New version: POST /v2/version multipart (`data` JSON + `file`; version_type release, game_versions
26.3, loaders fabric, featured; deps: Sodium required, Vitrail + Mod Menu optional), then PATCH /v3/version `client_only`.
Gallery: 6 images.

Newest first:
- v1.3.0 (2026-11-12, Latest): Frame Generation up to 6x (driver-reported), latency stat from frame start. Modrinth nItwjslA.
- v1.2.2 (2026-11-11): Windows on Arm (PR #3, MichaelMKenny): present ids only with VSync (driver crash on Arm), bridge builds on Arm64; jar still x64 only. Modrinth saT2SUXs.
- v1.2.1 (2026-11-11): DLSS-G motion vectors in pixels (PR #2, MichaelMKenny): no FG smear on turns, x3/x4 move. Modrinth vNjHsXyG.
- v1.2.0 (2026-11-09): DLSS-G UI Recomposition (HUD without ghosts in generated frames), NVIDIA license metadata. Modrinth SQLIZhSf.
- v1.1.6 (2026-11-03): mobs keep the pack shader, presenter hardening, Limit to monitor. Modrinth pAtgPLdr.
- v1.1.5 (2026-11-02): VIRIS split off, links and notes only; no change in the game. Modrinth oaAeAt6U.
- v1.1.4 (2026-11-02): LODs keep colour on rejoin (join-reload skip off with VIRIS). Modrinth j6taTT1r.
- v1.1.3 (2026-11-01): DLSS model M by default (K's grid over pack noise). Modrinth 4vXqzP0M.
- v1.1.2 (2026-10-31): first as SIRIS (rename). v1.1.1 (2026-10-31): orange theme `#FF6A3D` (amber `#FFB547`, ground
  `#17140F`; theme colour for anything new), icon in Sodium's list. v1.1.0 (2026-10-31, created by the user): first
  1.x, PYRAXIS icon (frame stack). Notes in `mod/build/release-notes-1.1.x.md`.
- v0.7.7 (2026-10-31): x jitter sign, Lunar Turbo entity modes off. v0.7.6/0.7.5 (2026-10-30): wWaypoints icons with FG,
  Vitrail join reload skipped for tagless packs. v0.7.4: pack TAA off, animated dither, RCAS, entity motion vectors.
  v0.7.3/0.7.2: LunarCompat. v0.7.1: pacing without VSync, vertex buffer reuse. v0.7.0 (2026-10-27): first with
  Vitrail. v0.5.0/v0.6.0: old Iris fork era (notes start with an "Outdated" line).

## Licensing (checked 2026-11-04, not legal advice)

NVIDIA RTX SDKs license (`third_party/ngx/LICENSE.txt`) allows shipping the DLLs in the jar. MIT never covers them: `fabric.mod.json` lists both licenses, README says so and carries the attribution, no-endorsement line and a pointer to the guide's third-party notices (the guide asks for their full text; open: copy it in). No `logo=nvidia` badges: logos/marks need NVIDIA's written approval. `social-preview.png` still says "NVIDIA RTX" as text. Open, user's: Modrinth license field and page text, commercial notice if Modrinth monetization is on, written answer from NVIDIA (nvidia-rtx-license-questions@nvidia.com).

## Working with the user

- Verify every change with screenshots/frame captures as above.
- Ask before pushing (memory: verified work may be committed and pushed right away), and before downloading
  third-party code (shader packs too: Solas, Complementary are free to download but not ours to redistribute).

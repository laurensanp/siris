Experimental, Windows + NVIDIA RTX only. Install, requirements and Lunar notes: [README](https://github.com/laurensanp/siris#readme). Replace `siris-1.3.4+mc26.3.jar` with this one; your settings stay.

## SIRIS 1.4.0

- **Smooth skies in Frame Generation.** Since 1.2.0 the generated frames showed colour bands in smooth skies while you turned (most visible with Solas). SIRIS handed DLSS Frame Generation its HUD mask in the wrong form, so it treated the whole screen as HUD. Now the sky stays smooth, and the HUD still doesn't ghost.
- **Mob limbs move on their own.** Motion vectors now follow the arms, legs and heads of mobs and players, not just their bodies, so DLSS and Frame Generation can track swinging limbs. The difference is small at walking speed and mostly shows on close or fast mobs, and at Frame Generation 3x and above. No change in frame rate.
- **License notices.** The jar now carries the full third-party notices of NVIDIA's DLSS runtime (`THIRD_PARTY_NOTICES.md`), and an old unused file from before the rename is gone.

Works with [VIRIS](https://github.com/laurensanp/viris) if you want Voxy LODs on Vulkan. It is not required.

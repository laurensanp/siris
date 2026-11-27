Experimental, Windows + NVIDIA RTX only. Install, requirements and Lunar notes: [README](https://github.com/laurensanp/siris#readme). Replace `siris-1.3.3+mc26.3.jar` with this one; your settings stay.

## SIRIS 1.3.4

- **Sharper DLSS with shader packs** ([#8](https://github.com/laurensanp/siris/pull/8), thanks SpinGiantCRM). Shader packs read the camera a second time for their shadows, and that moved the DLSS jitter forward twice per frame. DLSS was then told a different offset from the one the frame was drawn with, and it only got half of its sample positions. At 4K that showed as fine vertical lines in the sky and a softer picture. Now only the frame's own camera is jittered.

Works with [VIRIS](https://github.com/laurensanp/viris) if you want Voxy LODs on Vulkan. It is not required.

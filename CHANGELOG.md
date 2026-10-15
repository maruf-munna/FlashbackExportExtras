# Changelog

Newest first, headed by `mod.version` (`gradle.properties`).

## 1.2.0-dev - 2026-10-15
- **Export Transparent (Trans layer)**, OpenEXR options, on by default (Minecraft 26.3, Video Settings > Improved
  Transparency on, no Iris shader pack): glass, water, particles, rain and clouds go to their own EXR layer `Trans`
  (premultiplied RGBA) + `TransDepth`; `Combined` and `Depth` then hold the solid world (and the hand), the same layers
  as a Flashplus live take. The game's own picture is not changed. Off, or Improved Transparency off: as before (the
  window shows an orange note). Not combined with Scene-linear HDR color.
- **Camera in every EXR header:** `mc.frame`, `mc.camera.position` (x y z, Minecraft world), `mc.camera.rotation` (yaw
  pitch), `mc.camera.fov` (vertical, as rendered), `mc.channels`. Blender shows them under Image > Metadata.
- Test: 26.3, Improved Transparency on, a scene with stained glass and water; export OpenEXR with Export Transparent:
  `Combined` shows the world behind the glass without its tint, `Trans` the glass, `TransDepth` < `Depth` on the
  glass; the header holds the camera.

## 1.1.2-dev - 2026-10-08
- **The first-person hand is the near plane in the EXR `Depth.Z`** (Minecraft 26.2 and 26.3, no shader pack). The depth
  was copied before the game drew the hand, so hand pixels held the depth of the world behind the hand and a 3D object
  combined by depth in Blender could cover the hand. Now, right after the hand is drawn, its pixels are set to 0.05 m
  (0.0 with linear depth off) on the GPU before the readback, like Flashplus' live depth.
- Test: export an EXR sequence in first person with the hand in view, open a frame's `Depth.Z`: 0.05 on every hand
  pixel, the world's distances elsewhere; in Blender Z Combine a 3D object between the hand and the world stays behind
  the hand.

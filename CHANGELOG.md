# Changelog

Newest first, headed by `mod.version` (`gradle.properties`).

## 1.1.2-dev - 2026-10-08
- **The first-person hand is the near plane in the EXR `Depth.Z`** (Minecraft 26.2 and 26.3, no shader pack). The depth
  was copied before the game drew the hand, so hand pixels held the depth of the world behind the hand and a 3D object
  combined by depth in Blender could cover the hand. Now, right after the hand is drawn, its pixels are set to 0.05 m
  (0.0 with linear depth off) on the GPU before the readback, like Flashplus' live depth.
- Test: export an EXR sequence in first person with the hand in view, open a frame's `Depth.Z`: 0.05 on every hand
  pixel, the world's distances elsewhere; in Blender Z Combine a 3D object between the hand and the world stays behind
  the hand.

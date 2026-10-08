<div align="center">
  <img src="src/main/resources/assets/flashbackexportextras/icon.png" width="128" alt="Totem Doll icon">
  <h1>Flashback Export Extras</h1>
  <p>
    Flashback Export Extras 是一个 Fabric 模组扩展，用于增强 Flashback 的 Minecraft 回放导出功能。
  </p>
</div>

[English](README.md) | 中文

## 功能简介

- 导出深度图。
- 导出包含颜色和 `Depth.Z` 通道的多层 OpenEXR。第一人称手部在 `Depth.Z` 中为近平面（0.05 m），按深度合成（Blender Z Combine）时 3D 物体不会挡住手（Minecraft 26.2 及以上，未启用光影包时）。
- 可导出场景线性 HDR 颜色，用于 OpenEXR 后期处理。
- 在安装 HDR mod 时可以导出 HDR10 视频。
- 可以用 GLB、USDA、JSON、JSX 和 Lua 格式导出摄像机路径，供 Blender、After Effects 与 Fusion 使用。

OpenEXR 输出主要用于 Blender、After Effects 等软件进行合成和后期处理。颜色、深度和摄像机路径按照相同的帧编号导出，便于逐帧匹配。

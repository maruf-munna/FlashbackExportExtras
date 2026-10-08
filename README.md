<div align="center">
  <img src="src/main/resources/assets/flashbackexportextras/icon.png" width="128" alt="Flashback Export Extras icon">
  <h1>Flashback Export Extras</h1>
  <p>
    Flashback Export Extras is a Fabric add-on that extends Flashback's Minecraft replay export features.
  </p>
</div>

English | [中文](README_zh.md)

## Features

- Export depth maps.
- Export multi-layer OpenEXR files containing color and `Depth.Z` channels.
- Export scene-linear HDR color for OpenEXR post-processing.
- Export HDR10 video when HDR Mod is installed.
- Export camera paths for Blender, After Effects, and Fusion in GLB, USDA, JSON, JSX, and Lua formats.

OpenEXR output is mainly intended for compositing and post-processing in Blender, After Effects, and other software. Color, depth, and camera path data are exported with matching frame numbers for per-frame alignment.

## Build and deploy to an instance

Set `minecraft_instance_dir` in `versions/<version>/gradle.properties` to the root folder of your Minecraft or Modrinth instance. Quotes around a path with spaces are optional. The task creates the instance's `mods` folder if needed, removes only older jars of this mod, and copies in the freshly remapped jar.

```properties
# versions/26.3/gradle.properties
minecraft_instance_dir=C:/Users/you/AppData/Roaming/ModrinthApp/profiles/My Instance
```

```powershell
.\gradlew.bat :26.3:buildAndDeploy
```

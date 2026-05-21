# MapAccel

MapAccel is an experimental Forge 1.20.1 mod for predictive chunk preloading and terrain-preview acceleration.

It is designed around high-speed movement tests where normal chunk generation, save I/O, and server-thread work become the bottleneck.

## Current Features

- Dynamic movement prediction based on player speed and direction
- Thin forward-corridor planning at high speed
- OpenCL terrain-preview batching
- Server pressure control based on estimated TPS
- Seed-based `SurfacePreview`, `ShellPreview`, and `IslandPreview` modes
- Dirty chunk tracking for large post-generation edits
- Early `RegionSnapshotCache` foundation for modified terrain/building regions
- Cooperative hash validation packets
- Resource reporting from installed clients
- Detailed diagnostic logging for:
  - planned chunks
  - requested chunks
  - estimated write pressure
  - GPU preview work
  - dirty/snapshot state
  - client resources

## Important Limitations

MapAccel does not replace Minecraft's final chunk generator.

The GPU/OpenCL path currently computes predictive terrain preview data. Final chunk registration, lighting, POI, block entities, saving, and vanilla/Forge worldgen integration still run through the Minecraft server.

This means GPU usage can improve prediction and prioritization, but it cannot fully remove server-thread bottlenecks by itself.

## Build

```powershell
.\gradlew.bat build
```

Use the JarJar build for OpenCL testing:

```text
build/libs/mapaccel-0.1.0-all.jar
```

Do not install both `mapaccel-0.1.0.jar` and `mapaccel-0.1.0-all.jar` at the same time.

## Test Notes

Observed local test behavior:

- unloaded chunks: practical around creative dash flight 3x
- already loaded chunks: practical around 7x
- higher speeds are mostly limited by official chunk generation, lighting, save I/O, and packet delivery

## License

MIT

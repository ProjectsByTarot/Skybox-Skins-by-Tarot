# Skybox Skins by Tarot

Client-side Forge 1.8.9 mod that turns player skins into windows onto a skybox panorama. Local only; mobs and NPCs are unaffected.

## Usage
- Middle-click a player to toggle their window. Rules are saved by username, and your own name starts disabled.
- Requires OneConfig. Open settings with `/skyboxskins`; reload images with `/skyboxskins reload`.

## Custom skybox
- Import images in the **Images** tab: 2:1 equirectangular or 3x2 cubemap atlas (`Bottom, Top, South / West, North, East`), from 16x16 to 8 MP.
- Saved to `config/skyboxskins/skyboxes/`.
- Optional 64x64 black/white skin mask at `config/skyboxskins/mask.png` (white = skybox).
- Preview atlas orientation with `skybox-orientation-simulator.html`.

## Build
Java 8: `gradlew.bat build` → jar in `build/libs/`.

# Skybox Skins by Tarot

A client-side Minecraft Forge mod for 1.8.9. Aim at another player and press
the middle mouse button to toggle their skybox window off or on. Connected
players are enabled by default. Your username is added to the Players rules
with its window off by default; enable that rule to apply the effect to your
own skin. Mobs and NPCs are not changed.
The effect is local to your client and does not affect other players' views.
The shader replaces the base skin rendering; the outer hat/jacket/sleeve/pants
skin layer is hidden while the window is active.

Install OneConfig for Forge 1.8.9 to access the **Skybox Skins by Tarot**
settings. OneConfig is the settings interface: it controls the global toggle,
menu keybind, image selection and reset/reload actions, and persistent
per-username player rules. The menu keybind is unbound by default. Open the
OneConfig settings with `/skyboxskins` (or `/skyboxskins settings`,
`/skyboxsettings`); use
`/skyboxskins reload` to reload custom images.

The replacement uses a shader to sample the panorama by camera direction,
making the selected skin regions look like windows into a distant skybox as
you move the view. A mask controls which parts of the skin become windows.
Both the standard and slim player models are supported.

## Custom skybox

Add panoramas from OneConfig's **Images** tab; each imported image
is kept in the saved skybox library, and selecting a new one does not replace
the others. Both 2:1 equirectangular panoramas and MCPatcher/OptiFine cubemap
atlases are supported. The cubemap atlas is 3 columns by 2 rows: `Bottom`,
`Top`, `South` on the first row, then `West`, `North`, `East`. It is converted
to the shader's panorama format using the faces' inside-the-cube orientation
when loaded. Images must be at least 16x16 and no larger than 8 megapixels.
Saved skyboxes are stored under `config/skyboxskins/skyboxes/`. An existing
`config/skyboxskins/skybox.png` is automatically imported into the library
when upgrading. The mod also indexes supported images placed directly in
`config/skyboxskins/` or `config/skyboxskins/skyboxes/` at startup, copying them
into the library without removing the originals. Use **Scan image folder** in
the Images settings to pick up files added while the game is running. `mask.png`
and the legacy `skybox.png` in the root folder are reserved. Use the
**Images** tab to choose the global skybox from the visible dropdown or remove
saved images. In the **Players** table, choose Global, Built-in, or a saved
panorama from each player's skybox dropdown; assignments are saved by username.
Optionally put a 64x64 black-and-white skin UV mask at
`config/skyboxskins/mask.png`. White mask pixels show the skybox and black
pixels leave the player's normal skin visible. Without a custom mask, the
whole skin is a window.

The in-game image picker supports typed or pasted folder/image paths, a
Pictures shortcut, search, and paging through PNG, JPG, BMP, and GIF files.
It avoids native system dialogs; imported images are validated and saved as
PNG. OneConfig can use the built-in skybox, reset the mask, open the image
folder, and reload changes.
The Players tab stores window rules by username, including players who are
offline. The current Minecraft username is automatically added with its window
disabled, while other unlisted players have windows enabled. Newly added rules
also start disabled. Edit a username, toggle its window, or remove its rule in
the table. Middle-clicking a connected player toggles and saves that player's
rule, so it remains in effect after reconnecting or restarting Minecraft.
Missing or invalid files use the built-in panorama or full-skin mask, and
invalid images are reported in the log and picker.

## Orientation simulator

Open [skybox-orientation-simulator.html](./skybox-orientation-simulator.html) in
a browser to preview a 3x2 atlas from inside the cube. Load an image, drag to
look around or snap to a face, and adjust face rotations and flips independently.
The simulator can copy a JSON orientation report for comparing mappings.

## Build

Use Java 8. The checked-in Gradle wrapper downloads and runs the required
Gradle 2.14.1 version:

```text
gradlew.bat build
```

To launch the development client:

```text
gradlew.bat setupDecompWorkspace runClient
```

The mod jar is written to `build/libs/`.

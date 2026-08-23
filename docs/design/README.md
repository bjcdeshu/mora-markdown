# Mora launcher icon source

`mora-icon-v0.3.3.svg` is the source of truth for Mora's v0.3.3 launcher mark.
It preserves David's selected original C2M-H1 silhouette and uses a `0 0 1024
1024` viewBox.

Geometry recorded from the approved vector:

- visible bounds: approximately `x 232.7–791.3`, `y 317.7–725.9`;
- visible width / height: approximately `54.6% / 39.9%` of the viewBox;
- rasterized fill area: approximately `11.84%` of the full viewBox;
- optical fill centroid: approximately `(460.3, 558.1)`;
- the complete mark stays inside Android's `66/108` adaptive safe zone.

Color tokens remain in `app/src/main/res/values/colors.xml`:

- Indigo launcher / Ink background: `#17243A`
- Pine: `#1F4B3E`
- Night: `#0B1220`
- Warm paper: `#F7F3EA`

`social-preview-template.png` is the immutable input for the fixed 1280 × 640
preview composition. The exporter copies that template, resets the launcher-mark
slot, and draws the current SVG mark into the generated
`docs/assets/social-preview.png`. Keeping the template separate prevents repeat
exports from accumulating raster changes.

From the repository root, use JDK 17 or newer to regenerate the Android vector
resources, all legacy density PNGs, and the social preview:

```powershell
java tools/ExportMoraLauncherIcons.java
```

To check every generated target without writing to the working tree, run:

```powershell
java tools/ExportMoraLauncherIcons.java --verify
```

Verification reports missing, stale, and unexpected generated targets and exits
non-zero when regeneration is required. The exporter reads the SVG path, Android
color resources, and immutable preview template; writes identical foreground
geometry for Indigo (Ink), Pine, Night, and monochrome; and generates the legacy
`mdpi` through `xxxhdpi` squircle/round PNGs. Adaptive icon XML and launcher
aliases continue to reference the generated Android resources.

Do not edit generated VectorDrawable path data, launcher PNGs, or
`docs/assets/social-preview.png` independently. Change the SVG or the immutable
preview template as appropriate, rerun the exporter once, then run `--verify` and
validate vector parity, PNG dimensions, adaptive safe-zone bounds, small-size
appearance, and the full Android build gate.

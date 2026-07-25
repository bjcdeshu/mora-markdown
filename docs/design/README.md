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

- Ink: `#17243A`
- Pine: `#1F4B3E`
- Night: `#0B1220`
- Warm paper: `#F7F3EA`

From the repository root, use JDK 17 or newer to regenerate the Android vector
resources and all legacy density PNGs:

```powershell
java tools/ExportMoraLauncherIcons.java
```

The exporter reads the SVG path and Android color resources, writes identical
foreground geometry for Indigo, Pine, Night, and monochrome, generates the
legacy `mdpi` through `xxxhdpi` squircle/round PNGs, and updates the mark in the
1280 × 640 social preview without changing its screenshots or typography.
Adaptive icon XML and launcher aliases continue to reference the generated
Android resources.

Do not edit generated VectorDrawable path data or PNGs independently. Change the
SVG, rerun the exporter, then validate vector parity, PNG dimensions, adaptive
safe-zone bounds, small-size appearance, and the full Android build gate.

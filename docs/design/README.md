# Mora launcher icon source

`mora-icon-v0.3.3.svg` is the source of truth for Mora's launcher mark (updated in v0.4.0 to the **Origami Page M** multi-plane design).
It uses a `0 0 1024 1024` viewBox and stays strictly within Android's `66/108` adaptive icon safe zone (`x 226–798`, `y 252–776`).

Multi-plane layers in the SVG source:

- `mark-shadow`: `#38060D1A` subtle ground shadow
- `right-inner-facet`: `#6E92BC` deep cool slate-blue inner fold
- `left-inner-facet`: `#C4D5E8` luminous ice-slate inner fold
- `right-outer-wing`: `#E8E1D3` warm parchment right page wing
- `left-outer-wing`: `#F7F3EA` (`@color/mora_icon_*_foreground`) warm ivory left page wing
- `bookmark-shadow`: `#2E060D1A` spine crease shadow
- `center-bookmark`: `#5CB2EE` calm sky-cyan reading ribbon
- `monochrome-silhouette`: single-color negative-space crease silhouette for Android 13+ themed icons

Background color tokens in `app/src/main/res/values/colors.xml`:

- Indigo launcher / Ink background: `#17243A`
- Pine: `#1F4B3E`
- Night: `#0B1220`
- Warm paper foreground token: `#F7F3EA`

From the repository root, use JDK 17 or newer to regenerate the Android vector resources and verify all 25 launcher assets:

```powershell
java tools/ExportMoraLauncherIcons.java
java tools/ExportMoraLauncherIcons.java --verify
```

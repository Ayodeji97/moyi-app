# App icon

`make_icon.py` draws Moyi's app icon and writes every platform file from one geometry, so
the icon can be regenerated rather than redrawn. Run it from the repository root:

```sh
python3 tools/icon/make_icon.py /path/to/store-images   # the argument is optional
```

It needs the Python standard library, `rsvg-convert` and ImageMagick (`magick`).

## The mark

The "o-dot" (ọ) mark, small cut, from `docs/superpowers/specs/2026-09-07-logo-and-app-icon-design.md`
§3 in the parent Gratitude folder. It is used here **as amended by the owner's choice of
variant V1, "open gap" (2026-10-30)**.

On a 160-unit canvas, every shape a true circle:

| Part | Geometry |
|---|---|
| Ring | centre (80, 58), radius 36, stroke 13, unfilled |
| Dot | centre (80, 124.5), radius 15, filled |
| Gap, ring outer edge to dot top | 9 |
| Mark box | 85 wide, 124 high (y 15.5 to 139.5), centred on its own bounding box |

**The amendment.** The spec's small cut has a dot of radius 18 and a gap of about 5.5. At
24 px that gap fused, so the ring and dot read as the ♀ symbol. V1 shrinks the dot to
radius 15 and opens the gap to 9.

## Colours and placement

Ink on surface (Rosewood, `tokens.md` §1): dark `#F8EFE9` on `#171313`, light `#2A2020` on
`#FFF9F5`.

| Output | What it is |
|---|---|
| iOS `icon-any.png` | The dark tile, mark 62.5 % of the tile's height. The app is dark-first, so this is the default in either mode |
| iOS `icon-dark.png` | The mark in dark ink on transparency; iOS 18 supplies the dark background |
| iOS `icon-tinted.png` | Greyscale: white mark on black; the system tints by luminance |
| Android `ic_launcher_foreground.xml` | The mark, 48 dp high in the 108 dp adaptive canvas, inside the 66 dp safe zone. Also the monochrome (themed icon) layer |
| Android background | `@color/ic_launcher_background`, dark surface |
| Android `drawable-v31/splash_mark.xml` | The Android 12+ splash icon: 128 dp in the 288 dp canvas (the launcher's 48 / 108), in `@color/ink` on `surface`, both following the system mode |
| Android `mipmap-*/ic_launcher*.png` | API 24 and 25 only: the adaptive icon as a launcher masks it (the visible 72 dp), square and round |
| Store images (argument only, not committed) | `app-store-1024.png` (no alpha) and `play-store-512.png` |

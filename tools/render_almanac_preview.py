#!/usr/bin/env python3
"""Render the 万年历 / 宜忌 widget preview for the widget picker.

Mirrors res/layout/almanac_widget_preview.xml: rounded MD3 surface, a large
Gregorian day on the left, and the lunar date, ganzhi pillars and 宜 / 忌 rows
on the right.

Needs a CJK-capable font. Set ALMANAC_CJK_FONT to override the path; the
default is the Noto Sans CJK SC shipped inside the `mplfonts` PyPI wheel,
which is how the sandbox obtains one (direct font downloads are blocked).
"""
import os

from PIL import Image, ImageDraw, ImageFont

CJK = os.environ.get(
    "ALMANAC_CJK_FONT", "/tmp/fonts/NotoSansCJKsc-Regular.otf")

# Logical size in dp; the widget targets 5x2 cells.
W_DP, H_DP = 250, 118
SS = 6  # supersample factor

# Must stay in sync with res/values/colors_widget.xml (light palette).
SURFACE = (237, 243, 251, 255)
ON_SURFACE = (26, 28, 30, 255)
ON_SURFACE_VAR = (67, 71, 78, 255)
YI_RED = (198, 40, 40, 255)      # @color/almanac_yi
JI_INK = (55, 71, 79, 255)       # @color/almanac_ji
WHITE = (255, 255, 255, 255)

# Sample values; these match the almanac_preview_* strings.
DAY = "22"
MONTH = "2026年9月"
LUNAR = "八月十二"
WEEKDAY = "星期二  秋分"
GANZHI = "丙午年 丁酉月 戊辰日"
YI = "祖祭 祈福 开光 结婚"
JI = "动土 破土 安葬"


def f(size_dp):
    return ImageFont.truetype(CJK, int(size_dp * SS))


def render():
    img = Image.new("RGBA", (W_DP * SS, H_DP * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Container: 24dp corners (widget_corner_radius).
    d.rounded_rectangle([0, 0, W_DP * SS, H_DP * SS],
                        radius=24 * SS, fill=SURFACE)

    pad = 14

    # --- Left: the big day number over its month, centred in a 76dp column.
    col_w = 76
    cx = pad + col_w / 2
    day_font = f(40)
    dw = d.textlength(DAY, font=day_font)
    d.text((cx * SS - dw / 2, int(20 * SS)), DAY, font=day_font, fill=ON_SURFACE)

    m_font = f(10.5)
    mw = d.textlength(MONTH, font=m_font)
    d.text((cx * SS - mw / 2, int(72 * SS)), MONTH, font=m_font,
           fill=ON_SURFACE_VAR)

    # --- Right: lunar date, weekday/term, ganzhi, then the 宜 / 忌 rows.
    x = pad + col_w + 12
    d.text((x * SS, int(13 * SS)), LUNAR, font=f(15), fill=ON_SURFACE)
    d.text((x * SS, int(35 * SS)), WEEKDAY, font=f(10.5), fill=ON_SURFACE_VAR)
    d.text((x * SS, int(51 * SS)), GANZHI, font=f(9.5), fill=ON_SURFACE_VAR)

    badge = 20
    label_font = f(11.5)
    text_font = f(11.5)
    for y, color, label, body in (
            (70, YI_RED, "宜", YI),
            (94, JI_INK, "忌", JI)):
        d.ellipse([x * SS, y * SS, (x + badge) * SS, (y + badge) * SS],
                  fill=color)
        lw = d.textlength(label, font=label_font)
        # Nudge up slightly: CJK glyphs sit low in their em box.
        d.text(((x + badge / 2) * SS - lw / 2, (y + 3.2) * SS),
               label, font=label_font, fill=WHITE)
        d.text(((x + badge + 8) * SS, (y + 3.6) * SS),
               body, font=text_font, fill=ON_SURFACE)

    return img


def main():
    if not os.path.exists(CJK):
        raise SystemExit(
            f"CJK font not found at {CJK}. Set ALMANAC_CJK_FONT to a font "
            f"containing Han glyphs (e.g. Noto Sans CJK SC).")
    base = render()
    for bucket, scale in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2)):
        out = base.resize(
            (int(W_DP * scale), int(H_DP * scale)), Image.LANCZOS)
        path = (f"app/src/main/res/drawable-{bucket}/"
                "almanac_widget_preview_image.webp")
        out.save(path, "WEBP", lossless=True, quality=100, method=6)
        print(f"wrote {path} {out.size}")


if __name__ == "__main__":
    main()

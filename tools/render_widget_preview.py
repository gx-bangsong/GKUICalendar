#!/usr/bin/env python3
"""Render the MD3 agenda-widget preview used by the widget picker.

The output intentionally mirrors res/layout/appwidget.xml: a rounded surface
container, a large month headline with a supporting date line, a filled FAB,
and rounded event chips tinted with calendar colours.

Drawn at a supersampled scale then downsampled, which gives clean antialiased
corners without needing a real Android render pass.
"""
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# Logical size in dp, matching the widget's 4x3 target cell footprint.
W_DP, H_DP = 180, 150
SS = 8  # supersample factor

# Palette must stay in sync with res/values/colors_widget.xml (light).
SURFACE = (237, 243, 251, 255)
ON_SURFACE = (26, 28, 30, 255)
ON_SURFACE_VAR = (67, 71, 78, 255)
FAB = (11, 94, 158, 255)
WHITE = (255, 255, 255, 255)

# Sample events. Colours mirror widget_preview_chip_1..3 in colors_widget.xml
# so this static image and the API 31+ previewLayout agree. The third chip is
# deliberately pale so the preview shows the contrast-aware dark text.
WHITE_T = (255, 255, 255, 255)
WHITE_D = (255, 255, 255, 205)
DARK_T = (26, 28, 30, 255)
DARK_D = (26, 28, 30, 205)
EVENTS = [
    ("Team standup", "9:00 AM", (11, 128, 67, 255), WHITE_T, WHITE_D),
    ("Design review", "11:30 AM", (63, 121, 186, 255), WHITE_T, WHITE_D),
    ("Lunch with Sam", "1:00 PM", (246, 191, 38, 255), DARK_T, DARK_D),
]


def f(path, size_dp):
    return ImageFont.truetype(path, int(size_dp * SS))


def rr(draw, box, radius_dp, fill=None, outline=None, width_dp=0):
    draw.rounded_rectangle(
        [int(v * SS) for v in box],
        radius=int(radius_dp * SS),
        fill=fill,
        outline=outline,
        width=int(width_dp * SS) if width_dp else 0,
    )


def render():
    img = Image.new("RGBA", (W_DP * SS, H_DP * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Container: 24dp corners, full bleed.
    rr(d, (0, 0, W_DP, H_DP), 24, fill=SURFACE)

    pad = 14

    # Header: month headline + supporting weekday/date line.
    d.text((pad * SS, int(11.5 * SS)), "July", font=f(FONT, 22), fill=ON_SURFACE)
    d.text((pad * SS, int(36 * SS)), "Tue, Jul 11", font=f(FONT, 9), fill=ON_SURFACE_VAR)

    # Filled FAB, right-aligned in the header.
    fab = 38
    fx1, fy1 = W_DP - pad - fab, 12
    rr(d, (fx1, fy1, fx1 + fab, fy1 + fab), 12, fill=FAB)
    cx, cy, arm, thick = fx1 + fab / 2, fy1 + fab / 2, 8.5, 2.0
    d.rectangle(
        [int((cx - arm / 2) * SS), int((cy - thick / 2) * SS),
         int((cx + arm / 2) * SS), int((cy + thick / 2) * SS)], fill=WHITE)
    d.rectangle(
        [int((cx - thick / 2) * SS), int((cy - arm / 2) * SS),
         int((cx + thick / 2) * SS), int((cy + arm / 2) * SS)], fill=WHITE)

    # Event chips.
    y = 56
    chip_h = 27
    gap = 5
    for title, when, color, fg, fg_dim in EVENTS:
        rr(d, (pad, y, W_DP - pad, y + chip_h), 9, fill=color)
        d.text((int((pad + 9) * SS), int((y + 5.5) * SS)),
               title, font=f(FONT_BOLD, 8.5), fill=fg)
        d.text((int((pad + 9) * SS), int((y + 16) * SS)),
               when, font=f(FONT, 7.5), fill=fg_dim)
        y += chip_h + gap

    return img


def main():
    base = render()
    # mdpi is 1x; the widget picker scales from these buckets.
    for bucket, scale in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2)):
        out = base.resize(
            (int(W_DP * scale), int(H_DP * scale)), Image.LANCZOS)
        path = (f"app/src/main/res/drawable-{bucket}/"
                "calendar_widget_preview.webp")
        out.save(path, "WEBP", lossless=True, quality=100, method=6)
        print(f"wrote {path} {out.size}")


if __name__ == "__main__":
    main()

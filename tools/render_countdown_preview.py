#!/usr/bin/env python3
"""Render the countdown / memorial-day widget preview for the widget picker.

Mirrors res/layout/countdown_widget_preview.xml: rounded MD3 surface, the
"Countdown" headline, and two event chips with the day count on the right.
The second chip is deliberately pale (Banana) with dark text so the preview
advertises the contrast-aware text colour rather than hiding it.

Drawn supersampled then downsampled for clean antialiased corners.
"""
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# Logical size in dp. The widget targets 4x2 cells; this is sized to the
# natural content height so nothing is clipped in the picker.
W_DP, H_DP = 180, 152
SS = 8  # supersample factor

# Must stay in sync with res/values/colors_widget.xml (light palette).
SURFACE = (237, 243, 251, 255)
ON_SURFACE = (26, 28, 30, 255)

# widget_preview_chip_2 / _3
CHIP_BLUE = (63, 121, 186, 255)
CHIP_YELLOW = (246, 191, 38, 255)

WHITE = (255, 255, 255, 255)
WHITE_DIM = (255, 255, 255, 204)
DARK = (26, 28, 30, 255)
DARK_DIM = (26, 28, 30, 204)

# (title, subtitle, count, unit, chip colour, text, dim text)
CHIPS = [
    ("Trip to Kyoto", "Aug 14", "32", "days left",
     CHIP_BLUE, WHITE, WHITE_DIM),
    ("Our anniversary", "Year 7 \u00b7 Sep 2", "51", "days left",
     CHIP_YELLOW, DARK, DARK_DIM),
]


def f(path, size_dp):
    return ImageFont.truetype(path, int(size_dp * SS))


def rr(draw, box, radius_dp, fill):
    draw.rounded_rectangle(
        [int(v * SS) for v in box], radius=int(radius_dp * SS), fill=fill)


def render():
    img = Image.new("RGBA", (W_DP * SS, H_DP * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Container: 24dp corners (widget_corner_radius).
    rr(d, (0, 0, W_DP, H_DP), 24, SURFACE)

    pad = 14
    # Headline: "Countdown" at widget_title_text (22sp).
    d.text((pad * SS, int(11 * SS)), "Countdown",
           font=f(FONT, 20), fill=ON_SURFACE)

    y = 46
    chip_h = 44
    for title, subtitle, count, unit, color, fg, fg_dim in CHIPS:
        # Chip radius 16dp (widget_chip_radius).
        rr(d, (pad, y, W_DP - pad, y + chip_h), 14, color)

        # Left column: title + supporting line.
        d.text((int((pad + 12) * SS), int((y + 8) * SS)),
               title, font=f(FONT_BOLD, 9.5), fill=fg)
        d.text((int((pad + 12) * SS), int((y + 25) * SS)),
               subtitle, font=f(FONT, 8), fill=fg_dim)

        # Right column: the large figure over its unit, right-aligned.
        right = W_DP - pad - 12
        nf, uf = f(FONT_BOLD, 15), f(FONT, 6.5)
        nw = d.textlength(count, font=nf)
        uw = d.textlength(unit, font=uf)
        d.text((right * SS - nw - (uw - nw) / 2 if uw > nw else right * SS - nw,
                int((y + 7) * SS)), count, font=nf, fill=fg)
        d.text((right * SS - uw, int((y + 28) * SS)), unit, font=uf, fill=fg_dim)

        y += chip_h + 6  # widget_row_spacing

    return img


def main():
    base = render()
    for bucket, scale in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2)):
        out = base.resize(
            (int(W_DP * scale), int(H_DP * scale)), Image.LANCZOS)
        path = (f"app/src/main/res/drawable-{bucket}/"
                "countdown_widget_preview_image.webp")
        out.save(path, "WEBP", lossless=True, quality=100, method=6)
        print(f"wrote {path} {out.size}")


if __name__ == "__main__":
    main()

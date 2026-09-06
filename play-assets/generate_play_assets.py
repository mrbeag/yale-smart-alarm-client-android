#!/usr/bin/env python3
"""Generate deterministic Google Play artwork for Home Alarm."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
SCALE = 4

NAVY = "#173D68"
NAVY_DARK = "#0B243F"
GREEN = "#2DAE76"
WHITE = "#FFFFFF"
PALE_BLUE = "#DDEEFF"
MUTED_BLUE = "#A7CFFF"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    path = Path("/usr/share/fonts/truetype/dejavu") / name
    return ImageFont.truetype(str(path), size * SCALE)


def shield_mark(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = (value * SCALE for value in box)
    width = x1 - x0
    height = y1 - y0

    shield = [
        (x0 + width * 0.50, y0),
        (x0 + width * 0.92, y0 + height * 0.19),
        (x0 + width * 0.92, y0 + height * 0.51),
        (x0 + width * 0.86, y0 + height * 0.70),
        (x0 + width * 0.71, y0 + height * 0.85),
        (x0 + width * 0.50, y0 + height),
        (x0 + width * 0.29, y0 + height * 0.85),
        (x0 + width * 0.14, y0 + height * 0.70),
        (x0 + width * 0.08, y0 + height * 0.51),
        (x0 + width * 0.08, y0 + height * 0.19),
    ]
    draw.polygon(shield, fill=NAVY)

    house = [
        (x0 + width * 0.27, y0 + height * 0.49),
        (x0 + width * 0.50, y0 + height * 0.29),
        (x0 + width * 0.73, y0 + height * 0.49),
        (x0 + width * 0.68, y0 + height * 0.55),
        (x0 + width * 0.65, y0 + height * 0.52),
        (x0 + width * 0.65, y0 + height * 0.72),
        (x0 + width * 0.35, y0 + height * 0.72),
        (x0 + width * 0.35, y0 + height * 0.52),
        (x0 + width * 0.32, y0 + height * 0.55),
    ]
    draw.polygon(house, fill=WHITE)

    check = [
        (x0 + width * 0.43, y0 + height * 0.59),
        (x0 + width * 0.50, y0 + height * 0.65),
        (x0 + width * 0.64, y0 + height * 0.51),
    ]
    draw.line(check, fill=GREEN, width=max(8, int(width * 0.055)), joint="curve")


def vertical_gradient(size: tuple[int, int], top: str, bottom: str) -> Image.Image:
    width, height = (value * SCALE for value in size)
    image = Image.new("RGB", (width, height), top)
    pixels = image.load()
    top_rgb = tuple(int(top[i : i + 2], 16) for i in (1, 3, 5))
    bottom_rgb = tuple(int(bottom[i : i + 2], 16) for i in (1, 3, 5))
    for y in range(height):
        ratio = y / max(1, height - 1)
        colour = tuple(round(a + (b - a) * ratio) for a, b in zip(top_rgb, bottom_rgb))
        for x in range(width):
            pixels[x, y] = colour
    return image


def save_downsampled(image: Image.Image, filename: str, size: tuple[int, int]) -> None:
    output = image.resize(size, Image.Resampling.LANCZOS)
    output.save(ROOT / filename, format="PNG", optimize=True)


def make_icon() -> None:
    image = vertical_gradient((512, 512), PALE_BLUE, WHITE)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(
        (48 * SCALE, 48 * SCALE, 464 * SCALE, 464 * SCALE),
        radius=104 * SCALE,
        fill=WHITE,
        outline=MUTED_BLUE,
        width=6 * SCALE,
    )
    shield_mark(draw, (116, 76, 396, 436))
    save_downsampled(image, "home-alarm-play-icon.png", (512, 512))


def make_feature_graphic() -> None:
    image = vertical_gradient((1024, 500), NAVY, NAVY_DARK)
    draw = ImageDraw.Draw(image)

    # Gentle visual depth without compromising text readability.
    draw.ellipse(
        (690 * SCALE, -230 * SCALE, 1170 * SCALE, 250 * SCALE),
        fill="#214E7C",
    )
    draw.ellipse(
        (-160 * SCALE, 340 * SCALE, 300 * SCALE, 800 * SCALE),
        fill="#123456",
    )

    draw.rounded_rectangle(
        (70 * SCALE, 70 * SCALE, 370 * SCALE, 430 * SCALE),
        radius=72 * SCALE,
        fill=WHITE,
    )
    shield_mark(draw, (120, 94, 320, 406))

    draw.text((430 * SCALE, 105 * SCALE), "Home Alarm", font=font(62, bold=True), fill=WHITE)
    draw.text(
        (434 * SCALE, 190 * SCALE),
        "Secure control from phone, car and watch",
        font=font(24),
        fill=PALE_BLUE,
    )

    labels = ("AWAY", "HOME", "DISARMED")
    x = 434
    for label in labels:
        label_font = font(18, bold=True)
        left, top, right, bottom = draw.textbbox((0, 0), label, font=label_font)
        label_width = (right - left) // SCALE
        pill_width = label_width + 42
        draw.rounded_rectangle(
            (x * SCALE, 285 * SCALE, (x + pill_width) * SCALE, 337 * SCALE),
            radius=26 * SCALE,
            fill=GREEN,
        )
        draw.text(((x + 21) * SCALE, 300 * SCALE), label, font=label_font, fill=WHITE)
        x += pill_width + 18

    draw.text(
        (434 * SCALE, 370 * SCALE),
        "Confirmed alarm state",
        font=font(21),
        fill=MUTED_BLUE,
    )
    save_downsampled(image, "home-alarm-feature-graphic.png", (1024, 500))


if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    make_icon()
    make_feature_graphic()

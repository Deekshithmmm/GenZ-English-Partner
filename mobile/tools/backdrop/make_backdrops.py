"""Draws the app's aurora backdrops: a soft vertical gradient with blurred glows, light and dark.

Usage: python make_backdrops.py   (writes ../../assets/backdrop/{light,dark}.webp)
A little noise is added so the gradients don't band on 8-bit screens.
"""
import os
import random

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'assets', 'backdrop'))
W, H = 1080, 2340

THEMES = {
    'light': {
        'stops': [(0.0, (246, 243, 255)), (0.45, (242, 237, 255)), (1.0, (255, 243, 228))],
        # (x, y, radius, colour, opacity), in fractions of the width/height.
        'glows': [
            (0.92, 0.08, 0.55, (156, 132, 255), 0.55),
            (0.05, 0.16, 0.38, (120, 214, 255), 0.28),
            (0.0, 0.55, 0.5, (255, 196, 71), 0.36),
            (1.0, 0.78, 0.5, (255, 150, 200), 0.32),
            (0.35, 1.02, 0.45, (170, 150, 255), 0.3),
        ],
    },
    'dark': {
        'stops': [(0.0, (16, 12, 48)), (0.5, (23, 17, 72)), (1.0, (33, 24, 88))],
        'glows': [
            (0.95, 0.06, 0.6, (112, 82, 255), 0.62),
            (0.02, 0.2, 0.42, (46, 170, 255), 0.22),
            (0.0, 0.62, 0.5, (255, 79, 160), 0.26),
            (1.0, 0.86, 0.5, (255, 178, 26), 0.14),
            (0.45, 1.05, 0.5, (124, 92, 255), 0.4),
        ],
    },
}


def gradient(stops):
    image = Image.new('RGB', (1, H))
    for y in range(H):
        t = y / (H - 1)
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            if t0 <= t <= t1:
                k = (t - t0) / (t1 - t0)
                image.putpixel((0, y), tuple(round(a + (b - a) * k) for a, b in zip(c0, c1)))
                break
    return image.resize((W, H))


def main():
    os.makedirs(OUT, exist_ok=True)
    random.seed(7)
    for name, theme in THEMES.items():
        base = gradient(theme['stops']).convert('RGBA')
        glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        draw = ImageDraw.Draw(glow)
        for x, y, r, rgb, alpha in theme['glows']:
            cx, cy, radius = x * W, y * H, r * W
            draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=rgb + (round(alpha * 255),))
        glow = glow.filter(ImageFilter.GaussianBlur(220))
        image = Image.alpha_composite(base, glow).convert('RGB')
        noise = Image.effect_noise((W, H), 6).convert('RGB')
        image = Image.blend(image, Image.merge('RGB', [noise] * 3 if noise.mode == 'L' else noise.split()), 0.025)
        path = os.path.join(OUT, f'{name}.webp')
        image.save(path, quality=86, method=6)
        print(f'{path}: {os.path.getsize(path) // 1024} KB')


if __name__ == '__main__':
    main()

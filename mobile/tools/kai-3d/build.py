"""Packs the rendered frames of the first, 3D-modelled Kai: one animated WebP per mood, plus still images.

Retired: the app now draws Kai from her picture (tools/kai-2d). This writes to ./out so it can't replace that art.
Run after `node render.mjs`. Needs Pillow (pip install pillow).
"""
import glob
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
FRAMES = os.path.join(HERE, 'frames')
OUT = os.path.join(HERE, 'out')
LOOP_SIZE = 480
QUALITY = 78

meta = json.load(open(os.path.join(FRAMES, 'meta.json'), encoding='utf-8'))
os.makedirs(OUT, exist_ok=True)
frame_ms = round(1000 / meta['fps'])

for name in meta['loops']:
    files = sorted(glob.glob(os.path.join(FRAMES, name, '*.png')))
    frames = [Image.open(f).convert('RGBA').resize((LOOP_SIZE, LOOP_SIZE), Image.LANCZOS) for f in files]
    path = os.path.join(OUT, f'kai-{name}.webp')
    # method 4 encodes in seconds; 6 with minimize_size takes minutes per loop for a few percent.
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=frame_ms, loop=0, quality=QUALITY,
                   method=4)
    print(f'{path}: {len(frames)} frames, {os.path.getsize(path) // 1024} KB')

# The first idle frame, for reduced motion and anywhere Kai shouldn't move.
still = Image.open(os.path.join(FRAMES, 'stills', 'bust.png')).convert('RGBA').resize((LOOP_SIZE, LOOP_SIZE), Image.LANCZOS)
still.save(os.path.join(OUT, 'kai-still.webp'), quality=88, method=6)
# Small round avatars: the face, cropped square.
face = Image.open(os.path.join(FRAMES, 'stills', 'headshot.png')).convert('RGBA').resize((256, 256), Image.LANCZOS)
face.save(os.path.join(OUT, 'kai-face.webp'), quality=88, method=6)
for f in ('kai-still.webp', 'kai-face.webp'):
    print(f'{os.path.join(OUT, f)}: {os.path.getsize(os.path.join(OUT, f)) // 1024} KB')

"""Builds Kai's art from one picture (kai-source.png): her cut-out face and two animated loops of her moving in 3D.

    pip install "rembg[cpu]" onnxruntime opencv-python-headless pillow
    python make_kai.py

Writes to assets/kai/:
- kai-face.webp  her face with the background cut away, for small round avatars
- kai-idle.webp  a loop of her looking around and blinking
- kai-talk.webp  a loop of her talking: her mouth moves and she nods along
- kai-still.webp the idle loop's first frame
Both loops show the waving figure (the picture's top-left 1150 px square) with the background cut away. Her head turns
on a depth map (depth.py, pose.py), so it moves like a 3D model rather than a flat picture.

and src/components/kaiArt.ts with the files and where her head sits in the loops.
"""
import math
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

from depth import figure_depth
from pose import Rig
from warp import blink, jaw

HERE = Path(__file__).parent
MOBILE = HERE.parent.parent
ASSETS = MOBILE / "assets" / "kai"
SOURCE = HERE / "kai-source.png"
ALPHA_CACHE = HERE / "kai-figure-alpha.png"

# Feature geometry in source pixels: eye corners and the mouth corners, with lid and lip offsets across them.
LEFT_EYE = {"p0": (502, 388), "p1": (596, 376), "lash": -24, "lower": 14, "skin": 22}
RIGHT_EYE = {"p0": (679, 351), "p1": (765, 311), "lash": -20.5, "lower": 14.3, "skin": 18}
MOUTH = {"p0": (610, 491), "p1": (727, 457), "start": 12, "lip": 27, "end": 75}

# The face for avatars (left, top, side) and its output size.
FACE = ((370, 70, 560), 320)
# The loops: the top-left square of the picture (from the very top, so her hair isn't cut flat; the cut at the bottom
# is hidden by anchoring her to a card's edge or by the round window), drawn at this many pixels.
FIGURE_SIDE = 1150
LOOP_SIZE = 720
FPS = 12
# Head and shoulders, for the round window: (left, top, side) in source pixels. Her hair rises out of it at the top.
PORTAL = (318, 150, 650)
# The speech bubble in the top right of the picture, painted out of the background.
BUBBLE = (915, 95, 1325, 440)


def without_bubble(bgr):
    x0, y0, x1, y1 = BUBBLE
    region = bgr[y0:y1, x0:x1]
    hsv = cv2.cvtColor(region, cv2.COLOR_BGR2HSV)
    # The bubble is near-white and unsaturated; its text is dark navy and the emoji yellow, all inside the white.
    white = ((hsv[..., 2] > 215) & (hsv[..., 1] < 40)).astype(np.uint8)
    white = cv2.morphologyEx(white, cv2.MORPH_CLOSE, np.ones((25, 25), np.uint8))
    filled = white.copy()
    contours, _ = cv2.findContours(white, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    cv2.drawContours(filled, contours, -1, 1, thickness=cv2.FILLED)
    mask = np.zeros(bgr.shape[:2], np.uint8)
    mask[y0:y1, x0:x1] = cv2.dilate(filled, np.ones((9, 9), np.uint8))
    return cv2.inpaint(bgr, mask * 255, 12, cv2.INPAINT_TELEA)


def figure_alpha(rgb):
    """The person's matte, from BiRefNet (cached: it takes a while and downloads a 220 MB model the first time)."""
    if ALPHA_CACHE.exists():
        return np.asarray(Image.open(ALPHA_CACHE).convert("L"), np.float32) / 255
    from rembg import new_session, remove

    cut = remove(Image.fromarray(rgb), session=new_session("birefnet-general-lite"))
    alpha = np.asarray(cut, np.uint8)[..., 3]
    Image.fromarray(alpha).save(ALPHA_CACHE)
    return alpha.astype(np.float32) / 255


def decontaminate(rgb, alpha):
    """Gives see-through edge pixels (hair strands) the colour of the solid hair next to them, so the bright room behind
    her doesn't leave a pale halo on dark cards. Pixels under 60% opaque take that colour outright."""
    solid = (alpha > 0.95).astype(np.float32)
    weighted = cv2.GaussianBlur(rgb.astype(np.float32) * solid[..., None], (0, 0), 6)
    weight = cv2.GaussianBlur(solid, (0, 0), 6)[..., None]
    inner = np.where(weight > 1e-3, weighted / np.maximum(weight, 1e-3), rgb.astype(np.float32))
    # Far from any solid pixel the blur has little to go on; widen it there.
    wide = cv2.GaussianBlur(rgb.astype(np.float32) * solid[..., None], (0, 0), 18)
    wide_weight = cv2.GaussianBlur(solid, (0, 0), 18)[..., None]
    inner = np.where(weight > 0.05, inner, wide / np.maximum(wide_weight, 1e-3))
    mix = np.clip((0.95 - alpha) / 0.35, 0, 1)[..., None]
    return (rgb.astype(np.float32) * (1 - mix) + inner * mix).clip(0, 255).astype(np.uint8)


def to_rgba(premultiplied):
    """Premultiplied BGRA (0 to 1) back to a straight-alpha RGBA picture."""
    a = np.clip(premultiplied[..., 3:4], 0, 1)
    bgr = premultiplied[..., :3] / np.maximum(a, 1e-4)
    rgba = np.dstack([bgr[..., ::-1], a]) * 255
    return Image.fromarray(rgba.clip(0, 255).round().astype(np.uint8), "RGBA")


# --- The loops: poses over time. Angles are in degrees (see pose.Rig.shift); the app adds breathing and head tilts. --

def blink_at(t, start, frames=(0.55, 0.92, 0.5)):
    """How shut the eyes are at time `t` for a blink starting at `start`: a quick close and open over three frames."""
    i = math.floor((t - start) * FPS + 1e-6)
    return frames[i] if 0 <= i < len(frames) else 0.0


def idle_pose(t, length):
    """Looking around: a slow turn from side to side with a small nod, and two blinks."""
    a = 2 * math.pi * t / length
    return {
        "yaw": 5.0 * math.sin(a),
        "pitch": 1.4 * math.sin(2 * a + 0.8),
        "eyes": max(blink_at(t, 1.0), blink_at(t, 3.75)),
        "mouth": 0.0,
    }


def talk_pose(t, length):
    """Talking: the mouth opens and closes four times a second, with a short gap between words, while she nods along
    and turns a little."""
    a = 2 * math.pi * t / length
    beat = 0.5 + 0.5 * math.cos(12 * a)
    swell = 0.7 + 0.3 * math.sin(2 * a + 0.4)
    gap = abs(t - 2.45) < 0.15
    return {
        "yaw": 2.5 * math.sin(a) + 0.8 * math.sin(3 * a + 0.5),
        "pitch": 1.0 * math.sin(2 * a) + 0.8 * math.sin(5 * a + 1.3),
        "eyes": blink_at(t, 1.5),
        "mouth": 14.0 if gap else 14.0 * (1 - beat * swell),
    }


LOOPS = {"idle": (idle_pose, 5.0), "talk": (talk_pose, 3.0)}


def render_loop(pose_at, length, source_bgr, alpha, rig):
    """The frames of one loop: blink and mouth first (on the flat picture), then the head turned in 3D."""
    frames = []
    count = round(length * FPS)
    a = alpha[:FIGURE_SIDE, :FIGURE_SIDE, None]
    for i in range(count):
        pose = pose_at(i / FPS, length)
        picture = source_bgr
        if pose["eyes"] > 0:
            picture, _ = blink(picture, LEFT_EYE, pose["eyes"])
            picture, _ = blink(picture, RIGHT_EYE, pose["eyes"])
        if pose["mouth"] > 0:
            picture, _ = jaw(picture, MOUTH, pose["mouth"])
        square = picture[:FIGURE_SIDE, :FIGURE_SIDE].astype(np.float32) / 255
        premultiplied = np.dstack([square * a, a]).astype(np.float32)
        turned = rig.render(premultiplied, yaw=pose["yaw"], pitch=pose["pitch"])
        small = cv2.resize(turned, (LOOP_SIZE, LOOP_SIZE), interpolation=cv2.INTER_AREA)
        frames.append(to_rgba(small))
    return frames


def save_loop(frames, path):
    # Only her face changes from frame to frame, so most frames are small; a full key frame comes every 10 to 30.
    # Her outline is stored a little lossy, which hair edges hide well and which keeps those key frames small.
    frames[0].save(path, "WEBP", save_all=True, append_images=frames[1:], duration=round(1000 / FPS), loop=0,
                   quality=78, alpha_quality=85, method=4, kmin=10, kmax=30)


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    source = cv2.imread(str(SOURCE))
    clean = without_bubble(source)
    rgb = cv2.cvtColor(source, cv2.COLOR_BGR2RGB)
    alpha = figure_alpha(rgb)
    near = figure_depth(rgb, alpha)
    figure = cv2.cvtColor(decontaminate(cv2.cvtColor(clean, cv2.COLOR_BGR2RGB), alpha), cv2.COLOR_RGB2BGR)

    (left, top, side), size = FACE
    face = np.dstack([figure[top:top + side, left:left + side].astype(np.float32) / 255 * alpha[top:top + side,
                      left:left + side, None], alpha[top:top + side, left:left + side]])
    to_rgba(cv2.resize(face, (size, size), interpolation=cv2.INTER_AREA)).save(
        ASSETS / "kai-face.webp", "WEBP", quality=90, method=6)

    square = (slice(0, FIGURE_SIDE), slice(0, FIGURE_SIDE))
    rig = Rig(near[square], alpha[square], figure[square])
    for name, (pose_at, length) in LOOPS.items():
        frames = render_loop(pose_at, length, figure, alpha, rig)
        save_loop(frames, ASSETS / f"kai-{name}.webp")
        print(name, len(frames), "frames,", (ASSETS / f"kai-{name}.webp").stat().st_size // 1024, "KB")
        if name == "idle":
            # The first frame on its own: shown while a loop loads, and instead of the loops when motion is reduced.
            frames[0].save(ASSETS / "kai-still.webp", "WEBP", quality=82, alpha_quality=85, method=6)

    lines = [
        "// Generated by tools/kai-2d/make_kai.py; don't edit by hand.",
        "",
        "/** Kai's face with the background cut away, for small round avatars. */",
        "export const KAI_FACE = require('../../assets/kai/kai-face.webp');",
        "",
        "/** Animated loops of the waving figure, the picture's top-left %d px square, with the background cut away. */"
        % FIGURE_SIDE,
        "export const KAI_LOOPS = {",
        *("  %s: require('../../assets/kai/kai-%s.webp')," % (name, name) for name in LOOPS),
        "};",
        "/** The idle loop's first frame, for while a loop loads and for reduced motion. */",
        "export const KAI_STILL = require('../../assets/kai/kai-still.webp');",
        "",
        "/** The square the loops show, and the head-and-shoulders square inside it for the round window, in the",
        " * same pixels: [left, top, side]. */",
        "export const KAI_FIGURE_SIDE = %d;" % FIGURE_SIDE,
        "export const KAI_PORTAL = [%d, %d, %d] as const;" % PORTAL,
        "",
    ]
    (MOBILE / "src" / "components" / "kaiArt.ts").write_text("\n".join(lines), encoding="utf-8")
    print("wrote", sorted(p.name for p in ASSETS.glob("kai-*.webp")))


if __name__ == "__main__":
    main()

"""Blink and talk frames for the 2D Kai, made by warping the face along its own tilt.

Each feature (an eye, the mouth) gets a local frame: `u` runs corner to corner, `n` points down across it. A frame is a
displacement field in that frame, feathered out at the corners, so only the lids or the lower lip move.
"""
import numpy as np
import cv2


def frame(p0, p1):
    p0 = np.asarray(p0, np.float32)
    p1 = np.asarray(p1, np.float32)
    c = (p0 + p1) / 2
    u = (p1 - p0) / np.linalg.norm(p1 - p0)
    n = np.array([-u[1], u[0]], np.float32)
    if n[1] < 0:
        n = -n
    return c, u, n, float(np.linalg.norm(p1 - p0) / 2)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def local_coords(shape, c, u, n):
    h, w = shape[:2]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = xs - c[0]
    dy = ys - c[1]
    return xs, ys, dx * u[0] + dy * u[1], dx * n[0] + dy * n[1]


def blink(img, eye, t):
    """Closes one eye by `t` (0 open, 1 shut): the lid skin above the lashes is stretched down to the lower lid, so the
    lash line ends up resting on it.

    eye: dict with corners p0/p1 and, in the local frame at the middle of the eye, `lash` (upper lash line, negative =
    above the corner line), `lower` (lower lid) and `skin` (how much lid skin above the lashes to use).
    """
    c, u, n, half = frame(eye["p0"], eye["p1"])
    xs, ys, a, b = local_coords(img.shape, c, u, n)
    span = np.clip(1 - (a / (half * 1.08)) ** 2, 0, 1)  # 1 mid-eye, 0 past the corners
    lash = eye["lash"] * span ** 0.6
    lower = eye["lower"] * span ** 0.8 + 1.5
    top = lash - eye["skin"]
    # Where the lash line should be now: moving from `lash` down to `lower`.
    new_lash = lash + t * (lower - lash)
    src_b = b.copy()
    inside = (b >= top) & (b <= lower)
    # Rows between `top` and `new_lash` show the lid skin and lashes, stretched; rows below show the eye, squeezed.
    upper_part = inside & (b <= new_lash)
    src_b[upper_part] = top[upper_part] + (b[upper_part] - top[upper_part]) * (
        (lash[upper_part] - top[upper_part]) / np.maximum(new_lash[upper_part] - top[upper_part], 1e-3))
    lower_part = inside & (b > new_lash)
    src_b[lower_part] = lash[lower_part] + (b[lower_part] - new_lash[lower_part]) * (
        (lower[lower_part] - lash[lower_part]) / np.maximum(lower[lower_part] - new_lash[lower_part], 1e-3))
    # Fade the effect out at the corners and well away from the eye.
    weight = smoothstep(0.0, 0.25, span)
    shift = (src_b - b) * weight
    map_x = xs + shift * n[0]
    map_y = ys + shift * n[1]
    out = cv2.remap(img, map_x, map_y, cv2.INTER_CUBIC, borderMode=cv2.BORDER_REFLECT)
    mask = (np.abs(shift) > 0.05).astype(np.float32)
    return out, mask


def jaw(img, mouth, d):
    """Raises the lower lip by `d` pixels (at the middle of the mouth), squeezing the open mouth shut a little and
    letting the chin follow."""
    c, u, n, half = frame(mouth["p0"], mouth["p1"])
    xs, ys, a, b = local_coords(img.shape, c, u, n)
    across = np.clip(1 - (a / (half * 1.15)) ** 2, 0, 1) ** 0.9
    start, lip, end = mouth["start"], mouth["lip"], mouth["end"]
    along = np.where(b < start, 0.0,
                     np.where(b < lip, (b - start) / (lip - start), np.clip(1 - (b - lip) / (end - lip), 0, 1)))
    shift = d * across * along  # sample from further down: content moves up
    map_x = xs + shift * n[0]
    map_y = ys + shift * n[1]
    out = cv2.remap(img, map_x, map_y, cv2.INTER_CUBIC, borderMode=cv2.BORDER_REFLECT)
    mask = (shift > 0.05).astype(np.float32)
    return out, mask

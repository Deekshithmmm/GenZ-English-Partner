"""Turns and nods Kai's face in her picture as if it were a 3D model, using the depth map (depth.py).

Each pixel becomes a point in space: x and y from the picture, z from how near it is. Turning the head rotates those
points around the middle of her head, so the nose moves further than the cheeks and the cheeks further than the face's
edge. That difference (parallax) is what makes her read as 3D.

Only the face moves. For small turns the outline of a head hardly moves (it sits level with the rotation axis), and
keeping the hair still makes each animation frame about a third of the size. Tilting the head is a flat rotation, so
the app does that by rotating the whole picture.
"""
import cv2
import numpy as np

from warp import smoothstep

# All in source-picture pixels. Depth is "nearness" from depth.py (0 far, 1 nearest), scaled into pixels so her nose
# sits about 165 px in front of her ears, which is the right depth for a head this size.
DEPTH_PX = 560
# The rotation axes run through the middle of her head, level with her ears (the ears sit at about 0.32 nearness).
AXIS = {"x": 645, "y": 430, "near": 0.33}
# Her head: an ellipse that fades out down her neck. The face mask is cut to it.
HEAD = {"cx": 645, "cy": 335, "rx": 300, "ry": 345, "solid": 0.82, "fade": 1.22}
# How far "behind the axis" depth can go, so the neck below her chin barely moves against the turn.
BEHIND_LIMIT_PX = -45


def face_mask(bgr, alpha, head):
    """Her face: the biggest patch of skin inside the head, with the eyes, brows and mouth filled in, softened at the
    edge so the hair next to it is drawn along a little instead of tearing."""
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV).astype(np.float32)
    h, s, v = hsv[..., 0], hsv[..., 1] / 255, hsv[..., 2] / 255
    skin = (v > 0.42) & (s > 0.08) & (s < 0.75) & ((h < 22) | (h > 165))
    skin = (skin & (head > 0.5) & (alpha > 0.9)).astype(np.uint8)
    skin = cv2.morphologyEx(skin, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    _, labels, stats, _ = cv2.connectedComponentsWithStats(skin)
    face = (labels == 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))).astype(np.uint8)
    face = cv2.morphologyEx(face, cv2.MORPH_CLOSE, np.ones((41, 41), np.uint8))
    contours, _ = cv2.findContours(face, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    filled = np.zeros_like(face)
    cv2.drawContours(filled, contours, -1, 1, thickness=cv2.FILLED)
    soft = cv2.GaussianBlur(cv2.dilate(filled.astype(np.float32), np.ones((15, 15), np.uint8)), (0, 0), 9)
    return np.clip(soft, 0, 1) * head


def _spread(values, alpha, sigma):
    """Smooths `values` inside the figure and carries them outwards into the background, so the silhouette moves with
    the hair next to it instead of with the far wall the depth model saw behind her."""
    weight = cv2.GaussianBlur(alpha, (0, 0), sigma)
    blurred = cv2.GaussianBlur(values * alpha, (0, 0), sigma)
    smooth = blurred / np.maximum(weight, 1e-4)
    wide_weight = cv2.GaussianBlur(alpha, (0, 0), sigma * 6)
    wide = cv2.GaussianBlur(values * alpha, (0, 0), sigma * 6) / np.maximum(wide_weight, 1e-4)
    inside = np.clip(weight / 0.5, 0, 1)
    return (smooth * inside + wide * (1 - inside)).astype(np.float32)


class Rig:
    """Everything that stays the same between frames: the grid, depth in pixels and how much each pixel is 'face'."""

    def __init__(self, near, alpha, bgr):
        h, w = near.shape
        self.ys, self.xs = np.mgrid[0:h, 0:w].astype(np.float32)
        near = _spread(near.astype(np.float32), alpha.astype(np.float32), 5)
        self.z = np.maximum((near - AXIS["near"]) * DEPTH_PX, BEHIND_LIMIT_PX).astype(np.float32)
        d = np.sqrt(((self.xs - HEAD["cx"]) / HEAD["rx"]) ** 2 + ((self.ys - HEAD["cy"]) / HEAD["ry"]) ** 2)
        head = 1 - smoothstep(HEAD["solid"], HEAD["fade"], d)
        self.face = face_mask(bgr, alpha, head).astype(np.float32)

    def shift(self, yaw=0.0, pitch=0.0):
        """How far each point moves for a head pose, in degrees: yaw turns to the right of the picture, pitch nods
        down."""
        t, p = np.radians([yaw, pitch])
        sx = self.z * np.sin(t) + (self.xs - AXIS["x"]) * (np.cos(t) - 1)
        sy = self.z * np.sin(p) + (self.ys - AXIS["y"]) * (np.cos(p) - 1)
        return (sx * self.face).astype(np.float32), (sy * self.face).astype(np.float32)

    def render(self, bgra, **pose):
        """`bgra` (premultiplied, float) seen with the head in `pose`. Each output pixel looks up where it came from;
        the shift is smooth, so a few rounds of "where would this pixel have come from" converge."""
        sx, sy = self.shift(**pose)
        map_x, map_y = self.xs.copy(), self.ys.copy()
        for _ in range(4):
            px = cv2.remap(sx, map_x, map_y, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
            py = cv2.remap(sy, map_x, map_y, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
            map_x, map_y = self.xs - px, self.ys - py
        return cv2.remap(bgra, map_x, map_y, cv2.INTER_CUBIC, borderMode=cv2.BORDER_CONSTANT, borderValue=0)

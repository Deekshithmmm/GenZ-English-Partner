"""Kai's depth map, so her picture can turn like a 3D model: how near each pixel is to the camera.

Made with Depth Anything V2 Small (Apache-2.0), run through onnxruntime, at two input sizes: the smaller one gets the
overall shape right and the larger one adds the nose, lips and cheeks. The result is cached in kai-depth.png, so the
model (about 100 MB) is only downloaded when the picture changes.
"""
from pathlib import Path
from urllib.request import urlretrieve

import cv2
import numpy as np
from PIL import Image

HERE = Path(__file__).parent
CACHE = HERE / "kai-depth.png"
MODEL_URL = "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model.onnx"
MODEL = Path.home() / ".cache" / "kai-art" / "depth-anything-v2-small.onnx"
MEAN = np.array([0.485, 0.456, 0.406], np.float32)
STD = np.array([0.229, 0.224, 0.225], np.float32)


def _predict(session, rgb, short_side):
    h, w = rgb.shape[:2]
    scale = short_side / min(h, w)
    size = (round(w * scale / 14) * 14, round(h * scale / 14) * 14)
    x = cv2.resize(rgb.astype(np.float32) / 255, size, interpolation=cv2.INTER_CUBIC)
    x = ((x - MEAN) / STD).transpose(2, 0, 1)[None].astype(np.float32)
    out = session.run(None, {session.get_inputs()[0].name: x})[0][0]
    return cv2.resize(out, (w, h), interpolation=cv2.INTER_CUBIC)


def _normalised(disparity, alpha):
    inside = disparity[alpha > 0.5]
    lo, hi = np.percentile(inside, 1), np.percentile(inside, 99.5)
    return np.clip((disparity - lo) / (hi - lo), 0, 1)


def figure_depth(rgb, alpha):
    """Nearness from 0 (the far side of the figure) to 1 (her waving hand), for every pixel of the picture."""
    if CACHE.exists():
        return np.asarray(Image.open(CACHE), np.float32) / 65535
    import onnxruntime as ort

    if not MODEL.exists():
        MODEL.parent.mkdir(parents=True, exist_ok=True)
        print("downloading", MODEL_URL)
        urlretrieve(MODEL_URL, MODEL)
    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    coarse = _normalised(_predict(session, rgb, 518), alpha)
    fine = _normalised(_predict(session, rgb, 1036), alpha)
    near = 0.5 * (coarse + fine)
    Image.fromarray((near * 65535).astype(np.uint16)).save(CACHE)
    return near.astype(np.float32)

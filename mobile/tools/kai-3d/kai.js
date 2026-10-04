// Kai, the app's tutor: an original stylised 3D character (a young woman in a mango hoodie with a headset), built from
// primitives so it needs no third-party model. render.mjs drives this page to render the app's animation loops.
import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

const params = new URLSearchParams(location.search);
const SIZE = Number(params.get('size') ?? 768);
if (params.has('preview')) {
  document.body.classList.add('preview');
}

const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(1);
renderer.setSize(SIZE, SIZE);
renderer.setClearColor(0x000000, 0);
// Neutral tone mapping keeps the brand mango and neel close to their real colours.
renderer.toneMapping = THREE.NeutralToneMapping;
renderer.toneMappingExposure = 1.0;
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
scene.environmentIntensity = 0.45;

const V2 = THREE.Vector2;
const V3 = THREE.Vector3;
const lerp = THREE.MathUtils.lerp;
const smoothstep = (edge0, edge1, x) => {
  const t = Math.min(1, Math.max(0, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
};

// ---------- Palette and materials ----------

const physical = (o) => new THREE.MeshPhysicalMaterial(o);
const color = (hex) => new THREE.Color(hex);
const mat = {
  skin: physical({
    color: 0xc98a62,
    roughness: 0.5,
    sheen: 0.45,
    sheenRoughness: 0.55,
    sheenColor: color(0xffc7a8),
    clearcoat: 0.12,
    clearcoatRoughness: 0.55,
  }),
  hair: physical({
    color: 0x21160f,
    roughness: 0.52,
    sheen: 1,
    sheenRoughness: 0.35,
    sheenColor: color(0x9a6a4e),
    clearcoat: 0.18,
    clearcoatRoughness: 0.45,
  }),
  sclera: physical({ color: 0xfdf9f4, roughness: 0.22, clearcoat: 0.7 }),
  iris: physical({ color: 0x5a3220, roughness: 0.28, clearcoat: 1, clearcoatRoughness: 0.08 }),
  irisRim: physical({ color: 0x2a140b, roughness: 0.3, clearcoat: 1 }),
  pupil: physical({ color: 0x0b0505, roughness: 0.2, clearcoat: 1 }),
  shine: new THREE.MeshBasicMaterial({ color: 0xffffff }),
  lash: new THREE.MeshStandardMaterial({ color: 0x110807, roughness: 0.55 }),
  brow: new THREE.MeshStandardMaterial({ color: 0x23150f, roughness: 0.75 }),
  lip: physical({ color: 0xb04a5b, roughness: 0.38, clearcoat: 0.5, clearcoatRoughness: 0.3 }),
  mouthIn: new THREE.MeshStandardMaterial({ color: 0x4f1622, roughness: 0.85 }),
  teeth: physical({ color: 0xfffaf1, roughness: 0.25, clearcoat: 0.6 }),
  tongue: new THREE.MeshStandardMaterial({ color: 0xdb7480, roughness: 0.6 }),
  blush: new THREE.MeshStandardMaterial({
    color: 0xff6f86,
    roughness: 0.9,
    transparent: true,
    opacity: 0.32,
    depthWrite: false,
  }),
  hoodie: physical({
    color: 0xffb11f,
    roughness: 0.78,
    sheen: 0.7,
    sheenRoughness: 0.7,
    sheenColor: color(0xffe08a),
  }),
  hoodieDeep: physical({ color: 0xf59a00, roughness: 0.82, sheen: 0.6, sheenRoughness: 0.75, sheenColor: color(0xffd070) }),
  tee: physical({ color: 0xffffff, roughness: 0.8, sheen: 0.6, sheenColor: color(0xffffff) }),
  string: physical({ color: 0xffffff, roughness: 0.6 }),
  neel: physical({ color: 0x2f2690, roughness: 0.32, metalness: 0.15, clearcoat: 0.9, clearcoatRoughness: 0.18 }),
  neelSoft: physical({ color: 0x3b3299, roughness: 0.7, sheen: 0.6, sheenColor: color(0x8c80ff) }),
  mango: physical({ color: 0xffb21a, roughness: 0.35, clearcoat: 0.8, clearcoatRoughness: 0.2 }),
  gold: new THREE.MeshStandardMaterial({ color: 0xe8b04a, roughness: 0.22, metalness: 1 }),
  micTip: new THREE.MeshStandardMaterial({ color: 0x34334a, roughness: 0.95 }),
};

// ---------- Shape helpers ----------

/** A tube whose radius follows taper(t) along the curve, for hair strands and brows. */
function taperedTube(curve, radius, taper, tubular = 48, radial = 12) {
  const geometry = new THREE.TubeGeometry(curve, tubular, radius, radial, false);
  const position = geometry.attributes.position;
  const ring = radial + 1;
  for (let i = 0; i <= tubular; i++) {
    const center = curve.getPointAt(i / tubular);
    const factor = taper(i / tubular);
    for (let j = 0; j < ring; j++) {
      const index = i * ring + j;
      const v = new V3().fromBufferAttribute(position, index).sub(center).multiplyScalar(factor).add(center);
      position.setXYZ(index, v.x, v.y, v.z);
    }
  }
  geometry.computeVertexNormals();
  return geometry;
}

function mesh(geometry, material, { shadow = true } = {}) {
  const m = new THREE.Mesh(geometry, material);
  m.castShadow = shadow;
  m.receiveShadow = true;
  return m;
}

// ---------- Head shape ----------

const HEAD_Z = 0.9;
const headProfile = new THREE.SplineCurve([
  new V2(0.0, -1.0),
  new V2(0.33, -0.955),
  new V2(0.63, -0.79),
  new V2(0.85, -0.5),
  new V2(0.965, -0.14),
  new V2(1.0, 0.2),
  new V2(0.955, 0.5),
  new V2(0.81, 0.76),
  new V2(0.53, 0.945),
  new V2(0.0, 1.02),
])
  .getPoints(96)
  .map((p) => new V2(Math.max(0, p.x), p.y));
headProfile[0].x = 0;
headProfile[headProfile.length - 1].x = 0;

function radiusAt(y) {
  for (let i = 0; i < headProfile.length - 1; i++) {
    const a = headProfile[i];
    const b = headProfile[i + 1];
    if (y >= a.y && y <= b.y) {
      return lerp(a.x, b.x, (y - a.y) / (b.y - a.y || 1));
    }
  }
  return 0;
}

/** The point on the front of the face at (x, y), in head space. */
function surface(x, y) {
  const r = radiusAt(y);
  return new V3(x, y, Math.sqrt(Math.max(r * r - x * x, 0)) * HEAD_Z);
}

function normalAt(x, y) {
  const e = 0.01;
  const dx = surface(x + e, y).sub(surface(x - e, y));
  const dy = surface(x, y + e).sub(surface(x, y - e));
  return new V3().crossVectors(dx, dy).normalize();
}

/** Puts obj on the face at (x, y), lifted by offset along the surface normal, facing out (blended to forward). */
function placeOnFace(obj, x, y, offset = 0, facing = 1) {
  const n = normalAt(x, y);
  obj.position.copy(surface(x, y)).addScaledVector(n, offset);
  const outward = new THREE.Quaternion().setFromUnitVectors(new V3(0, 0, 1), n);
  obj.quaternion.slerpQuaternions(new THREE.Quaternion(), outward, facing);
  return obj;
}

// ---------- Build the character ----------

const root = new THREE.Group();
scene.add(root);

const body = new THREE.Group();
root.add(body);

// Torso in a mango hoodie.
const torsoProfile = [
  new V2(0.33, -0.3),
  new V2(0.64, -0.37),
  new V2(1.02, -0.5),
  new V2(1.33, -0.73),
  new V2(1.5, -1.05),
  new V2(1.56, -1.5),
  new V2(1.57, -2.7),
].reverse();
const torsoGeometry = new THREE.LatheGeometry(
  new THREE.SplineCurve(torsoProfile).getPoints(60),
  96,
);
torsoGeometry.scale(1, 1, 0.66);
const torso = mesh(torsoGeometry, mat.hoodie);
body.add(torso);

// Hood bunched behind the neck, the rolled hood rim, and the T-shirt neckline.
const hoodBack = mesh(new THREE.SphereGeometry(1, 48, 32), mat.hoodieDeep);
hoodBack.scale.set(0.95, 0.42, 0.42);
hoodBack.position.set(0, -0.33, -0.4);
body.add(hoodBack);
const rimGeometry = new THREE.TorusGeometry(0.5, 0.155, 24, 72);
rimGeometry.rotateX(Math.PI / 2);
rimGeometry.scale(1.08, 0.9, 0.8);
const hoodRim = mesh(rimGeometry, mat.hoodieDeep);
hoodRim.position.set(0, -0.36, 0.02);
body.add(hoodRim);
const teeGeometry = new THREE.TorusGeometry(0.335, 0.055, 16, 64);
teeGeometry.rotateX(Math.PI / 2);
teeGeometry.scale(1, 1, 0.8);
const tee = mesh(teeGeometry, mat.tee);
tee.position.set(0, -0.29, 0.02);
body.add(tee);

// Drawstrings with neel tips.
for (const side of [-1, 1]) {
  const curve = new THREE.CatmullRomCurve3([
    new V3(side * 0.2, -0.42, 0.44),
    new V3(side * 0.23, -0.72, 0.62),
    new V3(side * 0.25, -1.04, 0.74),
  ]);
  body.add(mesh(new THREE.TubeGeometry(curve, 24, 0.028, 10), mat.string));
  const tip = mesh(new THREE.CylinderGeometry(0.036, 0.036, 0.12, 16), mat.neel);
  tip.position.copy(curve.getPointAt(1)).add(new V3(0, -0.05, 0.01));
  body.add(tip);
}

// Neck.
const neck = mesh(new THREE.CylinderGeometry(0.3, 0.34, 0.62, 32), mat.skin);
neck.position.set(0, -0.1, -0.06);
body.add(neck);

// The head pivots at the top of the neck.
const headPivot = new THREE.Group();
headPivot.position.set(0, 0.1, 0);
body.add(headPivot);
const head = new THREE.Group();
head.position.set(0, 0.86, 0.04);
headPivot.add(head);

const headGeometry = new THREE.LatheGeometry(headProfile, 128);
headGeometry.scale(1, 1, HEAD_Z);
head.add(mesh(headGeometry, mat.skin));

// Ears sit under the headset cups.
for (const side of [-1, 1]) {
  const ear = mesh(new THREE.SphereGeometry(1, 24, 16), mat.skin);
  ear.scale.set(0.12, 0.19, 0.1);
  ear.position.set(side * 0.97, -0.08, -0.04);
  head.add(ear);
}

// Eyes: sclera, an iris and pupil that turn for gaze, fixed catchlights, and lids that blink.
const EYE_R = 0.2;
const eyes = [];
for (const side of [-1, 1]) {
  const socket = new THREE.Group();
  placeOnFace(socket, side * 0.37, -0.06, -0.085, 0.55);
  head.add(socket);

  socket.add(mesh(new THREE.SphereGeometry(EYE_R, 48, 32), mat.sclera, { shadow: false }));
  const ball = new THREE.Group();
  socket.add(ball);
  const irisRim = mesh(new THREE.SphereGeometry(0.142, 40, 24), mat.irisRim, { shadow: false });
  irisRim.scale.set(1, 1, 0.36);
  irisRim.position.z = EYE_R - 0.04;
  const iris = mesh(new THREE.SphereGeometry(0.128, 40, 24), mat.iris, { shadow: false });
  iris.scale.set(1, 1, 0.38);
  iris.position.z = EYE_R - 0.03;
  const pupil = mesh(new THREE.SphereGeometry(0.066, 32, 16), mat.pupil, { shadow: false });
  pupil.scale.set(1, 1, 0.3);
  pupil.position.z = EYE_R + 0.004;
  ball.add(irisRim, iris, pupil);

  const shineBig = new THREE.Mesh(new THREE.SphereGeometry(0.036, 16, 12), mat.shine);
  shineBig.position.set(0.05, 0.055, EYE_R + 0.03);
  const shineSmall = new THREE.Mesh(new THREE.SphereGeometry(0.017, 12, 8), mat.shine);
  shineSmall.position.set(-0.045, -0.045, EYE_R + 0.028);
  socket.add(shineBig, shineSmall);

  const upper = new THREE.Group();
  socket.add(upper);
  upper.add(mesh(new THREE.SphereGeometry(EYE_R * 1.07, 48, 24, 0, Math.PI * 2, 0, Math.PI / 2), mat.skin));
  const lashLine = mesh(new THREE.TorusGeometry(EYE_R * 1.075, 0.019, 10, 48, Math.PI), mat.lash);
  lashLine.rotation.x = Math.PI / 2;
  upper.add(lashLine);
  // A small flick at the outer corner.
  const flick = mesh(
    taperedTube(
      new THREE.QuadraticBezierCurve3(new V3(0, 0, 0), new V3(0.05, 0.015, 0), new V3(0.085, 0.045, 0)),
      0.018,
      (t) => 1 - t * 0.85,
      16,
      8,
    ),
    mat.lash,
  );
  flick.position.set(side * EYE_R * 1.02, 0, 0.03);
  flick.scale.x = side;
  upper.add(flick);

  const lower = new THREE.Group();
  socket.add(lower);
  lower.add(
    mesh(new THREE.SphereGeometry(EYE_R * 1.05, 48, 24, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2), mat.skin),
  );

  eyes.push({ side, socket, ball, upper, lower });
}

// Brows.
const brows = [];
for (const side of [-1, 1]) {
  const holder = new THREE.Group();
  placeOnFace(holder, side * 0.38, 0.215, 0.012, 0.8);
  head.add(holder);
  const curve = new THREE.QuadraticBezierCurve3(new V3(-0.15, -0.015, 0), new V3(0.0, 0.05, 0), new V3(0.15, 0.0, 0));
  const brow = mesh(taperedTube(curve, 0.03, (t) => 0.75 + 0.35 * Math.sin(Math.PI * Math.min(1, t * 1.25))), mat.brow);
  brow.scale.x = side;
  holder.add(brow);
  brows.push({ side, holder, brow, base: holder.position.clone() });
}

// Nose.
const nose = mesh(new THREE.SphereGeometry(0.075, 24, 16), mat.skin);
nose.scale.set(1, 0.78, 0.85);
placeOnFace(nose, 0, -0.3, 0.012);
head.add(nose);

// Blush.
for (const side of [-1, 1]) {
  const blush = new THREE.Mesh(new THREE.SphereGeometry(0.17, 24, 16), mat.blush);
  blush.scale.set(1, 0.6, 0.18);
  placeOnFace(blush, side * 0.57, -0.36, 0.0, 1);
  head.add(blush);
}

// Mouth: rebuilt for each pose.
const mouth = new THREE.Group();
placeOnFace(mouth, 0, -0.56, 0.006, 0.9);
head.add(mouth);

function bend(geometry, amount) {
  const p = geometry.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i);
    p.setZ(i, p.getZ(i) - amount * x * x);
  }
  geometry.computeVertexNormals();
  return geometry;
}

function buildMouth({ open, smile, width }) {
  for (const child of [...mouth.children]) {
    mouth.remove(child);
    child.geometry.dispose();
  }
  const w = 0.165 * width;
  const lift = 0.055 * smile;
  if (open < 0.04) {
    const curve = new THREE.QuadraticBezierCurve3(new V3(-w, lift, 0), new V3(0, -lift * 0.95, 0), new V3(w, lift, 0));
    mouth.add(mesh(bend(taperedTube(curve, 0.024, (t) => 0.6 + 0.4 * Math.sin(Math.PI * t), 40, 10), 1.3), mat.lip));
    // Little dimples at the corners of the smile.
    for (const side of [-1, 1]) {
      const corner = mesh(new THREE.SphereGeometry(0.016, 12, 8), mat.lip);
      corner.position.set(side * w * 1.02, lift * 1.05, -1.3 * w * w);
      mouth.add(corner);
    }
    return;
  }
  const depth = 0.07 + open * 0.3;
  const outline = new THREE.Shape();
  outline.moveTo(-w, lift);
  outline.quadraticCurveTo(0, lift * 0.2 - 0.015, w, lift);
  outline.quadraticCurveTo(0, -depth - lift * 0.4, -w, lift);
  const inside = mesh(
    bend(
      new THREE.ExtrudeGeometry(outline, {
        depth: 0.012,
        bevelEnabled: true,
        bevelThickness: 0.012,
        bevelSize: 0.012,
        bevelSegments: 3,
        curveSegments: 28,
      }),
      1.3,
    ),
    mat.mouthIn,
    { shadow: false },
  );
  mouth.add(inside);

  // Upper teeth just under the top lip.
  const teeth = new THREE.Shape();
  const toothDrop = Math.min(0.055, 0.02 + open * 0.07);
  teeth.moveTo(-w * 0.82, lift * 0.85 - 0.004);
  teeth.quadraticCurveTo(0, lift * 0.2 - 0.02, w * 0.82, lift * 0.85 - 0.004);
  teeth.quadraticCurveTo(0, lift * 0.2 - 0.02 - toothDrop * 1.6, -w * 0.82, lift * 0.85 - 0.004);
  const teethMesh = mesh(bend(new THREE.ShapeGeometry(teeth, 24), 1.3), mat.teeth, { shadow: false });
  teethMesh.position.z = 0.03;
  mouth.add(teethMesh);

  // Tongue resting at the bottom.
  const tongue = mesh(new THREE.SphereGeometry(1, 24, 12), mat.tongue, { shadow: false });
  tongue.scale.set(w * 0.62, Math.max(0.02, depth * 0.24), 0.02);
  tongue.position.set(0, -depth * 0.62 + lift * 0.2, 0.026 - 1.3 * 0.0);
  mouth.add(tongue);

  // Soft lips around the opening.
  const lipPoints = outline.getSpacedPoints(64).map((p) => new V3(p.x, p.y, 0.03 - 1.3 * p.x * p.x));
  const lips = mesh(
    new THREE.TubeGeometry(new THREE.CatmullRomCurve3(lipPoints, true), 96, 0.019, 10, true),
    mat.lip,
    { shadow: false },
  );
  mouth.add(lips);
}

// Hair: a cap that follows the head with a hairline, a side-swept fringe, framing strands and a high bun.
const HAIR_FRONT = 0.5;
function hairlineAt(theta) {
  const a = Math.abs(theta);
  if (a <= Math.PI / 2) {
    return lerp(HAIR_FRONT, 0.1, Math.pow(a / (Math.PI / 2), 1.6));
  }
  return lerp(0.1, -0.66, Math.pow((a - Math.PI / 2) / (Math.PI / 2), 0.7));
}

function shell(scale, keep) {
  const geometry = new THREE.LatheGeometry(headProfile, 160);
  geometry.scale(scale, scale * 0.995, HEAD_Z * scale);
  const p = geometry.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const v = new V3().fromBufferAttribute(p, i);
    const k = keep(v);
    // Vertices outside the hair sink inside the head, so the edge is a smooth line rather than a cut.
    v.multiplyScalar(lerp(0.84, 1, k));
    p.setXYZ(i, v.x, v.y, v.z);
  }
  geometry.computeVertexNormals();
  return geometry;
}

const hair = new THREE.Group();
head.add(hair);
hair.add(
  mesh(
    shell(1.075, (v) => {
      const theta = Math.atan2(v.x, v.z);
      return smoothstep(-0.08, 0.08, v.y - hairlineAt(theta) * 1.07);
    }),
    mat.hair,
  ),
);

// Side-swept fringe, parted on her right (the viewer's left) and sweeping down to her left temple.
hair.add(
  mesh(
    shell(1.105, (v) => {
      const theta = Math.atan2(v.x, v.z);
      const sweep = smoothstep(-0.55, 0.9, v.x);
      const edge = 0.56 - 0.36 * sweep - 0.05 * Math.sin(Math.PI * sweep);
      const part = smoothstep(-0.62, -0.42, v.x) + smoothstep(0.7, 0.95, v.y);
      return smoothstep(-0.07, 0.07, v.y - edge) * smoothstep(1.4, 1.1, Math.abs(theta)) * Math.min(1, part);
    }),
    mat.hair,
  ),
);

for (const side of [-1, 1]) {
  const top = surface(side * 0.74, 0.3).add(new V3(0, 0, 0.06));
  const mid = surface(side * 0.88, -0.15).add(new V3(side * 0.04, 0, 0.05));
  const end = surface(side * 0.8, -0.62).add(new V3(side * 0.06, 0, 0.02));
  const strand = mesh(
    taperedTube(new THREE.CatmullRomCurve3([top, mid, end]), 0.085, (t) => (1 - t) * 0.8 + 0.2, 40, 12),
    mat.hair,
  );
  hair.add(strand);
}

const bun = new THREE.Group();
bun.position.set(0, 1.12, -0.22);
hair.add(bun);
const bunBall = mesh(new THREE.SphereGeometry(0.4, 48, 32), mat.hair);
bunBall.scale.set(1.05, 0.95, 0.95);
bun.add(bunBall);
// Twists in the bun so it reads as hair rather than a ball.
for (let i = 0; i < 3; i++) {
  const turn = (i / 3) * Math.PI * 2;
  const points = [];
  for (let k = 0; k <= 16; k++) {
    const a = turn + (k / 16) * Math.PI * 1.3;
    const h = lerp(-0.24, 0.3, k / 16);
    const r = Math.sqrt(Math.max(0.4 * 0.4 - h * h, 0)) * 1.04 + 0.01;
    points.push(new V3(Math.cos(a) * r * 1.05, h * 0.92, Math.sin(a) * r * 0.95));
  }
  bun.add(
    mesh(taperedTube(new THREE.CatmullRomCurve3(points), 0.07, (t) => Math.sin(Math.PI * t) * 0.9 + 0.1, 48, 10), mat.hair),
  );
}
const scrunchieGeometry = new THREE.TorusGeometry(0.3, 0.09, 16, 48);
const scrunchie = mesh(scrunchieGeometry, mat.mango);
scrunchie.position.set(0, -0.27, 0.04);
scrunchie.rotation.x = Math.PI / 2 - 0.2;
bun.add(scrunchie);

// Headset: neel band, cups with mango rings, and a boom mic by her mouth.
const headset = new THREE.Group();
head.add(headset);
const bandCurve = new THREE.CatmullRomCurve3([
  new V3(-1.05, -0.02, 0.02),
  new V3(-1.0, 0.62, 0.04),
  new V3(-0.6, 1.09, 0.12),
  new V3(0, 1.22, 0.16),
  new V3(0.6, 1.09, 0.12),
  new V3(1.0, 0.62, 0.04),
  new V3(1.05, -0.02, 0.02),
]);
headset.add(mesh(new THREE.TubeGeometry(bandCurve, 96, 0.055, 14), mat.neel));
for (const side of [-1, 1]) {
  const cup = mesh(new THREE.CylinderGeometry(0.25, 0.25, 0.17, 48), mat.neel);
  cup.rotation.z = Math.PI / 2;
  cup.position.set(side * 1.08, -0.06, 0.02);
  headset.add(cup);
  const cushion = mesh(new THREE.TorusGeometry(0.2, 0.06, 16, 48), mat.neelSoft);
  cushion.rotation.y = Math.PI / 2;
  cushion.position.set(side * 1.0, -0.06, 0.02);
  headset.add(cushion);
  const ring = mesh(new THREE.TorusGeometry(0.17, 0.035, 16, 48), mat.mango);
  ring.rotation.y = Math.PI / 2;
  ring.position.set(side * 1.17, -0.06, 0.02);
  headset.add(ring);
}
const boomCurve = new THREE.CatmullRomCurve3([
  new V3(-1.12, -0.2, 0.12),
  new V3(-1.0, -0.55, 0.42),
  new V3(-0.7, -0.74, 0.7),
  new V3(-0.42, -0.76, 0.8),
]);
headset.add(mesh(new THREE.TubeGeometry(boomCurve, 48, 0.026, 10), mat.neel));
const micTip = mesh(new THREE.SphereGeometry(0.068, 24, 16), mat.micTip);
micTip.scale.set(1.25, 1, 1);
micTip.position.copy(boomCurve.getPointAt(1));
headset.add(micTip);

// ---------- Lights and camera ----------

scene.add(new THREE.HemisphereLight(0xffffff, 0x7a6ae0, 0.9));
const key = new THREE.DirectionalLight(0xfff0dc, 2.6);
key.position.set(3.5, 5, 6);
key.castShadow = true;
key.shadow.mapSize.set(2048, 2048);
key.shadow.camera.left = -3;
key.shadow.camera.right = 3;
key.shadow.camera.top = 3.5;
key.shadow.camera.bottom = -3;
key.shadow.bias = -0.0004;
key.shadow.normalBias = 0.02;
key.shadow.radius = 6;
scene.add(key);
const fill = new THREE.DirectionalLight(0xdcd6ff, 0.9);
fill.position.set(-5, 1.5, 4);
scene.add(fill);
const rimViolet = new THREE.DirectionalLight(0x9b86ff, 3.2);
rimViolet.position.set(-4, 3, -5);
scene.add(rimViolet);
const rimPink = new THREE.DirectionalLight(0xff9ad0, 1.4);
rimPink.position.set(4.5, 1, -4);
scene.add(rimPink);

const camera = new THREE.PerspectiveCamera(28, 1, 0.1, 100);
const VIEWS = {
  // Head and shoulders, for the stage and hero spots.
  bust: { position: new V3(0.0, 0.62, 10.4), target: new V3(0, 0.5, 0), fov: 27 },
  // Face only, for small round avatars.
  headshot: { position: new V3(0.0, 1.12, 6.6), target: new V3(0, 1.02, 0), fov: 27 },
};
function setView(name) {
  const view = VIEWS[name];
  camera.fov = view.fov;
  camera.position.copy(view.position);
  camera.lookAt(view.target);
  camera.updateProjectionMatrix();
}
setView('bust');
root.rotation.y = -0.16;

// ---------- Poses ----------

const NEUTRAL = {
  headX: 0,
  headY: 0,
  headZ: 0,
  lookX: 0,
  lookY: 0,
  lidOpen: 1,
  lowerLid: 0,
  browUp: 0,
  browTilt: 0,
  mouthOpen: 0,
  mouthSmile: 1,
  mouthWidth: 1,
  breath: 0,
  bob: 0,
  bunBounce: 0,
};

function applyPose(input) {
  const p = { ...NEUTRAL, ...input };
  headPivot.rotation.set(p.headX, p.headY, p.headZ);
  body.position.y = p.bob;
  torso.scale.set(1 + p.breath * 0.006, 1 + p.breath * 0.012, 1 + p.breath * 0.01);
  for (const eye of eyes) {
    eye.ball.rotation.set(-p.lookY * 0.32, p.lookX * 0.42, 0);
    eye.upper.rotation.x = lerp(1.2, -0.66, p.lidOpen);
    eye.lower.rotation.x = lerp(1.0, 0.38, p.lowerLid);
  }
  for (const brow of brows) {
    brow.holder.position.copy(brow.base).add(new V3(0, p.browUp * 0.045 + p.browTilt * brow.side * 0.02, 0));
    brow.holder.rotation.z = brow.side * (-0.06 + p.browTilt * 0.12) - p.browUp * brow.side * 0.04;
  }
  bun.position.y = 1.12 + p.bunBounce * 0.03;
  bun.rotation.z = p.bunBounce * 0.05;
  buildMouth({ open: p.mouthOpen, smile: p.mouthSmile, width: p.mouthWidth });
}

const TAU = Math.PI * 2;
/** 0 to 1 and back over [start, end], for blinks. */
function pulse(t, start, end) {
  if (t < start || t > end) {
    return 0;
  }
  return Math.sin(((t - start) / (end - start)) * Math.PI);
}

/** Each loop maps t in [0, 1) to a pose; every term is periodic in t, so the loops repeat seamlessly. */
const LOOPS = {
  idle: {
    seconds: 4,
    pose: (t) => ({
      breath: Math.sin(TAU * t),
      headZ: 0.035 * Math.sin(TAU * t),
      headY: 0.05 * Math.sin(TAU * t + 1.1),
      headX: 0.02 * Math.sin(TAU * 2 * t),
      lookX: 0.12 * Math.sin(TAU * t + 0.6),
      lookY: 0.04 * Math.sin(TAU * 2 * t),
      lidOpen: 1 - pulse(t, 0.58, 0.66),
      mouthSmile: 1,
      bunBounce: Math.sin(TAU * t + 0.4) * 0.4,
    }),
  },
  talk: {
    seconds: 3,
    pose: (t) => {
      const syllables = 0.5 + 0.28 * Math.sin(TAU * 6 * t) + 0.18 * Math.sin(TAU * 9 * t + 1.3);
      const phrase = 0.6 + 0.4 * Math.sin(TAU * 2 * t - 0.6);
      return {
        breath: Math.sin(TAU * t),
        mouthOpen: Math.min(0.85, Math.max(0, 0.08 + syllables * phrase * 1.1)),
        mouthSmile: 0.75 + 0.15 * Math.sin(TAU * 2 * t),
        mouthWidth: 0.92 + 0.1 * Math.sin(TAU * 3 * t),
        headX: 0.045 * Math.sin(TAU * 2 * t),
        headY: 0.07 * Math.sin(TAU * t),
        headZ: 0.025 * Math.sin(TAU * t + 2),
        browUp: 0.35 + 0.35 * Math.sin(TAU * 2 * t + 0.4),
        lidOpen: 1 - pulse(t, 0.44, 0.52),
        lookX: 0.06 * Math.sin(TAU * t),
        bunBounce: Math.sin(TAU * 2 * t) * 0.5,
      };
    },
  },
  listen: {
    seconds: 3,
    pose: (t) => ({
      breath: Math.sin(TAU * t),
      headZ: 0.13 + 0.02 * Math.sin(TAU * t),
      headX: 0.04 + 0.045 * Math.max(0, Math.sin(TAU * 2 * t)),
      headY: -0.05,
      browUp: 0.55,
      mouthSmile: 0.95,
      lookX: -0.08,
      lookY: 0.02,
      lidOpen: 1 - pulse(t, 0.72, 0.8),
      bunBounce: Math.sin(TAU * 2 * t) * 0.3,
    }),
  },
  think: {
    seconds: 3,
    pose: (t) => ({
      breath: Math.sin(TAU * t),
      headZ: -0.09 + 0.015 * Math.sin(TAU * t),
      headY: 0.14,
      headX: -0.05,
      lookX: 0.65 + 0.08 * Math.sin(TAU * t),
      lookY: 0.62,
      browUp: 0.35,
      browTilt: 0.7,
      mouthSmile: 0.15,
      mouthWidth: 0.7 + 0.05 * Math.sin(TAU * 2 * t),
      lidOpen: 0.95 - pulse(t, 0.3, 0.38) * 0.95,
      bunBounce: Math.sin(TAU * t) * 0.2,
    }),
  },
  happy: {
    seconds: 2,
    pose: (t) => ({
      breath: Math.sin(TAU * t),
      bob: 0.05 * Math.abs(Math.sin(TAU * 2 * t)),
      headZ: 0.07 * Math.sin(TAU * 2 * t),
      headX: -0.03,
      browUp: 0.7,
      mouthOpen: 0.62 + 0.08 * Math.sin(TAU * 4 * t),
      mouthSmile: 1.6,
      mouthWidth: 1.18,
      lidOpen: 0.95,
      lowerLid: 1,
      bunBounce: Math.sin(TAU * 2 * t + 0.5),
    }),
  },
};

// ---------- API for render.mjs ----------

window.KAI = {
  loops: Object.fromEntries(Object.entries(LOOPS).map(([name, loop]) => [name, loop.seconds])),
  setView,
  /** Renders loop `name` at time t in [0, 1) and returns a PNG data URL. */
  frame(name, t) {
    applyPose(LOOPS[name].pose(t));
    renderer.render(scene, camera);
    return renderer.domElement.toDataURL('image/png');
  },
  /** Renders a custom pose (for stills). */
  still(pose) {
    applyPose(pose);
    renderer.render(scene, camera);
    return renderer.domElement.toDataURL('image/png');
  },
};
applyPose(LOOPS.idle.pose(0));
renderer.render(scene, camera);
window.KAI_READY = true;

/**
 * "Glow 3D" palette. The brand colours (neel ink, mango, rani, tulsi) sit on soft aurora backdrops; interactive things
 * are glossy 3D objects with a lit face and a darker edge underneath; panels are frosted glass.
 * Every text/background pair used here meets WCAG AA for normal text, except celebrate text on celebrateSoft (AA-large
 * only), so small text on that wash stays in `text` and Rani is kept for icons and big numbers.
 */
export const brand = {
  chalk: '#F7F7FB',
  neel: '#1F1A5C',
  mango: '#FFB21A',
  rani: '#D6246E',
  tulsi: '#137A52',
  chilli: '#C4321C',
  /** Depth and progress: paths, meters, the learner's own bubbles. */
  violet: '#6C4DF6',
} as const;

/** At least two stops, top to bottom unless a component says otherwise. */
export type Gradient = readonly [string, string, ...string[]];

/**
 * A 3D surface: the lit face, the darker side seen below it, the ink that sits on the face, and how strongly light
 * glints off its top (0 to 1).
 */
export type Material3D = { face: Gradient; edge: string; ink: string; gloss: number };

export type ColorTokens = {
  /** Solid page colour under the aurora backdrop (also the status bar area). */
  background: string;
  /** Solid raised areas that must stay opaque: inputs, sheets. */
  surface: string;
  surfaceMuted: string;
  /** Frosted glass panels over the backdrop. */
  glass: string;
  glassStrong: string;
  /** The bright top edge of glass, like light catching it. */
  glassBorder: string;
  /** Mango wash: Luna's notes, the "up next" step, gentle emphasis. */
  primarySoft: string;
  /** Violet wash: the path, selections, progress. */
  accentSoft: string;
  /** Rani wash behind streak celebrations. */
  celebrateSoft: string;
  text: string;
  textMuted: string;
  /** Outlines of interactive controls (inputs, choices): 3:1 against the page. */
  border: string;
  /** Quiet dividers and the edges of passive cards. */
  hairline: string;
  /** Coloured, soft shadows: resting cards, and things that float (buttons, the mic, Luna). */
  cardShadow: string;
  raisedShadow: string;
  /** Mango: the mic, primary buttons and highlights. Always paired with onPrimary text. */
  primary: string;
  onPrimary: string;
  primaryPressed: string;
  accent: string;
  onAccent: string;
  /** The highlighter swipe behind a learner's slip. */
  highlight: string;
  onHighlight: string;
  /** Rani: streaks and celebrations only. */
  celebrate: string;
  onCelebrate: string;
  success: string;
  successBackground: string;
  error: string;
  errorBackground: string;
  focusRing: string;
  overlay: string;
  /** 3D materials for buttons, chips and path nodes. */
  mango3d: Material3D;
  violet3d: Material3D;
  rani3d: Material3D;
  tulsi3d: Material3D;
  /** White (light) or deep glass (dark) 3D, for secondary buttons and choices. */
  plain3d: Material3D;
  /** Locked or unavailable. */
  locked3d: Material3D;
  /** A chosen option: a pale violet face on a violet edge. */
  selected3d: Material3D;
  /** Hero cards: a rich gradient that white text and Luna sit on. */
  heroGradient: Gradient;
  onHero: string;
  onHeroMuted: string;
  /** The learner's chat bubble. */
  learnerBubble: Gradient;
  onLearnerBubble: string;
};

export const lightColors: ColorTokens = {
  background: '#F3F0FF',
  surface: '#FFFFFF',
  surfaceMuted: '#ECE8FB',
  glass: 'rgba(255, 255, 255, 0.72)',
  glassStrong: 'rgba(255, 255, 255, 0.9)',
  glassBorder: 'rgba(255, 255, 255, 0.95)',
  primarySoft: '#FFF1CF',
  accentSoft: '#ECE6FF',
  celebrateSoft: '#FFE3EF',
  text: brand.neel,
  textMuted: '#544F7D',
  border: '#8C88B0',
  hairline: 'rgba(31, 26, 92, 0.1)',
  cardShadow: '0px 2px 6px rgba(76, 52, 200, 0.07), 0px 12px 32px rgba(76, 52, 200, 0.13)',
  raisedShadow: '0px 6px 14px rgba(76, 52, 200, 0.18), 0px 22px 44px rgba(76, 52, 200, 0.2)',
  primary: brand.mango,
  onPrimary: brand.neel,
  primaryPressed: '#F0A000',
  accent: brand.violet,
  onAccent: '#FFFFFF',
  highlight: '#FFD37A',
  onHighlight: brand.neel,
  celebrate: brand.rani,
  onCelebrate: '#FFFFFF',
  success: brand.tulsi,
  successBackground: '#DDF4E9',
  error: brand.chilli,
  errorBackground: '#FDE6E1',
  focusRing: brand.neel,
  overlay: 'rgba(31, 26, 92, 0.45)',
  mango3d: { face: ['#FFE07A', '#FFBE2E', '#FFA514'], edge: '#D97E00', ink: brand.neel, gloss: 0.55 },
  violet3d: { face: ['#9A82FF', '#6D4EF7', '#5A3AE8'], edge: '#3E25B0', ink: '#FFFFFF', gloss: 0.32 },
  rani3d: { face: ['#FF7AB8', '#D92877', '#C21A66'], edge: '#8E1150', ink: '#FFFFFF', gloss: 0.3 },
  tulsi3d: { face: ['#5FE6AD', '#26C285', '#14A36C'], edge: '#0C7A4F', ink: brand.neel, gloss: 0.45 },
  plain3d: { face: ['#FFFFFF', '#FAF8FF', '#EFEAFF'], edge: '#D3CBF5', ink: brand.neel, gloss: 0.6 },
  locked3d: { face: ['#ECE9F6', '#DEDAEE', '#D2CDE6'], edge: '#B5AFCF', ink: '#57527F', gloss: 0.4 },
  selected3d: { face: ['#FBFAFF', '#EEE8FF', '#E2D8FF'], edge: '#7A5CFF', ink: brand.neel, gloss: 0.6 },
  heroGradient: ['#7C5CFF', '#5B3FE6', '#3D2AB3'],
  onHero: '#FFFFFF',
  onHeroMuted: '#E2DCFF',
  learnerBubble: ['#7D61FF', '#6243F2'],
  onLearnerBubble: '#FFFFFF',
};

export const darkColors: ColorTokens = {
  background: '#110D33',
  surface: '#2A2470',
  surfaceMuted: '#2C2675',
  glass: 'rgba(255, 255, 255, 0.075)',
  glassStrong: 'rgba(42, 36, 112, 0.92)',
  glassBorder: 'rgba(255, 255, 255, 0.16)',
  primarySoft: 'rgba(255, 178, 26, 0.16)',
  accentSoft: 'rgba(146, 120, 255, 0.2)',
  celebrateSoft: 'rgba(255, 95, 162, 0.18)',
  text: brand.chalk,
  textMuted: '#CBC7EE',
  border: '#8A84C9',
  hairline: 'rgba(255, 255, 255, 0.12)',
  cardShadow: '0px 2px 6px rgba(0, 0, 0, 0.25), 0px 14px 34px rgba(5, 2, 30, 0.45)',
  raisedShadow: '0px 6px 16px rgba(0, 0, 0, 0.35), 0px 22px 48px rgba(5, 2, 30, 0.55)',
  primary: brand.mango,
  onPrimary: brand.neel,
  primaryPressed: '#FFC24D',
  accent: '#9C86FF',
  onAccent: '#FFFFFF',
  highlight: '#FFC857',
  onHighlight: brand.neel,
  celebrate: '#FF78AE',
  onCelebrate: brand.neel,
  success: '#5FD3A0',
  successBackground: 'rgba(95, 211, 160, 0.16)',
  error: '#FF8C75',
  errorBackground: 'rgba(255, 120, 100, 0.16)',
  focusRing: brand.mango,
  overlay: 'rgba(8, 6, 32, 0.6)',
  mango3d: { face: ['#FFE07A', '#FFBE2E', '#FFA514'], edge: '#B86A00', ink: brand.neel, gloss: 0.5 },
  violet3d: { face: ['#A48FFF', '#7050FA', '#5D3EEC'], edge: '#3A239E', ink: '#FFFFFF', gloss: 0.28 },
  rani3d: { face: ['#FF7AB8', '#D92877', '#C21A66'], edge: '#7E0E46', ink: '#FFFFFF', gloss: 0.26 },
  tulsi3d: { face: ['#70EDB9', '#33CC90', '#1FAE76'], edge: '#0B6B45', ink: brand.neel, gloss: 0.4 },
  plain3d: { face: ['#3F3896', '#342D85', '#2B2575'], edge: '#17124A', ink: brand.chalk, gloss: 0.12 },
  locked3d: { face: ['#3A3570', '#322D63', '#2B2757'], edge: '#1A1640', ink: '#B3AED9', gloss: 0.08 },
  selected3d: { face: ['#5A4BC4', '#4A3DAE', '#3E3399'], edge: '#9C86FF', ink: brand.chalk, gloss: 0.16 },
  heroGradient: ['#7C5CFF', '#5136D8', '#2E1F8F'],
  onHero: '#FFFFFF',
  onHeroMuted: '#DCD5FF',
  learnerBubble: ['#7A5CFF', '#5B3BE8'],
  onLearnerBubble: '#FFFFFF',
};

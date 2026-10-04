export const space = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  xxxl: 48,
} as const;

export const radius = {
  sm: 8,
  md: 14,
  lg: 20,
  pill: 999,
} as const;

/** Minimum touch target (Material guidance, and our quality floor). */
export const TOUCH_TARGET = 48;

export const MIC_SIZE = 88;

/** Keeps text columns readable on tablets and the web build. */
export const MAX_CONTENT_WIDTH = 560;

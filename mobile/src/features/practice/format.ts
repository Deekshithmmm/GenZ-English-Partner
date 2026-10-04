/** "45s", "2m", "1m 5s": short enough for a stat tile. */
export function formatSpeakingTime(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  if (minutes === 0) {
    return `${rest}s`;
  }
  return rest === 0 ? `${minutes}m` : `${minutes}m ${rest}s`;
}

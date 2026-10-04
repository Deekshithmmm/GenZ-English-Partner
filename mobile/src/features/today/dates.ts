const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

/**
 * "Wednesday, 1 October" from a date the server already resolved in the learner's time zone (YYYY-MM-DD).
 * Read as a plain calendar date, so the device's own offset can't shift it by a day.
 */
export function formatLongDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  if (!year || !month || !day) {
    return '';
  }
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return `${WEEKDAYS[weekday]}, ${day} ${MONTHS[month - 1]}`;
}

/** 0 for Monday … 6 for Sunday, for the same kind of date. */
export function mondayFirstIndex(isoDate: string): number {
  const [year, month, day] = isoDate.split('-').map(Number);
  if (!year || !month || !day) {
    return 0;
  }
  return (new Date(Date.UTC(year, month - 1, day)).getUTCDay() + 6) % 7;
}

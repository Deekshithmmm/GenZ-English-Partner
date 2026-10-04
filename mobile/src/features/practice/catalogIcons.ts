import type { IconName } from '@/components/Icon';
import type { Icon3DName } from '@/components/Icon3D';

/** Pictures for free-talk topics and roleplays, by slug. Anything new falls back to a chat bubble. */
const TOPIC_ICONS: Record<string, IconName> = {
  anything: 'chatbubbles-outline',
  'college-life': 'school-outline',
  'movies-and-series': 'film-outline',
  food: 'restaurant-outline',
  'cricket-and-sports': 'trophy-outline',
  'career-plans': 'briefcase-outline',
  travel: 'airplane-outline',
  'tech-and-gadgets': 'phone-portrait-outline',
  festivals: 'sparkles-outline',
  music: 'musical-notes-outline',
  'social-media': 'share-social-outline',
  'weekend-plans': 'calendar-outline',
};

const ROLEPLAY_ICONS: Record<string, IconName> = {
  'ordering-at-cafe': 'cafe-outline',
  'tell-me-about-yourself': 'person-outline',
  'tech-interview-intro': 'code-slash-outline',
  'hr-interview': 'briefcase-outline',
  'group-discussion': 'people-outline',
  'first-day-at-office': 'business-outline',
  'pitch-project-idea': 'bulb-outline',
  'college-viva': 'school-outline',
  'college-fest-friends': 'happy-outline',
  'customer-care-call': 'call-outline',
  'calling-landlord': 'home-outline',
  'ask-manager-for-leave': 'calendar-outline',
};

export function topicIcon(slug: string | null | undefined): IconName {
  return (slug && TOPIC_ICONS[slug]) || 'chatbubbles-outline';
}

export function roleplayIcon(slug: string | null | undefined): IconName {
  return (slug && ROLEPLAY_ICONS[slug]) || 'people-outline';
}

/** 3D pictures for the same topics and roleplays, for lists and cards. */
const TOPIC_ICONS_3D: Record<string, Icon3DName> = {
  anything: 'speech',
  'college-life': 'college',
  'movies-and-series': 'movies',
  food: 'food',
  'cricket-and-sports': 'cricket',
  'career-plans': 'career',
  travel: 'travel',
  'tech-and-gadgets': 'tech',
  festivals: 'festival',
  music: 'music',
  'social-media': 'social',
  'weekend-plans': 'weekend',
};

const ROLEPLAY_ICONS_3D: Record<string, Icon3DName> = {
  'ordering-at-cafe': 'cafe',
  'tell-me-about-yourself': 'wave',
  'tech-interview-intro': 'laptop',
  'hr-interview': 'necktie',
  'group-discussion': 'group',
  'first-day-at-office': 'office',
  'pitch-project-idea': 'bulb',
  'college-viva': 'college',
  'college-fest-friends': 'balloon',
  'customer-care-call': 'phone',
  'calling-landlord': 'house',
  'ask-manager-for-leave': 'weekend',
};

export function topicIcon3d(slug: string | null | undefined): Icon3DName {
  return (slug && TOPIC_ICONS_3D[slug]) || 'speech';
}

export function roleplayIcon3d(slug: string | null | undefined): Icon3DName {
  return (slug && ROLEPLAY_ICONS_3D[slug]) || 'group';
}

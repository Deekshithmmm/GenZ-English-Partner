import { Image } from 'expo-image';
import type { StyleProp, ImageStyle } from 'react-native';

/**
 * Glossy 3D icons (Microsoft Fluent Emoji, MIT licence: assets/3d/LICENSE-fluent-emoji.txt) for topics, rewards and
 * lessons. Small UI controls keep the line icons in Icon; these are for things worth a second look.
 * Add one with tools/icons-3d/fetch_icons.py, then list it here.
 */
const ICONS = {
  alarm: require('../../assets/3d/alarm.webp'),
  badge: require('../../assets/3d/badge.webp'),
  balloon: require('../../assets/3d/balloon.webp'),
  book: require('../../assets/3d/book.webp'),
  books: require('../../assets/3d/books.webp'),
  brain: require('../../assets/3d/brain.webp'),
  bulb: require('../../assets/3d/bulb.webp'),
  cafe: require('../../assets/3d/cafe.webp'),
  camera: require('../../assets/3d/camera.webp'),
  cards: require('../../assets/3d/cards.webp'),
  career: require('../../assets/3d/career.webp'),
  chai: require('../../assets/3d/chai.webp'),
  chart: require('../../assets/3d/chart.webp'),
  check: require('../../assets/3d/check.webp'),
  college: require('../../assets/3d/college.webp'),
  compass: require('../../assets/3d/compass.webp'),
  cricket: require('../../assets/3d/cricket.webp'),
  crown: require('../../assets/3d/crown.webp'),
  crystal: require('../../assets/3d/crystal.webp'),
  envelope: require('../../assets/3d/envelope.webp'),
  festival: require('../../assets/3d/festival.webp'),
  fire: require('../../assets/3d/fire.webp'),
  food: require('../../assets/3d/food.webp'),
  gear: require('../../assets/3d/gear.webp'),
  gem: require('../../assets/3d/gem.webp'),
  group: require('../../assets/3d/group.webp'),
  headphone: require('../../assets/3d/headphone.webp'),
  hourglass: require('../../assets/3d/hourglass.webp'),
  'hourglass-done': require('../../assets/3d/hourglass-done.webp'),
  house: require('../../assets/3d/house.webp'),
  hundred: require('../../assets/3d/hundred.webp'),
  label: require('../../assets/3d/label.webp'),
  laptop: require('../../assets/3d/laptop.webp'),
  letters: require('../../assets/3d/letters.webp'),
  link: require('../../assets/3d/link.webp'),
  lock: require('../../assets/3d/lock.webp'),
  map: require('../../assets/3d/map.webp'),
  medal: require('../../assets/3d/medal.webp'),
  memo: require('../../assets/3d/memo.webp'),
  mic: require('../../assets/3d/mic.webp'),
  movies: require('../../assets/3d/movies.webp'),
  music: require('../../assets/3d/music.webp'),
  necktie: require('../../assets/3d/necktie.webp'),
  office: require('../../assets/3d/office.webp'),
  party: require('../../assets/3d/party.webp'),
  person: require('../../assets/3d/person.webp'),
  phone: require('../../assets/3d/phone.webp'),
  puzzle: require('../../assets/3d/puzzle.webp'),
  question: require('../../assets/3d/question.webp'),
  'raised-hand': require('../../assets/3d/raised-hand.webp'),
  rewind: require('../../assets/3d/rewind.webp'),
  rocket: require('../../assets/3d/rocket.webp'),
  scale: require('../../assets/3d/scale.webp'),
  scroll: require('../../assets/3d/scroll.webp'),
  seedling: require('../../assets/3d/seedling.webp'),
  social: require('../../assets/3d/social.webp'),
  sparkles: require('../../assets/3d/sparkles.webp'),
  speaker: require('../../assets/3d/speaker.webp'),
  speaking: require('../../assets/3d/speaking.webp'),
  speech: require('../../assets/3d/speech.webp'),
  star: require('../../assets/3d/star.webp'),
  sun: require('../../assets/3d/sun.webp'),
  target: require('../../assets/3d/target.webp'),
  tech: require('../../assets/3d/tech.webp'),
  thought: require('../../assets/3d/thought.webp'),
  toolbox: require('../../assets/3d/toolbox.webp'),
  travel: require('../../assets/3d/travel.webp'),
  trophy: require('../../assets/3d/trophy.webp'),
  wave: require('../../assets/3d/wave.webp'),
  weekend: require('../../assets/3d/weekend.webp'),
} as const;

export type Icon3DName = keyof typeof ICONS;

/** For icon names that come from the server: the name if the app has it, otherwise the fallback. */
export function icon3d(name: string | null | undefined, fallback: Icon3DName): Icon3DName {
  return name && name in ICONS ? (name as Icon3DName) : fallback;
}

type Props = {
  name: Icon3DName;
  size?: number;
  style?: StyleProp<ImageStyle>;
  /** Decorative by default; pass a label when the icon is the only thing saying what something is. */
  accessibilityLabel?: string;
};

export function Icon3D({ name, size = 32, style, accessibilityLabel }: Props) {
  return (
    <Image
      source={ICONS[name]}
      style={[{ width: size, height: size }, style]}
      contentFit="contain"
      accessible={Boolean(accessibilityLabel)}
      accessibilityLabel={accessibilityLabel}
      accessibilityElementsHidden={!accessibilityLabel}
      importantForAccessibility={accessibilityLabel ? 'yes' : 'no-hide-descendants'}
    />
  );
}

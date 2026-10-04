import type { LessonKind } from '@/api/types';
import { icon3d, type Icon3DName } from '@/components/Icon3D';
import type { ColorTokens } from '@/theme/colors';

/** What each kind of lesson is called on the path and in the lesson. */
export const KIND_LABEL: Record<LessonKind, string> = {
  LEARN: 'Learn',
  BOOST: 'Fluency booster',
  REVIEW: 'Revise',
  MASTER: 'Master it',
};

/** The big word on a lesson's title card. */
export const KIND_KICKER: Record<LessonKind, string> = {
  LEARN: 'LEARN',
  BOOST: 'FLUENCY BOOSTER',
  REVIEW: 'REVISE',
  MASTER: 'MASTER IT',
};

const KIND_ICON: Record<Exclude<LessonKind, 'LEARN'>, Icon3DName> = { BOOST: 'rocket', REVIEW: 'rewind', MASTER: 'crown' };

/** Each kind's name has its own colour: violet, rani, green, and plain ink for master rounds. */
export function kindColorOf(kind: LessonKind, colors: ColorTokens): string {
  return { LEARN: colors.accent, BOOST: colors.celebrate, REVIEW: colors.success, MASTER: colors.text }[kind];
}

/** A Learn lesson shows its topic's icon; the others show what kind of lesson they are. */
export function kindIcon(kind: LessonKind, topicIcon: string | null | undefined): Icon3DName {
  return kind === 'LEARN' ? icon3d(topicIcon, 'book') : KIND_ICON[kind];
}

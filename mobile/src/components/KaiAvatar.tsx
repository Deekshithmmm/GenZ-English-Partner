import { KaiFace } from './Kai3D';

type Props = {
  size?: number;
};

/** Luna's face. Decorative: the name next to it carries the meaning. */
export function KaiAvatar({ size = 40 }: Props) {
  return <KaiFace size={size} />;
}

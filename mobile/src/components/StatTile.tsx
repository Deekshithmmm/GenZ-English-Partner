import { StyleSheet, View } from 'react-native';

import { useTheme } from '@/theme/ThemeProvider';
import { radius, space } from '@/theme/layout';

import { AppText } from './AppText';
import { Icon, type IconName } from './Icon';
import { Icon3D, type Icon3DName } from './Icon3D';

type Props = {
  icon: IconName;
  /** A 3D icon instead of the line icon. */
  icon3d?: Icon3DName;
  /** Colour for the icon only; numbers and labels always stay in text colours. */
  iconColor?: string;
  value: string;
  label: string;
  /** Small supporting line, e.g. "Best: 12 days". */
  detail?: string;
};

/** One headline number in a KPI row. */
export function StatTile({ icon, icon3d, iconColor, value, label, detail }: Props) {
  const { colors } = useTheme();
  return (
    <View
      style={[
        styles.tile,
        {
          backgroundColor: colors.glass,
          borderColor: colors.glassBorder,
          boxShadow: colors.cardShadow,
        },
      ]}
      accessible
      accessibilityLabel={[`${value} ${label}`, detail].filter(Boolean).join('. ')}>
      <View style={styles.top}>
        {icon3d ? <Icon3D name={icon3d} size={26} /> : <Icon name={icon} size={20} color={iconColor ?? colors.text} />}
        <AppText variant="number">{value}</AppText>
      </View>
      <AppText variant="label" tone="muted">
        {label}
      </AppText>
      {detail ? (
        <AppText variant="small" tone="muted">
          {detail}
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  tile: {
    flex: 1,
    borderRadius: radius.lg + 4,
    borderWidth: 1,
    paddingHorizontal: space.lg,
    paddingVertical: space.md,
    gap: 2,
  },
  top: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
});

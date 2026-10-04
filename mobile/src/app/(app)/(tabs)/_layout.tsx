import { Tabs } from 'expo-router/js-tabs';
import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon3D, type Icon3DName } from '@/components/Icon3D';
import { useTheme } from '@/theme/ThemeProvider';
import { fonts } from '@/theme/typography';

/** Taller than the 49pt default so the 3D icons and 12pt labels fit without clipping. */
const TAB_BAR_HEIGHT = 64;

/** The selected tab grows, sits in a violet pill and gets a bold label, so it never relies on colour alone. */
function tabIcon(icon: Icon3DName) {
  return function TabIcon({ focused }: { focused: boolean }) {
    return <ActivePill focused={focused} icon={icon} />;
  };
}

function ActivePill({ focused, icon }: { focused: boolean; icon: Icon3DName }) {
  const { colors } = useTheme();
  return (
    <View
      style={{
        width: 58,
        height: 34,
        borderRadius: 17,
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: focused ? colors.accentSoft : 'transparent',
      }}>
      <Icon3D name={icon} size={focused ? 28 : 24} style={{ opacity: focused ? 1 : 0.72 }} />
    </View>
  );
}

export default function TabsLayout() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.text,
        tabBarInactiveTintColor: colors.textMuted,
        // A custom height replaces the computed one, so the bottom inset (home indicator) is added back here.
        tabBarStyle: {
          backgroundColor: colors.glassStrong,
          borderTopColor: colors.hairline,
          height: TAB_BAR_HEIGHT + insets.bottom,
          paddingTop: 4,
          boxShadow: '0px -8px 28px rgba(76, 52, 200, 0.12)',
        },
        tabBarLabelStyle: { fontFamily: fonts.bodyBold, fontSize: 12, lineHeight: 16 },
        sceneStyle: { backgroundColor: colors.background },
      }}>
      <Tabs.Screen
        name="today"
        options={{ title: 'Today', tabBarIcon: tabIcon('sun'), tabBarButtonTestID: 'tab-today' }}
      />
      <Tabs.Screen
        name="talk"
        options={{
          title: 'Talk',
          tabBarIcon: tabIcon('speech'),
          tabBarButtonTestID: 'tab-talk',
        }}
      />
      <Tabs.Screen
        name="path"
        options={{ title: 'Path', tabBarIcon: tabIcon('map'), tabBarButtonTestID: 'tab-path' }}
      />
      <Tabs.Screen
        name="me"
        options={{
          title: 'You',
          tabBarIcon: tabIcon('medal'),
          tabBarButtonTestID: 'tab-me',
        }}
      />
    </Tabs>
  );
}

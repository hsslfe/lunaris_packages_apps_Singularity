#!/usr/bin/env python3
"""Source-only regression checks; run the Settings build for Android integration."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'res'
ANDROID = '{http://schemas.android.com/apk/res/android}'
PREFERENCE = ROOT / 'src/org/lunaris/settings/preferences/SingularityDashboardPreference.java'


def inflate_layout(name):
    """Expand includes to validate IDs within each card's actual subtree."""
    root = ET.parse(RES / 'layout' / (name + '.xml')).getroot()
    for parent in list(root.iter()):
        for index, child in enumerate(list(parent)):
            if child.tag == 'include':
                expanded = inflate_layout(child.get('layout').split('/')[1])
                expanded.attrib.update({k: v for k, v in child.attrib.items() if k != 'layout'})
                parent.remove(child)
                parent.insert(index, expanded)
    return root


def colors(folder):
    return {e.get('name'): e.text for e in ET.parse(
        RES / folder / 'singularity_dashboard_colors.xml').getroot()}


def luminance(hex_color):
    values = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in values]
    return sum(v * w for v, w in zip(linear, (0.2126, 0.7152, 0.0722)))


class DashboardSourceChecks(unittest.TestCase):
    def test_navigation_preserves_original_destinations(self):
        prefs = ET.parse(RES / 'xml/lunaris_settings.xml').getroot()
        by_key = {p.get(ANDROID + 'key'): p for p in prefs}
        original = {
            'themes': 'themes.Themes', 'quick_settings': 'quicksettings.QuickSettings',
            'notifications': 'notifications.Notifications', 'lock_screen': 'lockscreen.LockScreen',
            'status_bar': 'statusbar.StatusBar', 'miscellaneous': 'miscellaneous.Miscellaneous',
            'buttons': 'buttons.Buttons', 'sound': 'sound.Sound',
        }
        for key, fragment in original.items():
            self.assertEqual('org.lunaris.settings.fragments.' + fragment,
                             by_key[key].get(ANDROID + 'fragment'))
        self.assertEqual(len(by_key), len(prefs), 'Duplicate preference key')
        targets = re.findall(r'"([a-z_]+)"\s*\);', PREFERENCE.read_text())
        self.assertGreaterEqual(len(targets), 12)
        for target in targets:
            self.assertIn(target, by_key)
        for pref in prefs:
            fragment = pref.get(ANDROID + 'fragment', '')
            if fragment.startswith('org.lunaris.'):
                self.assertTrue((ROOT / 'src' / (fragment.replace('.', '/') + '.java')).is_file())
            elif pref.tag == 'Preference' and not fragment:
                self.assertIsNotNone(pref.find('intent'))

    def test_binding_ids_exist_within_each_card(self):
        layout = inflate_layout('preference_singularity_dashboard')
        ids = {e.get(ANDROID + 'id', '').split('/')[-1]: e for e in layout.iter()}
        cards = re.findall(r'bind\(root, R.id.(\w+),', PREFERENCE.read_text())
        self.assertEqual(13, len(cards))
        for card in cards:
            children = {e.get(ANDROID + 'id', '').split('/')[-1]: e for e in ids[card].iter()}
            self.assertEqual('TextView', children['singularity_card_title'].tag)
            self.assertEqual('ImageView', children['singularity_card_icon'].tag)
        for card in ('singularity_monet', 'singularity_themes', 'singularity_aod'):
            self.assertTrue(any(e.get(ANDROID + 'id') == '@+id/singularity_card_trailing'
                                for e in ids[card].iter()))

    def test_day_and_night_palettes_are_complete_and_readable(self):
        day, night = colors('values'), colors('values-night')
        self.assertEqual(day.keys(), night.keys())
        self.assertGreater(luminance(day['singularity_background']), 0.9)
        self.assertLess(luminance(night['singularity_background']), 0.01)
        for palette in (day, night):
            for text in ('singularity_text_primary', 'singularity_text_secondary'):
                for background in ('singularity_background', 'singularity_card_start',
                                   'singularity_card_center', 'singularity_card_end'):
                    a, b = sorted((luminance(palette[text]), luminance(palette[background])))
                    self.assertGreaterEqual((b + 0.05) / (a + 0.05), 4.5, (text, background))
        styles = ET.parse(RES / 'values/singularity_dashboard_styles.xml').getroot()
        for item in styles.iter('item'):
            if item.get('name') == 'android:textColor':
                self.assertTrue(item.text.startswith('@color/singularity_'))

    def test_new_resource_references_resolve(self):
        resources = {}
        for p in RES.glob('*/*.xml'):
            tree = ET.parse(p).getroot()
            folder = p.parent.name.split('-')[0]
            if folder == 'values':
                for e in tree:
                    resources.setdefault(e.get('type', e.tag), set()).add(e.get('name'))
            else:
                resources.setdefault(folder, set()).add(p.stem)
        for p in RES.glob('*/*singularity*.xml'):
            for resource_type, name in re.findall(r'@(color|drawable|layout|string|style)/(\w+)', p.read_text()):
                self.assertIn(name, resources.get(resource_type, set()), (p.name, name))

    def test_wallpaper_blur_is_scoped_to_image_layer(self):
        layout = inflate_layout('preference_singularity_dashboard')
        card = next(e for e in layout.iter() if e.get(ANDROID + 'id') == '@+id/singularity_lockscreen')
        previews = [e for e in card if e.tag.endswith('BlurredLockscreenWallpaperView')]
        self.assertEqual(1, len(previews))
        self.assertTrue(any(e.tag == 'TextClock' for e in card.iter()))
        self.assertEqual('true', card.get(ANDROID + 'clipToOutline'))
        source = (ROOT / 'src/org/lunaris/settings/utils/BlurredLockscreenWallpaperView.java').read_text()
        self.assertNotIn('setBitmap(', source)
        self.assertNotIn('setStream(', source)


if __name__ == '__main__':
    unittest.main(verbosity=2)

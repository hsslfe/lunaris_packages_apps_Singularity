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
        self.assertEqual(12, len(cards))
        for card in cards:
            children = {e.get(ANDROID + 'id', '').split('/')[-1]: e for e in ids[card].iter()}
            self.assertEqual('TextView', children['singularity_card_title'].tag)
            self.assertEqual('ImageView', children['singularity_card_icon'].tag)
        for card in ('singularity_monet', 'singularity_themes', 'singularity_aod'):
            self.assertTrue(any(e.get(ANDROID + 'id') == '@+id/singularity_card_trailing'
                                for e in ids[card].iter()))

    def test_day_and_night_palettes_follow_settings_homepage(self):
        day, night = colors('values'), colors('values-night')
        self.assertEqual(day.keys(), night.keys())
        for mode, palette in (('light', day), ('dark', night)):
            tokens = {'background': 'surface_container', 'card': 'surface_bright',
                      'text_primary': 'on_surface', 'text_secondary': 'on_surface_variant',
                      'accent': 'primary'}
            for name, token in tokens.items():
                self.assertEqual('@android:color/system_' + token + '_' + mode,
                                 palette['singularity_' + name])
        styles = ET.parse(RES / 'values/singularity_dashboard_styles.xml').getroot()
        for item in styles.iter('item'):
            if item.get('name') == 'android:textColor':
                self.assertTrue(item.text.startswith('@color/singularity_'))
        card = ET.parse(RES / 'drawable/singularity_card_background.xml').getroot()
        self.assertFalse(list(card.iter('stroke')), 'Homepage cards have filled surfaces')
        self.assertFalse(list(card.iter('gradient')))

    def test_weighted_shortcut_rows_measure_their_own_height(self):
        layout = inflate_layout('preference_singularity_dashboard')
        rows = [e for e in layout.iter() if e.get(ANDROID + 'orientation') == 'horizontal'
                and any(c.get(ANDROID + 'layout_weight') == '1' for c in e)]
        shortcut_rows = [e for e in rows if e.get(ANDROID + 'baselineAligned') == 'false']
        self.assertEqual(3, len(shortcut_rows))
        for row in shortcut_rows:
            for card in row:
                if card.get(ANDROID + 'layout_weight') == '1':
                    # A MATCH_PARENT child in an unconstrained, WRAP_CONTENT
                    # horizontal row can be forced to zero height by LinearLayout.
                    self.assertEqual('wrap_content', card.get(ANDROID + 'layout_height'))
                    self.assertGreaterEqual(int(card.get(ANDROID + 'minHeight')[:-2]), 48)

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

    def test_wallpaper_is_dimmed_and_team_is_replaced_by_sound(self):
        layout = inflate_layout('preference_singularity_dashboard')
        card = next(e for e in layout.iter() if e.get(ANDROID + 'id') == '@+id/singularity_lockscreen')
        previews = [e for e in card if e.tag.endswith('LockscreenWallpaperPreviewView')]
        self.assertEqual(1, len(previews))
        self.assertTrue(any(e.tag == 'TextClock' for e in card.iter()))
        self.assertEqual('true', card.get(ANDROID + 'clipToOutline'))
        dim = next(e for e in card if e.get(ANDROID + 'id') == '@+id/singularity_lock_dim')
        self.assertEqual('0.3', dim.get(ANDROID + 'alpha'))
        self.assertEqual('@drawable/singularity_wallpaper_dim', dim.get(ANDROID + 'background'))
        shade = ET.parse(RES / 'drawable/singularity_wallpaper_dim.xml').getroot()
        self.assertEqual('@android:color/black', shade.find('solid').get(ANDROID + 'color'))
        self.assertEqual('88dp', card.get(ANDROID + 'minHeight'))
        self.assertFalse(any(e.get(ANDROID + 'id') == '@+id/singularity_team' for e in layout.iter()))
        sound = [e for e in layout.iter() if e.get(ANDROID + 'id') == '@+id/singularity_sound']
        self.assertEqual(1, len(sound))
        self.assertEqual('match_parent', sound[0].get(ANDROID + 'layout_width'))
        source = (ROOT / 'src/org/lunaris/settings/utils/LockscreenWallpaperPreviewView.java').read_text()
        self.assertNotIn('setRenderEffect(', source)
        self.assertNotIn('setBitmap(', source)
        self.assertNotIn('setStream(', source)


if __name__ == '__main__':
    unittest.main(verbosity=2)

"""Downloads the 3D icons the app uses from Microsoft's Fluent Emoji (MIT licence) and shrinks them to WebP.

Usage: python fetch_icons.py   (writes ../../assets/3d/<key>.webp and the licence)
Add an icon by adding a line to ICONS: app key -> Fluent Emoji folder name (see github.com/microsoft/fluentui-emoji).
"""
import io
import os
import sys
import urllib.parse
import urllib.request

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'assets', '3d'))
BASE = 'https://raw.githubusercontent.com/microsoft/fluentui-emoji/main'
SIZE = 160

ICONS = {
    # Rewards and progress
    'fire': 'Fire',
    'star': 'Glowing star',
    'gem': 'Gem stone',
    'trophy': 'Trophy',
    'medal': '1st place medal',
    'party': 'Party popper',
    'sparkles': 'Sparkles',
    'crown': 'Crown',
    'hundred': 'Hundred points',
    'chart': 'Chart increasing',
    'target': 'Bullseye',
    'rocket': 'Rocket',
    'check': 'Check mark button',
    'lock': 'Locked',
    'map': 'World map',
    # Speaking and learning
    'mic': 'Studio microphone',
    'headphone': 'Headphone',
    'speech': 'Speech balloon',
    'thought': 'Thought balloon',
    'bulb': 'Light bulb',
    'books': 'Books',
    'book': 'Open book',
    'memo': 'Memo',
    'cards': 'Card index dividers',
    'sun': 'Sun',
    'seedling': 'Seedling',
    'brain': 'Brain',
    'speaker': 'Speaker high volume',
    # Topics
    'college': 'Graduation cap',
    'movies': 'Clapper board',
    'food': 'Curry rice',
    'cricket': 'Cricket game',
    'career': 'Briefcase',
    'travel': 'Airplane',
    'tech': 'Mobile phone',
    'festival': 'Diya lamp',
    'music': 'Musical notes',
    'social': 'Camera with flash',
    'weekend': 'Spiral calendar',
    # Roleplays
    'cafe': 'Hot beverage',
    'chai': 'Teacup without handle',
    'badge': 'Name badge',
    'laptop': 'Laptop',
    'necktie': 'Necktie',
    'group': 'Busts in silhouette',
    'office': 'Office building',
    'balloon': 'Balloon',
    'phone': 'Telephone receiver',
    'house': 'House',
    # Grammar topics
    'letters': 'Input latin lowercase',
    'alarm': 'Alarm clock',
    'hourglass': 'Hourglass not done',
    'hourglass-done': 'Hourglass done',
    'question': 'Red question mark',
    'scroll': 'Scroll',
    'camera': 'Movie camera',
    'scale': 'Balance scale',
    'crystal': 'Crystal ball',
    'envelope': 'Envelope',
    'compass': 'Compass',
    'puzzle': 'Puzzle piece',
    'gear': 'Gear',
    'link': 'Link',
    'person': 'Bust in silhouette',
    'rewind': 'Counterclockwise arrows button',
    'toolbox': 'Toolbox',
    'label': 'Label',
    'speaking': 'Speaking head',
    # Skin-tone emoji use their default (yellow) tone.
    'wave': 'Waving hand*',
    'raised-hand': 'Raising hands*',
}


def fetch(url):
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read()


def main():
    os.makedirs(OUT, exist_ok=True)
    missing = []
    for key, folder in ICONS.items():
        target = os.path.join(OUT, f'{key}.webp')
        if os.path.exists(target):
            continue
        toned = folder.endswith('*')
        folder = folder.rstrip('*')
        file = folder.lower().replace(' ', '_').replace('-', '_') + ('_3d_default.png' if toned else '_3d.png')
        variant = 'Default/3D' if toned else '3D'
        url = f'{BASE}/assets/{urllib.parse.quote(folder)}/{variant}/{urllib.parse.quote(file)}'
        try:
            image = Image.open(io.BytesIO(fetch(url))).convert('RGBA')
        except Exception as error:  # noqa: BLE001 - report every icon that failed, then stop
            missing.append(f'{key} ({folder}): {error}')
            continue
        image.resize((SIZE, SIZE), Image.LANCZOS).save(target, quality=90, method=6)
        print(f'{key}: {os.path.getsize(target) // 1024} KB')
    licence = os.path.join(OUT, 'LICENSE-fluent-emoji.txt')
    if not os.path.exists(licence):
        with open(licence, 'wb') as f:
            f.write(b'3D icons from Microsoft Fluent Emoji (https://github.com/microsoft/fluentui-emoji)\n\n')
            f.write(fetch(f'{BASE}/LICENSE'))
    if missing:
        print('missing:\n  ' + '\n  '.join(missing))
        sys.exit(1)


if __name__ == '__main__':
    main()

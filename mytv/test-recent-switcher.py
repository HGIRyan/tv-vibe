#!/usr/bin/env python3
"""TV smoke test: open Recent apps with the physical cog button before running.
Checks close-mode selection/cancel without closing apps, then opens one recent app and returns Home.
Usage: TV_HOST=10.0.0.183 tools/venv/bin/python -I mytv/test-recent-switcher.py
"""
import base64
from pathlib import Path
import re
import sys
import time
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import tv


def main():
    device = tv.connect(timeout=30)
    try:
        def hierarchy():
            device.shell('uiautomator dump /data/local/tmp/recent-switcher-test.xml', read_timeout_s=40)
            return ET.fromstring(device.shell('cat /data/local/tmp/recent-switcher-test.xml'))

        def has(root, text):
            return any(n.get('text') == text for n in root.iter('node'))

        def tap(node):
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            device.shell(f'input tap {(x1 + x2) // 2} {(y1 + y2) // 2}')

        def app_card(root):
            return next(n for n in root.iter('node')
                        if n.get('clickable') == 'true' and n.get('content-desc'))

        assert 'RecentAppsDrawer' in device.shell('dumpsys activity activities | grep topResumedActivity'), \
            'Open Recent apps with the physical cog button before running this test'
        root = hierarchy()
        assert has(root, 'OK to switch apps  ·  Back to dismiss')
        assert has(root, 'Close apps…') and not has(root, 'Close selected')
        first = app_card(root)
        label = first.get('content-desc')
        device.shell('screencap -p /data/local/tmp/recent-switcher-test.png')
        Path('/private/tmp/tv-recent-switcher.png').write_bytes(base64.b64decode(
            device.shell('base64 /data/local/tmp/recent-switcher-test.png', read_timeout_s=30)))
        tap(next(n for n in root.iter('node') if n.get('text') == 'Close apps…'))
        root = hierarchy()
        assert has(root, 'Close selected') and has(root, 'Close all') and has(root, 'Done')
        tap(app_card(root))
        assert has(hierarchy(), '✓ Will close'), 'Close mode should select rather than launch'
        device.shell('input keyevent KEYCODE_BACK')
        root = hierarchy()
        assert has(root, 'OK to switch apps  ·  Back to dismiss') and not has(root, '✓ Will close')
        tap(app_card(root))
        deadline = time.monotonic() + 15
        resumed = ''
        while time.monotonic() < deadline:
            resumed = device.shell('dumpsys activity activities | grep topResumedActivity')
            if resumed and 'com.gathin.tvhome/' not in resumed:
                break
            time.sleep(0.5)
        assert resumed and 'com.gathin.tvhome/' not in resumed, 'Selecting an app did not switch away from the drawer'
        print(f'PASS: default switch mode, separate close controls, selection, Back cancels selection, and opening {label}.')
        print(resumed.strip())
        device.shell('input keyevent KEYCODE_HOME')
        device.shell('rm -f /data/local/tmp/recent-switcher-test.png /data/local/tmp/recent-switcher-test.xml')
    finally:
        device.close()


if __name__ == '__main__':
    main()

"""Exercise the actual single-field employer input without submitting a code to any server."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path('launch-results/auth')
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True)


def ui():
    adb('shell', 'uiautomator', 'dump', '/sdcard/auth-ui.xml')
    xml = adb('shell', 'cat', '/sdcard/auth-ui.xml')
    (OUT / 'latest-ui.xml').write_text(xml)
    return ET.fromstring(xml)


def find(tree, text):
    return next((n for n in tree.iter('node') if n.get('text') == text or n.get('content-desc') == text), None)


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1)


def enabled(tree, node):
    parents = {child: parent for parent in tree.iter() for child in parent}
    while node is not None:
        if node.get('enabled') == 'false':
            return False
        node = parents.get(node)
    return True


def screenshot(name):
    adb('shell', 'screencap', '-p', '/sdcard/auth-screen.png')
    adb('pull', '/sdcard/auth-screen.png', str(OUT / name))


tree = ui()
if find(tree, 'کد کارفرما را وارد کنید') is None:
    size = list(map(int, re.findall(r'\d+', adb('shell', 'wm', 'size'))))[-2:]
    w, h = size
    for _ in range(20):
        accept = find(tree, 'مطالعه کردم و می‌پذیرم')
        if accept is not None:
            tap(accept)
            break
        adb('shell', 'input', 'swipe', str(w // 2), str(h * 4 // 5), str(w // 2), str(h // 4), '150')
        tree = ui()
    else:
        raise AssertionError('Could not reach the first-run consent action')

time.sleep(2)
tree = ui()
assert find(tree, 'کد کارفرما را وارد کنید') is not None, 'Employer screen is missing'
editors = [n for n in tree.iter('node') if n.get('class') == 'android.widget.EditText']
assert len(editors) == 1, 'The six slots must remain one editable input'
screenshot('employer-light.png')
tap(editors[0])
adb('shell', 'input', 'text', '012345')
time.sleep(1)
adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
tree = ui()
editors = [n for n in tree.iter('node') if n.get('class') == 'android.widget.EditText']
assert any(n.get('text') == '012345' for n in editors), 'Leading zero or digit ordering was lost'
button = find(tree, 'بررسی کد و ادامه')
assert button is not None and enabled(tree, button), 'Six digits should enable Continue'
screenshot('employer-filled.png')
# The editor retains focus after hiding the IME. A second Back could leave
# the activity if tapping an already-focused editor did not reopen the IME.
adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
tree = ui()
editors = [n for n in tree.iter('node') if n.get('class') == 'android.widget.EditText']
assert any(n.get('text') == '01234' for n in editors), 'Backspace must remove the final digit'
button = find(tree, 'بررسی کد و ادامه')
assert button is not None and not enabled(tree, button), 'Incomplete code must disable Continue'
screenshot('employer-incomplete.png')
adb('shell', 'cmd', 'uimode', 'night', 'yes')
time.sleep(2)
screenshot('employer-dark.png')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
time.sleep(2)
assert find(ui(), 'کد کارفرما را وارد کنید') is not None
screenshot('employer-large-text.png')
print('Auth UI passed: single editor, leading zero, six-digit validation, deletion, dark mode and large text.')

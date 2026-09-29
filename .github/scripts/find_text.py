"""Prints the centre "x y" of the first UI element whose text (or content description, for icon
buttons) equals one of the given strings.

Usage: find_text.py <uiautomator dump xml> <text> [<text> ...]
Prints nothing when no element matches or the dump is missing/invalid.
"""
import sys
import xml.etree.ElementTree as ET

path, *texts = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
except (OSError, ET.ParseError):
    sys.exit(0)

for node in root.iter("node"):
    if node.get("text") in texts or node.get("content-desc") in texts:
        left_top, right_bottom = node.get("bounds", "[0,0][0,0]").strip("[]").split("][")
        x1, y1 = map(int, left_top.split(","))
        x2, y2 = map(int, right_bottom.split(","))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break

"""Select diagnostic controls without assuming a device's screen dimensions."""
import re
import sys
import xml.etree.ElementTree as ET

nodes = list(ET.parse(sys.argv[1]).iter("node"))
system_dialog = any(node.get("text") == "System UI isn't responding" for node in nodes)
target = "Wait" if system_dialog else "Run check"
for node in nodes:
    if node.get("text") == target:
        left, top, right, bottom = map(int, re.findall(r"\d+", node.get("bounds", "")))
        print("system-wait" if system_dialog else "run", (left + right) // 2, (top + bottom) // 2)
        break
else:
    print("missing")

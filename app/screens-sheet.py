#!/usr/bin/env python3
"""
One image per screen, every configuration side by side.

Reads what the screens/ tests rendered into app/build/screens/<config>/ and writes
app/build/screens/sheets/<pass>-<screen>.png: the screen as it opens along the
top, scrolled to the end along the bottom, phone to tablet, light to dark — so a
screen is reviewed in one look rather than ten.

Usage: python3 app/screens-sheet.py [name-filter]
"""
import os
import sys
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "build", "screens")
CONFIGS = ["phone", "phone-dark", "phone-big", "phone-land", "tablet", "tablet-port-dark"]
HEIGHT = 560          # every panel scaled to this height
GAP = 16
LABEL = 22


def load(config, name):
    path = os.path.join(ROOT, config, name + ".png")
    if not os.path.exists(path):
        return None
    img = Image.open(path).convert("RGB")
    scale = HEIGHT / img.height
    return img.resize((max(1, int(img.width * scale)), HEIGHT))


def sheet(name):
    rows = []
    for suffix in ("", ".end"):
        panels = [(c, load(c, name + suffix)) for c in CONFIGS]
        panels = [(c, p) for c, p in panels if p is not None]
        if panels:
            rows.append(panels)
    if not rows:
        return None
    width = max(sum(p.width for _, p in row) + GAP * (len(row) + 1) for row in rows)
    height = len(rows) * (HEIGHT + LABEL + GAP) + GAP
    out = Image.new("RGB", (width, height), (120, 120, 120))
    draw = ImageDraw.Draw(out)
    y = GAP
    for row in rows:
        x = GAP
        for config, panel in row:
            draw.text((x, y), config, fill=(255, 255, 255))
            out.paste(panel, (x, y + LABEL))
            x += panel.width + GAP
        y += HEIGHT + LABEL + GAP
    return out


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    names = set()
    for config in CONFIGS:
        folder = os.path.join(ROOT, config)
        if os.path.isdir(folder):
            for f in os.listdir(folder):
                if f.endswith(".png") and not f.endswith(".end.png"):
                    names.add(f[:-4])
    os.makedirs(os.path.join(ROOT, "sheets"), exist_ok=True)
    made = 0
    for name in sorted(names):
        if wanted and wanted not in name:
            continue
        img = sheet(name)
        if img:
            img.save(os.path.join(ROOT, "sheets", name + ".png"))
            made += 1
    print(f"{made} sheets in {os.path.join(ROOT, 'sheets')}")


if __name__ == "__main__":
    main()

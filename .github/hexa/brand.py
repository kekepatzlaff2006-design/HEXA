#!/usr/bin/env python3
# HEXA branding applied at build time (name, package, theme, icons)
import math
import pathlib
import re
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(".")
RES = ROOT / "app/src/main/res"
APP_ID = "com.hexa.emulator"
NAME = "HEXA"

BG = (0x0B, 0x0D, 0x10, 255)
CYAN = (0x00, 0xE5, 0xFF, 255)
PURPLE = (0x7C, 0x4D, 0xFF, 255)

def sub(path, pattern, repl, count=0, required=True):
 p = ROOT / path
 text = p.read_bytes().decode("utf-8")
 new, n = re.subn(pattern, repl, text, count=count, flags=re.S)
 if required and n == 0:
  sys.exit(f"brand.py: pattern not found in {path}: {pattern}")
 p.write_bytes(new.encode("utf-8"))
 print(f"  {path}: {n} change(s)")

def texts():
 print("Name and package")
 for f in RES.glob("values*/strings.xml"):
  sub(f.relative_to(ROOT), r'(<string name="app_name">)"?[^<"]*"?(</string>)', rf"\g<1>{NAME}\g<2>", required=False)
 sub("app/src/main/AndroidManifest.xml", r'android:label="Winlator Ludashi"', f'android:label="{NAME}"', required=False)
 sub("app/build.gradle", r'applicationId "[^"]+"', f'applicationId "{APP_ID}"')
 sub("app/src/main/java/com/winlator/cmod/core/WineUtils.java",
  r'"/com\.winlator\.cmod/storage"', f'"/{APP_ID}/storage"', required=False)
 sub("app/src/main/res/values/ic_launcher_background.xml", r"#[0-9A-Fa-f]{6}", "#0B0D10")

def theme():
 print("Theme (default dark = HEXA)")
 block = """private val BlackColors = darkColorScheme(
    primary = Color(0xFF00E5FF), onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF00363F), onPrimaryContainer = Color(0xFFB8F6FF),
    secondary = Color(0xFF7C4DFF), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2A1F4D), onSecondaryContainer = Color(0xFFE4DAFF),
    background = Color(0xFF0B0D10), onBackground = Color(0xFFE8EEF2),
    surface = Color(0xFF12151A), onSurface = Color(0xFFE8EEF2),
    surfaceVariant = Color(0xFF1A1E25), onSurfaceVariant = Color(0xFF9AA4B2),
    outline = Color(0xFF1F7C88), outlineVariant = Color(0xFF232A33),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)"""
 sub("app/src/main/java/com/winlator/cmod/ui/theme/WinZTheme.kt",
  r"private val BlackColors = darkColorScheme\(.*?\n\)", block.replace("\\", "\\\\"), count=1)

def hexagon(cx, cy, r):
 return [(cx + r * math.cos(math.radians(60 * i - 90)), cy + r * math.sin(math.radians(60 * i - 90))) for i in range(6)]

def logo(size, mono=False, background=False, round_mask=False, scale=1.0):
 s = size * 4
 img = Image.new("RGBA", (s, s), BG if background else (0, 0, 0, 0))
 d = ImageDraw.Draw(img)
 c = s / 2
 r = s * 0.5 * scale
 cyan = (255, 255, 255, 255) if mono else CYAN
 purple = (255, 255, 255, 255) if mono else PURPLE
 w = max(4, int(r * 0.085))
 d.polygon(hexagon(c, c, r), outline=cyan, width=w)
 lw = max(3, int(r * 0.055))
 dot = r * 0.075
 traces = [
  [(c - r * 0.45, c - r * 0.30), (c - r * 0.10, c - r * 0.30), (c + r * 0.05, c - r * 0.12), (c + r * 0.40, c - r * 0.12)],
  [(c - r * 0.45, c + r * 0.05), (c + r * 0.10, c + r * 0.05)],
  [(c - r * 0.25, c + r * 0.38), (c + r * 0.05, c + r * 0.38), (c + r * 0.22, c + r * 0.22), (c + r * 0.42, c + r * 0.22)],
 ]
 for t in traces:
  d.line(t, fill=cyan, width=lw, joint="curve")
  for (x, y) in (t[0], t[-1]):
   d.ellipse([x - dot, y - dot, x + dot, y + dot], fill=cyan)
 x, y = c + r * 0.10, c + r * 0.05
 d.ellipse([x - dot * 1.5, y - dot * 1.5, x + dot * 1.5, y + dot * 1.5], fill=purple)
 img = img.resize((size, size), Image.LANCZOS)
 if round_mask:
  m = Image.new("L", (s, s), 0)
  ImageDraw.Draw(m).ellipse([0, 0, s - 1, s - 1], fill=255)
  img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (size, size), 0), m.resize((size, size), Image.LANCZOS)))
 return img

def icons():
 print("Icons")
 dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
 for name, k in dens.items():
  folder = RES / f"mipmap-{name}"
  if not folder.is_dir():
   continue
  legacy, fg = round(48 * k), round(108 * k)
  logo(legacy, background=True, scale=0.62).save(folder / "ic_launcher.png")
  logo(legacy, background=True, round_mask=True, scale=0.58).save(folder / "ic_launcher_round.png")
  logo(fg, scale=0.40).save(folder / "ic_launcher_foreground.png")
  logo(fg, mono=True, scale=0.40).save(folder / "ic_launcher_monochrome.png")
  Image.new("RGBA", (legacy, legacy), BG).save(folder / "ic_launcher_background.png")
  print(f"  mipmap-{name}")
 full = RES / "drawable-nodpi/winlator_launcher_full.png"
 if full.exists():
  logo(512, scale=0.92).save(full)
  print("  drawable-nodpi/winlator_launcher_full.png")

if __name__ == "__main__":
 texts()
 theme()
 icons()
 print("HEXA branding applied")

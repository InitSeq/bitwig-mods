"""Draws the panel art ("Nebula": dark glass, colour light, glass cards): panel_art.svg (gradients and filters) -> panel.png (2x, rendered by macOS's
Quick Look) -> panel.svg, a plain SVG holding the PNG, which Bitwig draws under the controls.
python3 make_panel.py   (reads the layout from build_hybrid_reverb.py)"""
import base64
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
os.environ.setdefault("KILN_VENDOR", str(HERE / "vendor"))
import build_hybrid_reverb as b  # noqa: E402

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
U = 4
W, H = b.PANEL[0] * U, b.PANEL[1] * U
DX, DY, DW, DH = (v * U for v in b.DISPLAY)
TITLES = {"algorithm": "Algorithm", "space": "Space", "output": "Output", "tone": "Tone"}


def knob_rings():
    """A faint glass ring under every knob."""
    out = []
    for p in b.hybrid_reverb(display=False).params:
        if hasattr(p, "knob"):
            x, y, w, h = p.knob
            cx, cy = (x + w / 2) * U, (y + h / 2) * U
            out.append(f'<circle cx="{cx}" cy="{cy}" r="{w * U * 0.57:.1f}" fill="#ffffff" fill-opacity=".035" '
                       f'stroke="#ffffff" stroke-opacity=".10" stroke-width=".8"/>')
    return "\n".join(out)


ACCENT = {"algorithm": "url(#cyanline)", "space": "url(#magline)", "output": "url(#mixline)", "tone": "url(#mixline)"}


def cards():
    out = []
    for key, (sx, sw) in b.SECTIONS.items():
        x, w = sx * U + 1, sw * U - 2
        out.append(f'''<g>
<rect x="{x}" y="4" width="{w}" height="{H - 8}" rx="11" fill="#ffffff" fill-opacity=".045" stroke="#ffffff" stroke-opacity=".13" stroke-width=".9"/>
<rect x="{x + .5}" y="4.5" width="{w - 1}" height="40" rx="10.5" fill="url(#sheen)"/>
<rect x="{x + 8}" y="20.5" width="{w - 16}" height="1.1" fill="{ACCENT[key]}"/>
<text x="{x + 8}" y="15.8" font-family="Avenir Next, Helvetica Neue, Helvetica, Arial" font-size="8.2" font-weight="600" letter-spacing="1.6" fill="#e9e6ff" fill-opacity=".92">{TITLES[key]}</text>
</g>''')
    return "\n".join(out)


def art() -> str:
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W * 2}" height="{H * 2}" viewBox="0 0 {W} {H}">
<defs>
<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0d1022"/><stop offset=".5" stop-color="#120f26"/><stop offset="1" stop-color="#0a0c18"/></linearGradient>
<filter id="blur" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="38"/></filter>
<filter id="grain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="3"/><feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .05 0"/></filter>
<linearGradient id="sheen" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff" stop-opacity=".07"/><stop offset="1" stop-color="#ffffff" stop-opacity="0"/></linearGradient>
<linearGradient id="cyanline" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#3fe8ff"/><stop offset="1" stop-color="#3fe8ff" stop-opacity="0"/></linearGradient>
<linearGradient id="magline" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#ff4fb8"/><stop offset="1" stop-color="#ff4fb8" stop-opacity="0"/></linearGradient>
<linearGradient id="mixline" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#9d7bff"/><stop offset="1" stop-color="#9d7bff" stop-opacity="0"/></linearGradient>
<linearGradient id="logo" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#3fe8ff"/><stop offset=".55" stop-color="#9d7bff"/><stop offset="1" stop-color="#ff4fb8"/></linearGradient>
<linearGradient id="frame" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#3fe8ff" stop-opacity=".55"/><stop offset=".5" stop-color="#9d7bff" stop-opacity=".25"/><stop offset="1" stop-color="#ff4fb8" stop-opacity=".55"/></linearGradient>
<clipPath id="round"><rect width="{W}" height="{H}" rx="10"/></clipPath>
</defs>
<g clip-path="url(#round)">
<rect width="{W}" height="{H}" fill="url(#bg)"/>
<g filter="url(#blur)" opacity=".85">
<circle cx="{DX + DW * .2}" cy="{H * .9}" r="90" fill="#2a7bff" fill-opacity=".55"/>
<circle cx="{DX + DW * .9}" cy="{H * .05}" r="80" fill="#ff3fa6" fill-opacity=".35"/>
<circle cx="{W * .48}" cy="{H * 1.0}" r="110" fill="#6c3cff" fill-opacity=".40"/>
<circle cx="{W * .82}" cy="{H * .0}" r="100" fill="#1fd6ff" fill-opacity=".22"/>
<circle cx="{W * .97}" cy="{H * .95}" r="70" fill="#ff4fb8" fill-opacity=".28"/>
</g>
<rect width="{W}" height="{H}" filter="url(#grain)"/>
</g>
<rect x=".5" y=".5" width="{W - 1}" height="{H - 1}" rx="10" fill="none" stroke="#ffffff" stroke-opacity=".12"/>
<rect x="{DX - 3}" y="{DY - 3}" width="{DW + 6}" height="{DH + 6}" rx="12" fill="#04050c" stroke="url(#frame)" stroke-width="1.4"/>
<text x="{DX + DW / 2}" y="{H - 8}" text-anchor="middle" font-family="Avenir Next, Helvetica Neue, Helvetica, Arial" font-size="11" font-weight="300" letter-spacing="6" fill="url(#logo)">HYBRID REVERB</text>
{cards()}
{knob_rings()}
</svg>'''


def main():
    (HERE / "panel_art.svg").write_text(art())
    # Chrome renders the SVG with its filters, at twice the size (the SVG's own width and height)
    tmp = Path(tempfile.mkdtemp())
    png = tmp / "panel.png"
    page = tmp / "panel.html"
    page.write_text('<html><body style="margin:0;background:#000">' + (HERE / "panel_art.svg").read_text() + "</body></html>")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    f"--window-size={W * 2},{H * 2}", "--virtual-time-budget=2000", f"--user-data-dir={tmp / 'chrome'}",
                    f"--screenshot={png}", page.as_uri()], check=True, capture_output=True)
    shutil.copy(png, HERE / "panel.png")
    data = base64.b64encode(png.read_bytes()).decode()
    (HERE / "panel.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}"><image width="{W}" height="{H}" xlink:href="data:image/png;base64,{data}"/></svg>')
    print("PANEL_OK", png.stat().st_size, "bytes PNG")


if __name__ == "__main__":
    main()

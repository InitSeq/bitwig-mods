"""Draws the panel art: panel_art.svg (gradients and filters) -> panel.png (2x, rendered by macOS's
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
TITLES = {"algorithm": "ALGORITHM", "space": "SPACE", "output": "OUTPUT", "tone": "TONE"}


def knob_wells():
    """A recessed ring under every knob of the device."""
    out = []
    for p in b.hybrid_reverb(display=False).params:
        if hasattr(p, "knob"):
            x, y, w, h = p.knob
            cx, cy = (x + w / 2) * U, (y + h / 2) * U
            out.append(f'<circle cx="{cx}" cy="{cy}" r="{w * U * 0.56:.1f}" fill="url(#well)"/>'
                       f'<circle cx="{cx}" cy="{cy}" r="{w * U * 0.56:.1f}" fill="none" stroke="#ffffff" stroke-opacity=".07" stroke-width="1"/>')
    return "\n".join(out)


def sections():
    out = []
    for key, (sx, sw) in b.SECTIONS.items():
        x, w = sx * U, sw * U
        out.append(f'''<g>
<rect x="{x + 1}" y="5" width="{w - 2}" height="{H - 10}" rx="6" fill="url(#plate)" stroke="#000" stroke-opacity=".55"/>
<rect x="{x + 1.5}" y="5.5" width="{w - 3}" height="{H - 11}" rx="5.5" fill="none" stroke="#fff" stroke-opacity=".06"/>
<rect x="{x + 1}" y="5" width="{w - 2}" height="19" rx="6" fill="#000" fill-opacity=".28"/>
<rect x="{x + 7}" y="22" width="22" height="1.6" fill="#ff8e3c"/>
<text x="{x + 7}" y="17.5" font-family="Helvetica Neue, Helvetica, Arial" font-size="9" font-weight="bold" letter-spacing="2.2" fill="#000" fill-opacity=".6">{TITLES[key]}</text>
<text x="{x + 7}" y="16.8" font-family="Helvetica Neue, Helvetica, Arial" font-size="9" font-weight="bold" letter-spacing="2.2" fill="#d9d2ca">{TITLES[key]}</text>
</g>''')
    return "\n".join(out)


def screw(cx, cy, r=3.2):
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#screw)" stroke="#111" stroke-width=".6"/>'
            f'<line x1="{cx - r * .7}" y1="{cy + r * .2}" x2="{cx + r * .7}" y2="{cy - r * .2}" stroke="#1a1715" stroke-width="1"/>')


def art() -> str:
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W * 2}" height="{H * 2}" viewBox="0 0 {W} {H}">
<defs>
<linearGradient id="steel" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4a4642"/><stop offset=".5" stop-color="#36322f"/><stop offset="1" stop-color="#24211f"/></linearGradient>
<filter id="brush" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.004 0.9" numOctaves="3" seed="7"/><feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .09 0"/></filter>
<linearGradient id="plate" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3b3733"/><stop offset="1" stop-color="#2a2725"/></linearGradient>
<radialGradient id="well" cx=".5" cy=".42" r=".6"><stop offset="0" stop-color="#141211"/><stop offset=".8" stop-color="#1e1b19"/><stop offset="1" stop-color="#2e2a27"/></radialGradient>
<radialGradient id="screw" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#bdb5ad"/><stop offset=".6" stop-color="#6d655e"/><stop offset="1" stop-color="#3b3531"/></radialGradient>
<linearGradient id="bezel" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5d5650"/><stop offset=".08" stop-color="#3d3833"/><stop offset=".92" stop-color="#2a2623"/><stop offset="1" stop-color="#1a1816"/></linearGradient>
<linearGradient id="edge" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#ff6a12" stop-opacity="0"/><stop offset=".15" stop-color="#ff8e3c"/><stop offset=".6" stop-color="#ffb35a"/><stop offset="1" stop-color="#ff6a12" stop-opacity="0"/></linearGradient>
<radialGradient id="heat" cx=".17" cy="1.05" r=".55"><stop offset="0" stop-color="#ff5a10" stop-opacity=".35"/><stop offset="1" stop-color="#ff5a10" stop-opacity="0"/></radialGradient>
<linearGradient id="logo" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffd08a"/><stop offset="1" stop-color="#ff8e3c"/></linearGradient>
<filter id="glow" x="-20%" y="-50%" width="140%" height="200%"><feGaussianBlur stdDeviation="2.2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
</defs>
<rect width="{W}" height="{H}" rx="8" fill="url(#steel)"/>
<rect width="{W}" height="{H}" rx="8" filter="url(#brush)"/>
<rect width="{W}" height="{H}" rx="8" fill="url(#heat)"/>
<rect x="0" y="0" width="{W}" height="2.2" fill="url(#edge)"/>
<rect x=".5" y=".5" width="{W - 1}" height="{H - 1}" rx="8" fill="none" stroke="#fff" stroke-opacity=".08"/>
<rect x="{DX - 7}" y="{DY - 7}" width="{DW + 14}" height="{H - DY - 0}" rx="9" fill="url(#bezel)" stroke="#000" stroke-opacity=".7"/>
<rect x="{DX - 2}" y="{DY - 2}" width="{DW + 4}" height="{DH + 4}" rx="5" fill="#050404" stroke="#000"/>
<rect x="{DX - 2.5}" y="{DY + DH + 2.5}" width="{DW + 5}" height="1" fill="#fff" fill-opacity=".07"/>
{screw(DX - 2, H - 9)}{screw(DX + DW + 2, H - 9)}
<text x="{DX + 10}" y="{H - 6.5}" font-family="Helvetica Neue, Helvetica, Arial" font-size="12.5" font-weight="bold" letter-spacing="4.5" fill="url(#logo)" filter="url(#glow)">HYBRID REVERB</text>
<text x="{DX + DW - 10}" y="{H - 7}" text-anchor="end" font-family="Helvetica Neue, Helvetica, Arial" font-size="6.5" letter-spacing="2.4" fill="#9c9188">COLDFORGE</text>
{sections()}
{knob_wells()}
{screw(W - 6, 6, 2.6)}{screw(W - 6, H - 6, 2.6)}
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

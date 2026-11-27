"""Draws the panel art: a 1970s studio unit. Dark enamel front plate with cream screen print, a scale
round every knob, framed groups, and a meter bridge with two VU meter faces whose needles the display
shader draws on top. panel_art.svg (gradients and filters) -> panel.png (2x, rendered by headless
Chrome) -> panel.svg, a plain SVG holding the PNG, which Bitwig draws under the controls.
python3 make_panel.py   (reads the layout from build_hybrid_reverb.py; meter geometry is shared with
furnace.sksl through the METER constants below, in display-relative units)"""
import base64
import math
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
CREAM = "#eadfc4"
INK = "#2a2420"

# VU meters, in units of the display's height from its top left: face rectangles, pivot below each face
# (the same numbers are in furnace.sksl)
METERS = [dict(x=.03, w=.84), dict(x=.892, w=.84)]   # x and width in display heights
FACE_Y, FACE_H = .06, .62
PIVOT_DY = .15          # pivot below the face's bottom edge
R_SCALE = .5            # radius of the scale arc
SWING = 48.0            # degrees either side of vertical, -20 VU .. +3 VU
LAMPS = [(-.12, "FREEZE"), (.12, "SHIMMER")]   # x from the display's middle, in display heights; y .87


def vu_pos(db):
    """0..1 along the scale for a VU reading (scales are linear in voltage)."""
    lo, hi = 10 ** (-20 / 20), 10 ** (3 / 20)
    return (10 ** (db / 20) - lo) / (hi - lo)


def meter_face(m, label):
    s = DH                                   # display height in px is the unit
    fx, fy, fw, fh = DX + m["x"] * s, DY + FACE_Y * s, m["w"] * s, FACE_H * s
    px, py = fx + fw / 2, fy + fh + PIVOT_DY * s
    r_scale = R_SCALE * s
    out = [f'<rect x="{fx}" y="{fy}" width="{fw}" height="{fh}" rx="4" fill="url(#face)" stroke="#0b0908" stroke-width="1.4"/>']

    def pt(t, r):
        a = math.radians(-SWING + 2 * SWING * t)
        return px + r * math.sin(a), py - r * math.cos(a)
    # the scale arc, black to 0 VU, red above
    def arc(t0, t1, r, colr, wdt):
        (x0, y0), (x1, y1) = pt(t0, r), pt(t1, r)
        return f'<path d="M{x0:.2f} {y0:.2f} A{r:.2f} {r:.2f} 0 0 1 {x1:.2f} {y1:.2f}" fill="none" stroke="{colr}" stroke-width="{wdt}"/>'
    out.append(arc(0, vu_pos(0), r_scale, INK, .9))
    out.append(arc(vu_pos(0), 1, r_scale, "#b3261e", 2.6))
    for db in [-20, -10, -7, -5, -3, -2, -1, 0, 1, 2, 3]:
        t = vu_pos(db)
        (x0, y0), (x1, y1) = pt(t, r_scale), pt(t, r_scale + (5 if db in (-20, -10, -5, 0, 3) else 3))
        colr = "#b3261e" if db > 0 else INK
        out.append(f'<line x1="{x0:.2f}" y1="{y0:.2f}" x2="{x1:.2f}" y2="{y1:.2f}" stroke="{colr}" stroke-width=".9"/>')
        if db in (-10, -5, -3, 0, 3):
            tx, ty = pt(t, r_scale + 10)
            out.append(f'<text x="{tx:.2f}" y="{ty + 2:.2f}" text-anchor="middle" font-family="Futura, Avenir Next, Helvetica" '
                       f'font-size="6" fill="{"#b3261e" if db > 0 else INK}">{"+" if db > 0 else ""}{db}</text>')
    # percentage scale inside
    for k in range(0, 11):
        (x0, y0), (x1, y1) = pt(k / 10, r_scale - 4), pt(k / 10, r_scale - 6.5)
        out.append(f'<line x1="{x0:.2f}" y1="{y0:.2f}" x2="{x1:.2f}" y2="{y1:.2f}" stroke="{INK}" stroke-width=".5"/>')
    vx, vy = pt(.5, r_scale * .55)
    out.append(f'<text x="{vx:.2f}" y="{vy:.2f}" text-anchor="middle" font-family="Futura, Avenir Next, Helvetica" font-size="11" font-weight="bold" fill="{INK}">VU</text>')
    out.append(f'<text x="{fx + 6}" y="{fy + fh - 5}" font-family="Futura, Avenir Next, Helvetica" font-size="5.5" letter-spacing="1.6" fill="{INK}" fill-opacity=".75">{label}</text>')
    # glass sheen
    out.append(f'<rect x="{fx + 1}" y="{fy + 1}" width="{fw - 2}" height="{fh * .45}" rx="3" fill="url(#glass)"/>')
    return "\n".join(out)


def knob_scales():
    """Screen-printed scales: 11 ticks over the knob's travel, longer at the ends and the middle."""
    out = []
    for p in b.hybrid_reverb(display=False).params:
        if not hasattr(p, "knob"):
            continue
        x, y, w, h = p.knob
        cx, cy = (x + w / 2) * U, (y + h / 2) * U - 1
        r0 = w * U * .58
        for k in range(11):
            a = math.radians(-135 + 27 * k)
            ln = 4 if k in (0, 5, 10) else 2.5
            x0, y0 = cx + r0 * math.sin(a), cy - r0 * math.cos(a)
            x1, y1 = cx + (r0 + ln) * math.sin(a), cy - (r0 + ln) * math.cos(a)
            out.append(f'<line x1="{x0:.2f}" y1="{y0:.2f}" x2="{x1:.2f}" y2="{y1:.2f}" stroke="{CREAM}" stroke-opacity=".8" stroke-width=".8"/>')
    return "\n".join(out)


def groups():
    """Cream lines round each group, the title set into the top line."""
    out = []
    for key, (sx, sw) in b.SECTIONS.items():
        x, w = sx * U + 2, sw * U - 4
        y0, y1 = 12, H - 6
        title = TITLES[key]
        tw = len(title) * 6.3 + 8
        out.append(f'<path d="M{x + 10} {y0} L{x + 4} {y0} Q{x} {y0} {x} {y0 + 4} L{x} {y1 - 4} Q{x} {y1} {x + 4} {y1} '
                   f'L{x + w - 4} {y1} Q{x + w} {y1} {x + w} {y1 - 4} L{x + w} {y0 + 4} Q{x + w} {y0} {x + w - 4} {y0} '
                   f'L{x + 10 + tw} {y0}" fill="none" stroke="{CREAM}" stroke-opacity=".55" stroke-width=".8"/>')
        out.append(f'<text x="{x + 14}" y="{y0 + 2.6}" font-family="Futura, Avenir Next, Helvetica" font-size="7.2" '
                   f'letter-spacing="1.8" fill="{CREAM}">{title}</text>')
    return "\n".join(out)


def screw(cx, cy, r=3.4):
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#screw)" stroke="#0d0b0a" stroke-width=".6"/>'
            f'<line x1="{cx - r * .75}" y1="{cy}" x2="{cx + r * .75}" y2="{cy}" stroke="#2a2522" stroke-width="1.1"/>')


def art() -> str:
    lamps = []
    for dxl, name in LAMPS:
        lx, ly = DX + DW / 2 + dxl * DH, DY + .87 * DH
        lamps.append(f'<circle cx="{lx}" cy="{ly}" r="6.5" fill="url(#trim)" stroke="#0b0908"/>'
                     f'<circle cx="{lx}" cy="{ly}" r="4.6" fill="url(#jewel)"/>')
        tx = lx - 11 if dxl < 0 else lx + 11
        lamps.append(f'<text x="{tx}" y="{ly + 2.2}" text-anchor="{"end" if dxl < 0 else "start"}" '
                     f'font-family="Futura, Avenir Next, Helvetica" font-size="6" letter-spacing="1.4" fill="{CREAM}">{name}</text>')
    lamps = "\n".join(lamps)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W * 2}" height="{H * 2}" viewBox="0 0 {W} {H}">
<defs>
<linearGradient id="enamel" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a3632"/><stop offset="1" stop-color="#25221f"/></linearGradient>
<filter id="hammer" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".055" numOctaves="3" seed="11"/><feColorMatrix values="0 0 0 0 1  0 0 0 0 .95  0 0 0 0 .85  0 0 0 .07 0"/></filter>
<filter id="fine" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="1.2" numOctaves="1" seed="4"/><feColorMatrix values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 .12 0"/></filter>
<linearGradient id="bridge" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4a4540"/><stop offset="1" stop-color="#1c1917"/></linearGradient>
<linearGradient id="face" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f6ecd2"/><stop offset="1" stop-color="#e2cf9f"/></linearGradient>
<linearGradient id="glass" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff" stop-opacity=".35"/><stop offset="1" stop-color="#ffffff" stop-opacity="0"/></linearGradient>
<radialGradient id="screw" cx=".35" cy=".3" r=".85"><stop offset="0" stop-color="#d9d2c8"/><stop offset=".6" stop-color="#8a8178"/><stop offset="1" stop-color="#4a433d"/></radialGradient>
<radialGradient id="jewel" cx=".4" cy=".35" r=".7"><stop offset="0" stop-color="#7a2a22"/><stop offset="1" stop-color="#2a0c09"/></radialGradient>
<linearGradient id="trim" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#cfc8be"/><stop offset=".5" stop-color="#8d867d"/><stop offset="1" stop-color="#5c5650"/></linearGradient>
</defs>
<rect width="{W}" height="{H}" rx="6" fill="url(#enamel)"/>
<rect width="{W}" height="{H}" rx="6" filter="url(#hammer)"/>
<rect width="{W}" height="{H}" rx="6" filter="url(#fine)"/>
<rect x="0" y="0" width="{W}" height="2.4" fill="url(#trim)"/>
<rect x="0" y="{H - 2.4}" width="{W}" height="2.4" fill="url(#trim)"/>
<rect x="{DX - 6}" y="{DY - 6}" width="{DW + 12}" height="{DH + 12}" rx="6" fill="url(#bridge)" stroke="#0b0908"/>
<rect x="{DX - 2}" y="{DY - 2}" width="{DW + 4}" height="{DH + 4}" rx="4" fill="#15120f" stroke="#000"/>
{meter_face(METERS[0], "INPUT")}
{meter_face(METERS[1], "REVERB")}
{lamps}
<text x="{DX}" y="{H - 5.5}" font-family="Futura, Avenir Next, Helvetica" font-size="9" font-weight="bold" letter-spacing="3.2" fill="{CREAM}">HYBRID REVERB</text>
<text x="{DX + DW}" y="{H - 5.5}" text-anchor="end" font-family="Futura, Avenir Next, Helvetica" font-size="6" letter-spacing="1.8" fill="{CREAM}" fill-opacity=".6">MODEL HR-2 · STEREO</text>
{groups()}
{knob_scales()}
{screw(5, 6)}{screw(W - 5, 6)}{screw(5, H - 6)}{screw(W - 5, H - 6)}
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

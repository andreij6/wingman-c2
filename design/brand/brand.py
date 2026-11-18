import math
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

BG = "#0A0E13"
GLOW = "#16243A"
PRIMARY = "#8AB4FF"
PRIMARY_HI = "#C9DBFF"
FRIEND = "#4FC3F7"
TEXT = "#E7ECF3"
MUTED = "#6B7889"

C = 54.0
RING_R = 29.0
RING_W = 2.2
GAP_DEG = 14.0
TICK_IN, TICK_OUT = 23.5, 32.0
TICK_W = 2.2
WINGMAN_OFFSET = (11.0, 11.0)


def f(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def chevron(apex_x, apex_y, span, depth, notch):
    h = span / 2
    return (f"M{f(apex_x)},{f(apex_y)}L{f(apex_x + h)},{f(apex_y + depth)}"
            f"L{f(apex_x)},{f(apex_y + notch)}L{f(apex_x - h)},{f(apex_y + depth)}Z")


LEAD_APEX, LEAD_SPAN, LEAD_DEPTH = (47.0, 35.0), 25.0, 23.0
WING_APEX, WING_SPAN, WING_DEPTH = (66.0, 55.0), 15.0, 14.0
LEAD = chevron(*LEAD_APEX, LEAD_SPAN, LEAD_DEPTH, LEAD_DEPTH * 0.76)
WINGMAN = chevron(*WING_APEX, WING_SPAN, WING_DEPTH, WING_DEPTH * 0.76)


def span_y(apex, depth):
    return f(apex[0]), f(apex[1]), f(apex[1] + depth)


def arc_path():
    parts = []
    for k in range(4):
        a0 = math.radians(-90 + k * 90 + GAP_DEG / 2)
        a1 = math.radians(-90 + (k + 1) * 90 - GAP_DEG / 2)
        x0, y0 = C + RING_R * math.cos(a0), C + RING_R * math.sin(a0)
        x1, y1 = C + RING_R * math.cos(a1), C + RING_R * math.sin(a1)
        parts.append(f"M{f(x0)},{f(y0)}A{f(RING_R)},{f(RING_R)} 0 0,1 {f(x1)},{f(y1)}")
    return "".join(parts)


def tick_path():
    return (f"M{f(C)},{f(C - TICK_OUT)}L{f(C)},{f(C - TICK_IN)}"
            f"M{f(C + TICK_IN)},{f(C)}L{f(C + TICK_OUT)},{f(C)}"
            f"M{f(C)},{f(C + TICK_IN)}L{f(C)},{f(C + TICK_OUT)}"
            f"M{f(C - TICK_OUT)},{f(C)}L{f(C - TICK_IN)},{f(C)}")


RING = arc_path()
TICKS = tick_path()


def svg_mark(mono=False, glow=True):
    if mono:
        return (f'<g fill="none" stroke="#fff" stroke-linecap="round">'
                f'<path d="{RING}" stroke-width="{RING_W}"/><path d="{TICKS}" stroke-width="{TICK_W}"/></g>'
                f'<path d="{LEAD}" fill="#fff"/><path d="{WINGMAN}" fill="#fff"/>')
    g = (f'<radialGradient id="glow" cx="{f(C)}" cy="{f(C)}" r="40" gradientUnits="userSpaceOnUse">'
         f'<stop offset="0" stop-color="{GLOW}"/><stop offset="1" stop-color="{GLOW}" stop-opacity="0"/></radialGradient>'
         '<linearGradient id="lead" x1="{0}" y1="{1}" x2="{0}" y2="{2}" gradientUnits="userSpaceOnUse">'.format(*span_y(LEAD_APEX, LEAD_DEPTH)) +
         f'<stop offset="0" stop-color="{PRIMARY_HI}"/><stop offset="1" stop-color="{PRIMARY}"/></linearGradient>'
         '<linearGradient id="wing" x1="{0}" y1="{1}" x2="{0}" y2="{2}" gradientUnits="userSpaceOnUse">'.format(*span_y(WING_APEX, WING_DEPTH)) +
         f'<stop offset="0" stop-color="{FRIEND}"/><stop offset="1" stop-color="#2E9BD6"/></linearGradient>')
    body = f'<circle cx="{f(C)}" cy="{f(C)}" r="40" fill="url(#glow)"/>' if glow else ""
    body += (f'<g fill="none" stroke-linecap="round">'
             f'<path d="{RING}" stroke="{PRIMARY}" stroke-opacity="0.55" stroke-width="{RING_W}"/>'
             f'<path d="{TICKS}" stroke="{PRIMARY}" stroke-width="{TICK_W}"/></g>'
             f'<path d="{LEAD}" fill="url(#lead)"/><path d="{WINGMAN}" fill="url(#wing)"/>')
    return f"<defs>{g}</defs>{body}"


def svg(content, view="0 0 108 108", size=1024, bg=None, shape=None):
    x, y, w, h = (float(v) for v in view.split())
    back = ""
    if bg and shape == "squircle":
        back = f'<rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" rx="{f(w * 0.225)}" fill="{bg}"/>'
    elif bg and shape == "circle":
        back = f'<circle cx="{f(x + w / 2)}" cy="{f(y + h / 2)}" r="{f(w / 2)}" fill="{bg}"/>'
    elif bg:
        back = f'<rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" fill="{bg}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view}">'
            f"{back}{content}</svg>\n")


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as out:
        out.write(text)


def shoot(html, out, w, h, scale=1):
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as tmp:
        tmp.write(f'<!doctype html><html><head><meta charset="utf-8"><style>html,body{{margin:0;background:transparent;overflow:hidden}}'
                  f"svg{{display:block}}</style></head><body>{html}</body></html>")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                    f"--force-device-scale-factor={scale}", f"--window-size={w},{h}",
                    "--default-background-color=00000000", f"--screenshot={out}", f"file://{tmp.name}"],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    os.unlink(tmp.name)


def resize(src, dst, px):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    subprocess.run(["sips", "-Z", str(px), src, "--out", dst], check=True, stdout=subprocess.DEVNULL)


def vd_paths(colored, glow, ring_trim=None, tick_alpha=None, wing_group=None):
    lead_fill = (f'<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="{{0}}" android:startY="{{1}}" '
                 f'android:endX="{{0}}" android:endY="{{2}}" android:startColor="{PRIMARY_HI}" android:endColor="{PRIMARY}"/></aapt:attr>').format(*span_y(LEAD_APEX, LEAD_DEPTH))
    wing_fill = (f'<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="{{0}}" android:startY="{{1}}" '
                 f'android:endX="{{0}}" android:endY="{{2}}" android:startColor="{FRIEND}" android:endColor="#2E9BD6"/></aapt:attr>').format(*span_y(WING_APEX, WING_DEPTH))
    out = []
    if glow:
        out.append(f'<path android:pathData="M{f(C - 40)},{f(C)}a40,40 0 1,0 80,0a40,40 0 1,0 -80,0">'
                   f'<aapt:attr name="android:fillColor"><gradient android:type="radial" android:centerX="{f(C)}" android:centerY="{f(C)}" '
                   f'android:gradientRadius="40" android:startColor="{GLOW}" android:endColor="#0016243A"/></aapt:attr></path>')
    ring_color = PRIMARY if colored else "#FFFFFFFF"
    ring_alpha = ' android:strokeAlpha="0.55"' if colored else ""
    trim = f' android:trimPathEnd="{ring_trim}"' if ring_trim is not None else ""
    name = ' android:name="ring"' if ring_trim is not None else ""
    out.append(f'<path{name} android:pathData="{RING}" android:strokeColor="{ring_color}"{ring_alpha} '
               f'android:strokeWidth="{f(RING_W)}" android:strokeLineCap="round"{trim}/>')
    talpha = f' android:strokeAlpha="{tick_alpha}"' if tick_alpha is not None else ""
    tname = ' android:name="ticks"' if tick_alpha is not None else ""
    out.append(f'<path{tname} android:pathData="{TICKS}" android:strokeColor="{ring_color}" '
               f'android:strokeWidth="{f(TICK_W)}" android:strokeLineCap="round"{talpha}/>')
    if colored:
        out.append(f'<path android:pathData="{LEAD}">{lead_fill}</path>')
        fade = ' android:name="wingman" android:fillAlpha="0"' if wing_group else ""
        wing = f'<path{fade} android:pathData="{WINGMAN}">{wing_fill}</path>'
    else:
        out.append(f'<path android:pathData="{LEAD}" android:fillColor="#FFFFFFFF"/>')
        wing = f'<path android:pathData="{WINGMAN}" android:fillColor="#FFFFFFFF"/>'
    if wing_group:
        wing = (f'<group android:name="formation" android:translateX="{f(wing_group[0])}" '
                f'android:translateY="{f(wing_group[1])}">{wing}</group>')
    out.append(wing)
    return "\n    ".join(out)


def vector(body, size_dp, aapt):
    ns = ' xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    return (f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="{size_dp}dp"\n    android:height="{size_dp}dp"\n'
            f'    android:viewportWidth="108"\n    android:viewportHeight="108">\n    {body}\n</vector>\n')


def animated_splash():
    inner = (f'<vector android:width="240dp" android:height="240dp" android:viewportWidth="108" android:viewportHeight="108">'
             f'{vd_paths(True, False, ring_trim="0", tick_alpha="0", wing_group=WINGMAN_OFFSET)}</vector>')

    def target(name, animators):
        return (f'    <target android:name="{name}">\n        <aapt:attr name="android:animation">\n'
                f'            <set>{animators}</set>\n        </aapt:attr>\n    </target>\n')

    def anim(prop, a, b, dur, delay=0, kind="floatType", interp="@android:interpolator/fast_out_slow_in"):
        return (f'<objectAnimator android:propertyName="{prop}" android:valueFrom="{a}" android:valueTo="{b}" '
                f'android:valueType="{kind}" android:duration="{dur}" android:startOffset="{delay}" '
                f'android:interpolator="{interp}"/>')

    decel = "@android:interpolator/decelerate_cubic"
    return ('<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt">\n'
            f'    <aapt:attr name="android:drawable">\n        {inner}\n    </aapt:attr>\n'
            + target("ring", anim("trimPathEnd", 0, 1, 650))
            + target("ticks", anim("strokeAlpha", 0, 1, 250, 450))
            + target("wingman", anim("fillAlpha", 0, 1, 300, 250))
            + target("formation", anim("translateX", f(WINGMAN_OFFSET[0]), 0, 600, 250, interp=decel)
                     + anim("translateY", f(WINGMAN_OFFSET[1]), 0, 600, 250, interp=decel))
            + "</animated-vector>\n")


def wordmark_html(mark_px, gap):
    return (f'<div style="width:412px;height:915px;background:{BG};display:flex;flex-direction:column;align-items:center;justify-content:center;'
            f'font-family:-apple-system,\'SF Pro Display\',Inter,Helvetica,sans-serif">'
            f'<div style="width:{mark_px}px;height:{mark_px}px;margin-top:-40px">{svg(svg_mark(), size=mark_px)}</div>'
            f'<div style="margin-top:{gap}px;color:{TEXT};font-size:22px;font-weight:600;letter-spacing:0.32em;padding-left:0.32em">WINGMAN C2</div>'
            f'<div style="margin-top:10px;color:{MUTED};font-family:Menlo,monospace;font-size:11px;letter-spacing:0.18em;padding-left:0.18em">'
            f"TACTICAL COMMAND &amp; CONTROL</div></div>")


def main():
    tmp = tempfile.mkdtemp()
    icon = svg(svg_mark(), view="6 6 96 96", bg=BG, shape="squircle")
    write(os.path.join(HERE, "icon.svg"), icon)
    write(os.path.join(HERE, "icon-foreground.svg"), svg(svg_mark()))
    write(os.path.join(HERE, "icon-monochrome.svg"), svg(svg_mark(mono=True)))

    master = os.path.join(tmp, "icon-1024.png")
    shoot(icon, master, 1024, 1024)
    resize(master, os.path.join(HERE, "icon-512.png"), 512)
    resize(master, os.path.join(HERE, "icon-192.png"), 192)

    legacy_master = os.path.join(tmp, "legacy-1024.png")
    round_master = os.path.join(tmp, "round-1024.png")
    shoot(svg(svg_mark(), view="12 12 84 84", bg=BG, shape="squircle"), legacy_master, 1024, 1024)
    shoot(svg(svg_mark(), view="12 12 84 84", bg=BG, shape="circle"), round_master, 1024, 1024)
    for density, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        resize(legacy_master, os.path.join(RES, f"mipmap-{density}", "ic_launcher.png"), px)
        resize(round_master, os.path.join(RES, f"mipmap-{density}", "ic_launcher_round.png"), px)

    shoot(wordmark_html(220, 28), os.path.join(HERE, "splash-preview.png"), 412, 915, scale=2)

    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), vector(vd_paths(True, True), 108, True))
    write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), vector(vd_paths(False, False), 108, False))
    write(os.path.join(RES, "drawable", "splash_mark.xml"), vector(vd_paths(True, False), 240, True))
    write(os.path.join(RES, "drawable-v31", "splash_mark.xml"), animated_splash())


if __name__ == "__main__" and not sys.flags.interactive:
    main()

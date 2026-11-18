# Generates design/wingman-c2.figcraft — Wingman C2 Android screens (412×915 dp) + a foundations sheet.
# Icons: Material Symbols SVGs, pre-converted to vector networks in icons/icons.json (icons.py).
# Basemap: images/terrain.png (Nano Banana, images.sh).
# Usage: python3 -I gen.py OUT.figcraft
import hashlib, json, math, os, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = json.load(open(os.path.join(HERE, "..", "icons", "icons.json")))
TERRAIN = open(os.path.join(HERE, "..", "images", "terrain.png"), "rb").read()
TERRAIN_HASH = hashlib.sha256(TERRAIN).hexdigest()

# ---------------------------------------------------------------- tokens (mirror core/ui theme)
BG = "#0A0E13"        # app background
SURF = "#11171F"      # cards, sheets, nav bar
SURF2 = "#18202A"     # inset wells, search bar
SURF3 = "#222C38"     # neutral chips, tonal buttons
LINE = "#232D3A"      # hairlines
TX = "#E7ECF3"        # primary text
TX2 = "#A3AFBF"       # secondary text
MUTED = "#6B7889"     # captions, overlines
PRIMARY = "#8AB4FF"   # interactive / selection (M3 dark primary)
ON_PRIMARY = "#0B1B36"
AI = "#B79CFF"        # NOVA / SMARTS machine output
OK = "#4ADE80"        # nominal, synced, recommended
WARN = "#F5A524"      # degraded, pending, caution
CRIT = "#FF6B6B"      # hostile, conflict, abort
# MIL-STD-2525-inspired affiliation colors (map symbology only)
FRIEND = "#4FC3F7"
HOSTILE = CRIT
UNKNOWN = "#F7D154"
NEUTRAL = "#6EDB8F"
# map
MAPBG = "#0D131A"

W, H = 412, 915          # Pixel 8 viewport in dp
M = 16                   # side margin
NAV_Y = 811              # navigation bar top
SANS, MONO = "Inter", "Menlo"

def col(h, a=1.0):
    h = h.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return {"r": r, "g": g, "b": b, "a": a}

def solid(h, a=1.0):
    return [{"type": "solid", "color": col(h, a), "opacity": 1.0, "visible": True, "blend": "normal"}]

def stroke_spec(sw, dashes=None, align="inside", cap="none"):
    return {"weight": float(sw), "align": align, "sides": None, "cap_start": cap, "cap_end": cap, "join": "round", "miter_limit": 4.0,
            "dashes": [float(d) for d in (dashes or [])]}

# text width estimate (fix.py corrects centered/right text from FigCraft's measured widths)
_NARROW = set("ilI.,:;!|'`\"· ")
_SEMI = set("fjrt()[]-–/")
_WIDE = set("mwABCDFGHKNOQRSTUVXYZ+=→←✓×≥≤")
_XWIDE = set("MW%@—●◐")
_WMULT = {"Regular": 0.98, "Medium": 1.01, "Semi Bold": 1.04, "Bold": 1.07}

def est_width(s, size, style, font=SANS, ls=0.0):
    def units(line):
        if font == MONO: return len(line) * 0.602
        u = 0.0
        for ch in line:
            u += 0.28 if ch in _NARROW else 0.36 if ch in _SEMI else 0.65 if ch in _WIDE else 0.82 if ch in _XWIDE else 0.53
        return u
    lines = s.split("\n")
    mult = 1.0 if font == MONO else _WMULT.get(style, 1.0)
    return max(units(l) * size * mult + len(l) * size * ls / 100 for l in lines) + 2.0

# ---------------------------------------------------------------- document
class Doc:
    def __init__(self):
        self.nodes = {1: {"id": 1, "name": "Wingman C2 · Android", "type": "page", "background": col("#05080C"), "flows": [], "guides": [],
                          "prototype": {"device": "None", "background": col("#05080C"), "rotation": "portrait"}, "children": [], "width": 0.0, "height": 0.0}}
        self.next = 2; self.frames = []

    def add(self, parent, node):
        i = self.next; self.next += 1
        node["id"] = i; node["parent"] = parent
        self.nodes[i] = node
        self.nodes[parent]["children"].append(i)
        return i

    def frame(self, name, x, y, w=W, h=H, fill=BG):
        f = self.add(1, {"name": name, "type": "frame", "children": [], "transform": [1, 0, 0, 1, float(x), float(y)], "width": float(w), "height": float(h),
                         "fills": solid(fill), "clip": True})
        self.frames.append((name, f))
        return f

    def clip(self, p, x, y, w, h, fill=None, r=0, stroke=None, name="Clip"):
        n = {"name": name, "type": "frame", "children": [], "transform": [1, 0, 0, 1, float(x), float(y)], "width": float(w), "height": float(h),
             "corners": {"radii": [float(r)] * 4, "smoothing": 0.0}, "clip": True}
        if fill: n["fills"] = solid(fill)
        if stroke: n["strokes"] = solid(stroke); n["stroke"] = stroke_spec(1)
        return self.add(p, n)

    def rect(self, p, x, y, w, h, fill=None, r=0, stroke=None, sw=1.0, a=1.0, rot=0.0, dashes=None, radii=None, name="Rectangle"):
        t = [1, 0, 0, 1, float(x), float(y)]
        if rot:
            c, s = math.cos(rot), math.sin(rot); t = [c, s, -s, c, float(x), float(y)]
        n = {"name": name, "type": "rectangle", "blend": "normal", "transform": t, "width": float(w), "height": float(h),
             "corners": {"radii": [float(v) for v in (radii or [r] * 4)], "smoothing": 0.0}}
        if fill: n["fills"] = solid(fill, a)
        if stroke: n["strokes"] = solid(stroke); n["stroke"] = stroke_spec(sw, dashes)
        return self.add(p, n)

    def ellipse(self, p, cx, cy, r, fill=None, stroke=None, sw=1.0, a=1.0, dashes=None, sa=1.0, name="Ellipse"):
        n = {"name": name, "type": "ellipse", "start": 0.0, "sweep": 2 * math.pi, "inner": 0.0, "blend": "normal",
             "transform": [1, 0, 0, 1, float(cx - r), float(cy - r)], "width": float(2 * r), "height": float(2 * r)}
        if fill: n["fills"] = solid(fill, a)
        if stroke: n["strokes"] = solid(stroke, sa); n["stroke"] = stroke_spec(sw, dashes, "center")
        return self.add(p, n)

    def path(self, p, pts, color, sw=1.5, dashes=None, closed=False, a=1.0, name="Path"):
        mx, my = min(x for x, _ in pts), min(y for _, y in pts)
        verts = [{"x": float(x - mx), "y": float(y - my), "radius": 0.0} for x, y in pts]
        n_seg = len(pts) if closed else len(pts) - 1
        segs = [{"a": k, "b": (k + 1) % len(pts), "ta": [0.0, 0.0], "tb": [0.0, 0.0]} for k in range(n_seg)]
        return self.add(p, {"name": name, "type": "vector", "blend": "normal", "transform": [1, 0, 0, 1, float(mx), float(my)],
                            "width": float(max(1, max(x for x, _ in pts) - mx)), "height": float(max(1, max(y for _, y in pts) - my)),
                            "network": {"vertices": verts, "segments": segs, "regions": []},
                            "strokes": solid(color, a), "stroke": stroke_spec(sw, dashes, "center", "round")})

    def text(self, p, x, y, s, size=14, style="Regular", color=TX, w=None, align=None, lh=1.3, font=SANS, ls=0.0, name=None):
        """Left text auto-sizes; centered/right text is a fixed-width box FigCraft aligns itself."""
        n = {"name": name or (s.split("\n")[0][:40] or "Text"), "type": "text", "characters": s,
             "style": {"font_family": font, "font_style": style, "font_size": float(size),
                       "line_height": {"unit": "percent", "value": lh * 100} if "\n" in s else {"unit": "auto"},
                       "letter_spacing": {"unit": "percent", "value": float(ls)}, "decoration": "none", "case": "original", "fills": None, "link": None},
             "blend": "normal", "transform": [1, 0, 0, 1, float(x), float(y)], "width": float(w if align else est_width(s, size, style, font, ls)),
             "height": float(len(s.split("\n")) * size * lh), "fills": solid(color)}
        if align: n["align_h"] = align; n["auto_resize"] = "height"
        return self.add(p, n)

    def icon(self, p, name, x, y, size=24, color=TX):
        """Material Symbol from icons.json, scaled from its 48-unit box into a size×size box at (x, y)."""
        k = size / ICONS[name]["size"]
        for part in ICONS[name]["parts"]:
            net = part["network"]
            verts = [dict(v, x=v["x"] * k, y=v["y"] * k) for v in net["vertices"]]
            segs = [dict(s, ta=[t * k for t in s["ta"]], tb=[t * k for t in s["tb"]]) for s in net["segments"]]
            self.add(p, {"name": "icon/" + name, "type": "vector", "blend": "normal",
                         "transform": [1, 0, 0, 1, float(x + part["x"] * k), float(y + part["y"] * k)],
                         "width": float(max(v["x"] for v in verts)), "height": float(max(v["y"] for v in verts)),
                         "network": {"vertices": verts, "segments": segs, "regions": net["regions"]}, "fills": solid(color)})

    def image(self, p, x, y, w, h, name="Basemap"):
        return self.add(p, {"name": name, "type": "rectangle", "blend": "normal", "transform": [1, 0, 0, 1, float(x), float(y)],
                            "width": float(w), "height": float(h), "corners": {"radii": [0.0] * 4, "smoothing": 0.0},
                            "fills": [{"type": "image", "image": TERRAIN_HASH, "scale_mode": "fill", "scale": 1.0, "rotation": 0, "crop": None,
                                       "opacity": 1.0, "visible": True, "blend": "normal",
                                       "filters": {"contrast": 0.0, "exposure": 0.0, "highlights": 0.0, "saturation": 0.0, "shadows": 0.0, "temperature": 0.0, "tint": 0.0}}]})

    def label(self, p, x, y, w, h, s, size=14, style="Semi Bold", color=TX, font=SANS, ls=0.0):
        return self.text(p, x, y + (h - size * 1.21) / 2, s, size, style, color, w=w, align="center", font=font, ls=ls)

    def nav(self, src_ids, dest):
        for i in src_ids:
            self.nodes[i]["interactions"] = [{"trigger": {"type": "onClick"}, "actions": [{"type": "navigate", "dest": dest}],
                                              "transition": {"type": "dissolve"}, "duration_ms": 200, "easing": "easeOut"}]

    def write(self, path):
        by = {n: i for n, i in self.frames}
        self.nodes[1]["flows"] = [{"name": "Detect → Understand → Plan → Execute → Sync", "start": by["01 · Battlespace"],
                                   "description": "TRK-07 from map pick, through NOVA lineage and SMARTS COA approval, to the live mission under DDIL."}]
        doc = {"format": "figcraft", "version": 1, "imageIndex": [{"hash": TERRAIN_HASH, "width": 1024, "height": 1024}], "name": "wingman-c2",
               "pages": [1], "nodes": {str(k): v for k, v in self.nodes.items()},
               "next_id": self.next, "styles": [], "variables": {"collections": [], "variables": []}, "current_page": 1, "document_colors": [], "comments": []}
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("document.json", json.dumps(doc))
            z.writestr(f"images/{TERRAIN_HASH}", TERRAIN)

D = Doc()
NAV = {}
def goto(ids, frame):
    NAV.setdefault(frame, []).extend(ids if isinstance(ids, (list, tuple)) else [ids])

# ---------------------------------------------------------------- primitives
def overline(p, x, y, s, color=MUTED):
    return D.text(p, x, y, s.upper(), 11, "Semi Bold", color, ls=8)

def card(p, x, y, w, h, fill=SURF, r=16):
    return D.rect(p, x, y, w, h, fill, r, stroke=LINE, name="Card")

def chip(p, x, y, s, fg=TX2, bg=None, a=0.14, h=28, size=12, dot=False, right=False, mono=False, style="Semi Bold", check=False):
    """Pill chip; returns (rect_id, width). right=True treats x as the right edge."""
    font = MONO if mono else SANS
    lead = 14 if (dot or check) else 0
    w = est_width(s, size, style, font) + 20 + lead
    if right: x -= w
    r = D.rect(p, x, y, w, h, bg or fg, h / 2, a=a if bg is None else 1.0, name="Chip " + s)
    if dot: D.ellipse(p, x + 14, y + h / 2, 3.5, fg)
    if check: icon_check(p, x + 10, y + h / 2 - 4, fg)
    D.label(p, x + lead, y, w - lead, h, s, size, style, fg, font)
    return r, w

def button(p, x, y, w, h, s, kind="filled", fg=None, size=14):
    fill, a, stroke, color = {
        "filled": (PRIMARY, 1.0, None, ON_PRIMARY),
        "tonal": (SURF3, 1.0, None, TX),
        "outlined": (None, 1.0, LINE, PRIMARY),
        "danger": (CRIT, 0.16, None, CRIT),
    }[kind]
    r = D.rect(p, x, y, w, h, fill, h / 2, a=a, stroke=stroke, name="Button " + s)
    D.label(p, x, y, w, h, s, size, "Semi Bold", fg or color)
    return r

def kv(p, x, y, k, v, vcolor=TX, vsize=15):
    D.text(p, x, y, k.upper(), 10.5, "Semi Bold", MUTED, ls=6)
    D.text(p, x, y + 17, v, vsize, "Bold", vcolor, font=MONO)

# ---------------------------------------------------------------- icons (24 dp box unless noted)
def icon_check(p, x, y, c, s=1.0):   # (x, y) = top-left of a ~10×8 tick, kept for call-site compatibility
    D.icon(p, "check", x - 2 * s, y - 4 * s, 14 * s, c)

def icon_search(p, x, y, c): D.icon(p, "search", x, y, 24, c)
def icon_chevron(p, x, y, c): D.icon(p, "chevron_right", x - 7, y - 7, 24, c)
def icon_map(p, x, y, c): D.icon(p, "map", x, y, 24, c)
def icon_graph(p, x, y, c): D.icon(p, "hub", x, y, 24, c)
def icon_route(p, x, y, c): D.icon(p, "route", x, y, 24, c)
def icon_target(p, x, y, c): D.icon(p, "my_location", x, y, 24, c)
def icon_drone(p, x, y, c): D.icon(p, "drone", x, y, 24, c)
def icon_sync(p, x, y, c): D.icon(p, "sync", x, y, 24, c)
def icon_layers(p, x, y, c): D.icon(p, "layers", x, y, 24, c)

# ---------------------------------------------------------------- MIL-STD-2525-inspired symbols
def sym_friend(p, cx, cy, s=14, uav=False):
    if uav:  # friendly air frame (dome) with a UAV glyph
        w, h = s * 1.5, s * 1.1
        D.rect(p, cx - w / 2, cy - h / 2, w, h, FRIEND, radii=[w / 2, w / 2, 2, 2], stroke=BG, sw=1.5, name="Friendly air")
        D.icon(p, "drone", cx - s * 0.42, cy - s * 0.32, s * 0.84, BG)
    else:
        D.rect(p, cx - s * 0.72, cy - s / 2, s * 1.44, s, FRIEND, 2, stroke=BG, sw=1.5, name="Friendly")

def sym_hostile(p, cx, cy, s=14):
    a = s * 0.95
    D.rect(p, cx, cy - a / math.sqrt(2), a, a, HOSTILE, 1.5, stroke=BG, sw=1.5, rot=math.pi / 4, name="Hostile")

def sym_unknown(p, cx, cy, s=14):
    r = s * 0.3
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)): D.ellipse(p, cx + dx * r * 0.85, cy + dy * r * 0.85, r, UNKNOWN)
    D.rect(p, cx - r, cy - r, 2 * r, 2 * r, UNKNOWN, name="Unknown")

def sym_neutral(p, cx, cy, s=14):
    D.rect(p, cx - s / 2, cy - s / 2, s, s, NEUTRAL, 1.5, stroke=BG, sw=1.5, name="Neutral")

def map_tag(p, x, y, s, color=TX):
    w = est_width(s, 10.5, "Bold", MONO) + 10
    D.rect(p, x, y, w, 18, "#05080C", 4, a=0.78)
    D.text(p, x + 5, y + 3, s, 10.5, "Bold", color, font=MONO)
    return w

# ---------------------------------------------------------------- basemap
def basemap(p, w, h, ox, oy, size, grid=True):
    """Nano Banana terrain (ridge runs lower-left → upper-right) placed at (ox, oy) and clipped by the parent,
    dimmed so symbols stay dominant, with a faint 1 km MGRS-style grid."""
    D.image(p, ox, oy, size, size)
    D.rect(p, 0, 0, w, h, BG, a=0.28, name="Dim")
    if grid:
        for gx in range(0, int(w) + 1, 64): D.path(p, [(gx, 0), (gx, h)], TX, 1, a=0.05)
        for gy in range(0, int(h) + 1, 64): D.path(p, [(0, gy), (w, gy)], TX, 1, a=0.05)

# ---------------------------------------------------------------- shared chrome
SCREENS = ["01 · Battlespace", "02 · Intel", "03 · Plan", "04 · Mission", "05 · Sync"]
TABS = [("Map", icon_map), ("Intel", icon_graph), ("Plan", icon_route), ("Mission", icon_drone), ("Sync", icon_sync)]

def status_bar(p):
    D.text(p, 20, 8, "9:42", 13, "Medium", TX)
    for k in range(4):
        bh = 4 + k * 2.5
        D.rect(p, 336 + k * 4.5, 21 - bh, 3, bh, TX, 1)
    D.rect(p, 360, 10, 22, 11, None, 3, stroke=TX2, sw=1.2)
    D.rect(p, 362, 12, 14, 7, TX, 1.5)
    D.rect(p, 383, 13.5, 2, 4, TX2, 1)

def app_bar(p, title, sub, link="online"):
    status_bar(p)
    D.text(p, M, 40, title, 22, "Semi Bold", TX)
    D.text(p, M, 70, sub, 12.5, "Regular", TX2)
    if link == "online":
        chip(p, W - M, 46, "Online · 42 ms", OK, dot=True, right=True)
    else:
        chip(p, W - M, 46, "Denied · 4 queued", WARN, dot=True, right=True)

def ddil_banner(p):
    """Global banner every screen shows while the link is denied (driven by LinkState)."""
    D.rect(p, 0, 98, W, 32, WARN, a=0.12)
    D.rect(p, 0, 98, 3, 32, WARN)
    D.text(p, M, 106, "Link denied — working from on-device store. 4 changes queued.", 12, "Medium", WARN)

def nav_bar(p, active):
    D.rect(p, 0, NAV_Y, W, H - NAV_Y, SURF)
    D.path(p, [(0, NAV_Y), (W, NAV_Y)], LINE, 1)
    tw = W / len(TABS)
    for i, (name, ic) in enumerate(TABS):
        cx = tw * i + tw / 2
        on = name == active
        hit = D.rect(p, tw * i, NAV_Y, tw, 80, None, name="Tab " + name)
        if on: D.rect(p, cx - 32, NAV_Y + 12, 64, 32, PRIMARY, 16, a=0.18)
        ic(p, cx - 12, NAV_Y + 16, PRIMARY if on else TX2)
        D.label(p, tw * i, NAV_Y + 50, tw, 16, name, 12, "Semi Bold" if on else "Medium", TX if on else TX2)
        goto(hit, SCREENS[i])
    D.rect(p, W / 2 - 54, H - 12, 108, 4, TX2, 2, a=0.6, name="Gesture handle")

def section(p, y, title, meta=None, x=M, w=W - 2 * M):
    D.text(p, x, y, title, 15, "Semi Bold", TX)
    if meta: D.text(p, x, y + 2, meta, 12, "Regular", MUTED, w=w, align="right")

# ==============================================================================
# 01 · Battlespace — map-first C2 picture with entity sheet
# ==============================================================================
f = D.frame(SCREENS[0], 0, 0)
app_bar(f, "Battlespace", "Sector 4 · 14 tracks · offline tiles")

# NOVA search bar
D.rect(f, M, 104, W - 2 * M, 48, SURF2, 24, name="Search bar")
icon_search(f, 30, 116, TX2)
D.text(f, 62, 119, "Ask NOVA about the battlespace…", 14, "Regular", MUTED)
chip(f, W - M - 10, 114, "NOVA", AI, h=28, size=11, right=True)

# Affiliation filter chips
x = M
for label_s, c, sel in (("All 14", PRIMARY, True), ("Hostile 3", HOSTILE, False), ("Unknown 4", UNKNOWN, False), ("Friendly 7", FRIEND, False)):
    if sel:
        _, w_ = chip(f, x, 164, label_s, PRIMARY, h=32, size=12.5, check=True, a=0.18)
    else:
        w_ = est_width(label_s, 12.5, "Medium") + 38
        D.rect(f, x, 164, w_, 32, None, 8, stroke=LINE)
        D.ellipse(f, x + 15, 180, 4, c)
        D.text(f, x + 26, 172, label_s, 12.5, "Medium", TX2)
    x += w_ + 8

# Map (full-bleed, clipped)
MY, MH = 208, 400
mp = D.clip(f, 0, MY, W, MH, MAPBG, name="Map")
basemap(mp, W, MH, -60, -80, 520)
# ZONE-4B no-fly polygon
nfz = [(48, 178), (150, 158), (170, 246), (70, 268)]
D.path(mp, nfz, WARN, 1.5, dashes=[6, 4], closed=True)
map_tag(mp, 56, 186, "ZONE-4B · NFZ", WARN)
# TRK-07 threat envelope
D.ellipse(mp, 300, 136, 82, HOSTILE, stroke=HOSTILE, sw=1.2, a=0.07, dashes=[5, 4], sa=0.8)
D.ellipse(mp, 300, 136, 16, None, stroke=TX, sw=1.5)
sym_hostile(mp, 300, 136, 15)
map_tag(mp, 322, 128, "TRK-07", HOSTILE)
# others
sym_unknown(mp, 206, 116); map_tag(mp, 218, 98, "SPOT-19", UNKNOWN)
sym_neutral(mp, 368, 40)
sym_friend(mp, 46, 330); sym_friend(mp, 330, 300)
# UAS-01 with flown track
D.path(mp, [(10, 384), (50, 364), (92, 348), (128, 330)], FRIEND, 2, dashes=[2, 4], a=0.8)
D.path(mp, [(128, 330), (152, 314)], FRIEND, 2)
sym_friend(mp, 128, 330, 16, uav=True)
map_tag(mp, 146, 334, "UAS-01 · 185 m", FRIEND)
# overlays
w_ = map_tag(mp, 12, 12, "18S UJ 2340 0675", TX2)
for k, ic in enumerate((icon_layers, icon_target)):
    D.rect(mp, W - 52, 12 + k * 48, 40, 40, SURF, 12, stroke=LINE)
    ic(mp, W - 44, 20 + k * 48, TX)
D.path(mp, [(W - 76, 356), (W - 76, 362), (W - 12, 362), (W - 12, 356)], TX2, 1.5)
D.text(mp, W - 76, 340, "500 m", 10.5, "Medium", TX2, w=64, align="center")

# Entity bottom sheet
SY = 590
D.rect(f, 0, SY, W, NAV_Y - SY, SURF, radii=[24, 24, 0, 0], stroke=LINE, name="Entity sheet")
D.rect(f, W / 2 - 16, SY + 8, 32, 4, MUTED, 2)
D.rect(f, M, SY + 24, 40, 40, HOSTILE, 12, a=0.14)
sym_hostile(f, M + 20, SY + 44, 13)
D.text(f, 68, SY + 22, "TRK-07", 18, "Semi Bold", TX)
D.text(f, 68, SY + 46, "S-band emitter · suspect hostile", 12.5, "Regular", TX2)
chip(f, W - M, SY + 28, "Conf 0.84", WARN, right=True, size=12, mono=False)
for k, (kk, vv) in enumerate((("Freq", "3.1 GHz"), ("Bearing", "042°"), ("Last seen", "2 min"))):
    kv(f, M + k * 128, SY + 80, kk, vv)
D.text(f, M, SY + 130, "Fused from RF ping · SPOT-19 · AIS (partial)", 12, "Regular", MUTED)
b1 = button(f, M, SY + 160, 182, 44, "View lineage", "outlined")
b2 = button(f, M + 198, SY + 160, 182, 44, "Plan recon", "filled")
goto(b1, SCREENS[1]); goto(b2, SCREENS[2])
nav_bar(f, "Map")

# ==============================================================================
# 02 · Intel — NOVA natural-language query, entity graph, source lineage
# ==============================================================================
f = D.frame(SCREENS[1], 1 * (W + 64), 0)
app_bar(f, "Intel", "NOVA knowledge graph · on-device")

D.rect(f, M, 104, W - 2 * M, 48, SURF2, 24, stroke=AI, name="Query bar")
icon_search(f, 30, 116, AI)
D.text(f, 62, 119, "unverified emitters near Zone-4B", 14, "Regular", TX)
D.text(f, M, 120, "14 ms", 12, "Medium", MUTED, w=W - 2 * M - 18, align="right", font=MONO)
# parsed predicates = explainable query interpretation
overline(f, M, 162, "NOVA interpreted as")
x = M
for s in ("kind:EMITTER", "status:unverified", "near:ZONE-4B ≤500m"):
    _, w_ = chip(f, x, 180, s, AI, h=26, size=10.5, mono=True, style="Regular")
    x += w_ + 6

# Graph card
GY, GH = 218, 284
card(f, M, GY, W - 2 * M, GH)
section(f, GY + 16, "Entity graph", "3 matches · 18 nodes · 29 edges", x=M + 16, w=W - 2 * M - 32)
gp = D.clip(f, M + 1, GY + 44, W - 2 * M - 2, GH - 46, None, name="Graph")
# background nodes to imply the larger graph
for (bx, by_), (ex, ey) in (((20, 108), (60, 80)), ((358, 108), (318, 80)), ((150, 220), (190, 120)), ((240, 228), (190, 120))):
    D.path(gp, [(bx, by_), (ex, ey)], LINE, 1)
    D.ellipse(gp, bx, by_, 4, SURF3)
NODES = {"TRK": (189, 120), "SPOT": (60, 66), "ZONE": (318, 66), "UAS": (60, 168), "SBAND": (318, 168)}
EDGES = [("SPOT", "TRK", "CORROBORATES", UNKNOWN, None), ("ZONE", "TRK", "NEAR 180 m", WARN, None),
         ("UAS", "TRK", "TASKED_TO", FRIEND, None), ("SBAND", "TRK", "INFERRED", AI, [4, 4])]
for a_, b_, lbl, c, dash in EDGES:
    (ax, ay), (bx, by_) = NODES[a_], NODES[b_]
    D.path(gp, [(ax, ay), (bx, by_)], c, 1.5, dashes=dash, a=0.7)
for a_, b_, lbl, c, dash in EDGES:
    (ax, ay), (bx, by_) = NODES[a_], NODES[b_]
    mx, my = (ax + bx) / 2, (ay + by_) / 2
    w_ = est_width(lbl, 9.5, "Bold", MONO) + 10
    D.rect(gp, mx - w_ / 2, my - 9, w_, 18, SURF, 9, stroke=LINE)
    D.label(gp, mx - w_ / 2, my - 9, w_, 18, lbl, 9.5, "Bold", c, MONO)
def gnode(key, title, sub, color, sym, r=20, focus=False):
    cx, cy = NODES[key]
    if focus: D.ellipse(gp, cx, cy, r + 8, color, a=0.10)
    D.ellipse(gp, cx, cy, r, SURF2, stroke=color, sw=2 if focus else 1.5)
    sym(gp, cx, cy)
    ty = cy - r - 36 if cy < NODES["TRK"][1] else cy + r + 4   # top row labels above, others below
    D.label(gp, cx - 60, ty, 120, 16, title, 12, "Semi Bold", TX, MONO)
    D.label(gp, cx - 60, ty + 16, 120, 14, sub, 10.5, "Regular", MUTED)
gnode("TRK", "TRK-07", "Emitter · 0.84", HOSTILE, lambda p, x, y: sym_hostile(p, x, y, 13), r=24, focus=True)
gnode("SPOT", "SPOT-19", "Spot report", UNKNOWN, lambda p, x, y: sym_unknown(p, x, y, 12))
gnode("ZONE", "ZONE-4B", "No-fly zone", WARN, lambda p, x, y: D.rect(p, x - 7, y - 7, 14, 14, None, 2, stroke=WARN, sw=1.5, dashes=[3, 2]))
gnode("UAS", "UAS-01", "Wingman", FRIEND, lambda p, x, y: sym_friend(p, x, y, 12, uav=True))
gnode("SBAND", "S-BAND", "Ontology class", AI, lambda p, x, y: D.ellipse(p, x, y, 5, AI))

# Source lineage
LY = 512
card(f, M, LY, W - 2 * M, 212)
section(f, LY + 16, "Source lineage", "TRK-07", x=M + 16, w=W - 2 * M - 32)
rows = [("RF_PING", '{"src":"rf_rx2","band":"3.1G","az":42}', "09:31"),
        ("SPOT_TEXT", '"Saw van w/ dish east of ridge"', "09:33"),
        ("AIS_JSON", "mmsi, alt_m missing · tolerated", "09:35")]
for k, (fmt, raw, t) in enumerate(rows):
    ry = LY + 48 + k * 36
    chip(f, M + 16, ry + 2, fmt, TX2, bg=SURF3, h=22, size=10, mono=True, style="Bold")
    D.text(f, M + 112, ry + 6, raw, 11, "Regular", TX2, font=MONO)
for k in range(3):
    D.path(f, [(M + 16, LY + 48 + k * 36 + 32), (W - M - 16, LY + 48 + k * 36 + 32)], LINE, 1)
ny = LY + 160
D.rect(f, M + 16, ny, W - 2 * M - 32, 40, OK, 10, a=0.10)
icon_check(f, M + 30, ny + 16, OK)
D.text(f, M + 50, ny + 5, "Fused → ObjectBox entity #408", 12.5, "Semi Bold", TX)
D.text(f, M + 50, ny + 22, "kind=EMITTER  conf=0.84  edges=4  synced", 10.5, "Regular", TX2, font=MONO)

# Extended FAB → Plan
fab = D.rect(f, W - M - 172, 738, 172, 56, PRIMARY, 16, name="FAB Plan recon")
icon_route(f, W - M - 156, 754, ON_PRIMARY)
D.text(f, W - M - 124, 756, "Plan recon", 15, "Semi Bold", ON_PRIMARY)
goto(fab, SCREENS[2])
nav_bar(f, "Intel")

# ==============================================================================
# 03 · Plan — SMARTS commander's intent → ranked, explainable COAs
# ==============================================================================
f = D.frame(SCREENS[2], 2 * (W + 64), 0)
app_bar(f, "Plan", "SMARTS · recon TRK-07")

card(f, M, 104, W - 2 * M, 120)
overline(f, M + 16, 118, "Commander's intent")
D.icon(f, "edit", W - M - 62, 115, 16, PRIMARY)
D.text(f, M + 16, 116, "Edit", 13, "Semi Bold", PRIMARY, w=W - 2 * M - 32, align="right")
D.text(f, M + 16, 138, "“Recon TRK-07 from 200 m standoff, avoid the\nridge radar, keep 30% battery reserve.”", 14.5, "Regular", TX, lh=1.4)
x = M + 16
for s in ("Standoff ≥ 200 m", "Avoid radar", "Reserve ≥ 30%"):
    _, w_ = chip(f, x, 186, s, PRIMARY, h=26, size=11.5, check=True)
    x += w_ + 6

# Route preview with all three COAs
pv = D.clip(f, M, 236, W - 2 * M, 136, MAPBG, r=16, stroke=LINE, name="COA preview")
basemap(pv, W - 2 * M, 136, -20, -160, 420, grid=False)
D.ellipse(pv, 318, 44, 50, HOSTILE, stroke=HOSTILE, sw=1, a=0.08, dashes=[4, 3], sa=0.7)
sym_hostile(pv, 318, 44, 11)
D.path(pv, [(28, 112), (90, 80), (178, 56)], MUTED, 1.5)
D.ellipse(pv, 192, 52, 14, None, stroke=MUTED, sw=1.5, dashes=[3, 3])
D.path(pv, [(28, 112), (140, 62), (272, 32)], WARN, 1.5, dashes=[5, 4])
D.path(pv, [(28, 112), (110, 120), (196, 104), (256, 84)], OK, 3)
sym_friend(pv, 28, 112, 12, uav=True)
for (lx, ly, l, c) in ((256, 84, "A", OK), (272, 32, "B", WARN), (192, 34, "C", MUTED)):
    D.ellipse(pv, lx, ly, 9, c)
    D.label(pv, lx - 9, ly - 9, 18, 18, l, 10.5, "Bold", BG)

section(f, 386, "Courses of action", "Ranked by intent match")

# COA-A expanded (recommended)
AY = 412
D.rect(f, M, AY, W - 2 * M, 176, SURF, 16, stroke=OK, sw=1.5, name="COA-A")
D.ellipse(f, M + 32, AY + 30, 14, OK); D.label(f, M + 18, AY + 16, 28, 28, "A", 13, "Bold", BG)
D.text(f, M + 56, AY + 14, "Terrain-masked ridge flank", 15.5, "Semi Bold", TX)
D.text(f, M + 56, AY + 35, "Recommended · best intent match", 12, "Medium", OK)
for k, (kk, vv, c) in enumerate((("Match", "92%", OK), ("Risk", "0.14", OK), ("ETA", "4:10", TX), ("Reserve", "41%", TX))):
    kv(f, M + 16 + k * 90, AY + 62, kk, vv, c, 14)
D.path(f, [(M + 16, AY + 104), (W - M - 16, AY + 104)], LINE, 1)
for k, s in enumerate(("Ridge shadow keeps UAS below TRK-07 radar lobe",
                       "235 m minimum standoff (limit 200 m)",
                       "Lands with 11% margin over 30% reserve")):
    icon_check(f, M + 18, AY + 118 + k * 18, OK, 0.9)
    D.text(f, M + 34, AY + 113 + k * 18, s, 12, "Regular", TX2)

def coa_row(y, letter, title, sub, sub_c, c):
    r = card(f, M, y, W - 2 * M, 60)
    D.ellipse(f, M + 32, y + 30, 14, None, stroke=c, sw=1.5)
    D.label(f, M + 18, y + 16, 28, 28, letter, 13, "Bold", c)
    D.text(f, M + 56, y + 12, title, 14.5, "Semi Bold", TX)
    D.text(f, M + 56, y + 33, sub, 12, "Regular", sub_c)
    icon_chevron(f, W - M - 24, y + 25, MUTED)
    return r
coa_row(596, "B", "Direct high-altitude transit", "78% · risk 0.42 · 2:20 · 38 s in S-band lobe", WARN, WARN)
coa_row(664, "C", "Passive loiter, EO/IR orbit", "71% · risk 0.18 · 6:45 · zero RF emissions", TX2, TX2)
ap = button(f, M, 740, W - 2 * M, 52, "Approve & dispatch COA-A", "filled", size=15)
goto(ap, SCREENS[3])
nav_bar(f, "Plan")

# ==============================================================================
# 04 · Mission — live wingman execution, telemetry, explainable autonomy log
# ==============================================================================
f = D.frame(SCREENS[3], 3 * (W + 64), 0)
app_bar(f, "Mission", "COA-A · UAS-01 · waypoint 3 of 5", link="denied")
ddil_banner(f)

hy, hh = 130, 300
hm = D.clip(f, 0, hy, W, hh, MAPBG, name="Mission map")
basemap(hm, W, hh, -40, -150, 520)
D.ellipse(hm, 348, 82, 70, HOSTILE, stroke=HOSTILE, sw=1.2, a=0.07, dashes=[5, 4], sa=0.8)
sym_hostile(hm, 348, 82, 13); map_tag(hm, 318, 100, "TRK-07", HOSTILE)
WP = [(40, 250), (110, 212), (186, 176), (250, 196), (276, 150)]
D.path(hm, WP[:2] + [(168, 184)], FRIEND, 2.5)
D.path(hm, [WP[2], WP[3]], MUTED, 1.2, dashes=[2, 4])  # superseded leg
D.path(hm, [(168, 184), WP[2], (224, 220), WP[3]], WARN, 2, dashes=[6, 4])
D.path(hm, [WP[3], WP[4]], TX2, 1.5, dashes=[6, 4])
D.ellipse(hm, 276, 150, 22, None, stroke=TX2, sw=1, dashes=[3, 3])
for k, (wx, wy) in enumerate(WP):
    done = k < 2
    D.ellipse(hm, wx, wy, 8, FRIEND if done else SURF, stroke=None if done else TX2, sw=1.5)
    D.label(hm, wx - 8, wy - 8, 16, 16, str(k + 1), 9, "Bold", BG if done else TX, MONO)
sym_friend(hm, 168, 184, 15, uav=True)
D.rect(hm, 196, 228, 148, 36, "#05080C", 8, a=0.85, stroke=WARN)
D.text(hm, 205, 233, "Re-routed +35 m E", 11.5, "Semi Bold", WARN)
D.text(hm, 205, 249, "terrain mask vs. RF spike", 10, "Regular", TX2)
chip(hm, 12, 12, "En route", OK, h=26, size=11.5, dot=True, bg="#0C1A12")
map_tag(hm, W - 72, 14, "T+01:24", TX)

# Telemetry strip
ty = 442
card(f, M, ty, W - 2 * M, 76)
tw = (W - 2 * M) / 4
for k, (kk, vv, c, sub) in enumerate((("Alt AGL", "162 m", TX, "+2 m/s"), ("Speed", "22 m/s", TX, "hdg 061°"),
                                       ("Battery", "64%", OK, "RTB at 30%"), ("Link", "Denied", WARN, "local 5 Hz"))):
    x = M + k * tw
    if k: D.path(f, [(x, ty + 16), (x, ty + 60)], LINE, 1)
    kv(f, x + 14, ty + 12, kk, vv, c, 15)
    D.text(f, x + 14, ty + 52, sub, 10.5, "Regular", MUTED)

# Explainable autonomy log
ly = 530
card(f, M, ly, W - 2 * M, 196)
section(f, ly + 16, "Autonomy decisions", "Explainable · 3", x=M + 16, w=W - 2 * M - 32)
D.path(f, [(M + 24, ly + 60), (M + 24, ly + 168)], LINE, 1.5)
for k, (t, title, why, c) in enumerate((("T+01:12", "Re-routed +35 m east", "RF spike az 042° → ridge masks 235 m standoff", WARN),
                                        ("T+00:44", "Captured EO frame #14", "Stored on device · queued for sync (link denied)", FRIEND),
                                        ("T+00:00", "COA-A approved by operator", "Locked: ≥200 m standoff · ≥30% reserve", OK))):
    ey = ly + 50 + k * 48
    D.ellipse(f, M + 24, ey + 10, 5, c, stroke=SURF, sw=3)
    D.text(f, M + 40, ey + 2, t, 11, "Bold", MUTED, font=MONO)
    D.text(f, M + 104, ey, title, 13.5, "Semi Bold", TX)
    D.text(f, M + 104, ey + 20, why, 11.5, "Regular", TX2)

# Human-on-the-loop overrides
bw = (W - 2 * M - 16) / 3
button(f, M, 742, bw, 48, "Hold / loiter", "tonal")
rp = button(f, M + bw + 8, 742, bw, 48, "Replan", "tonal")
button(f, M + 2 * (bw + 8), 742, bw, 48, "Return to base", "danger", size=13.5)
goto(rp, SCREENS[2])
nav_bar(f, "Mission")

# ==============================================================================
# 05 · Sync — DDIL link control, edge-vs-cloud state, outbox, scenario injects
# ==============================================================================
f = D.frame(SCREENS[4], 4 * (W + 64), 0)
app_bar(f, "Sync", "Edge store ↔ cloud mirror", link="denied")
ddil_banner(f)

cy = 142
card(f, M, cy, W - 2 * M, 112)
overline(f, M + 16, cy + 14, "Link condition · simulated")
sw_ = (W - 2 * M - 32) / 3
D.rect(f, M + 16, cy + 36, W - 2 * M - 32, 40, None, 20, stroke=LINE, name="Segmented")
for k, (s, on) in enumerate((("Online", False), ("Degraded", False), ("Denied", True))):
    sx = M + 16 + k * sw_
    if k: D.path(f, [(sx, cy + 36), (sx, cy + 76)], LINE, 1)
    if on:
        D.rect(f, sx, cy + 36, sw_, 40, WARN, radii=[0, 20, 20, 0], a=0.18)
        icon_check(f, sx + sw_ / 2 - 34, cy + 52, WARN)
        D.label(f, sx + 8, cy + 36, sw_, 40, s, 13, "Semi Bold", WARN)
    else:
        D.label(f, sx, cy + 36, sw_, 40, s, 13, "Medium", TX2)
D.text(f, M + 16, cy + 88, "RTT —   ·   loss 100%   ·   last ack 2 min ago", 11.5, "Regular", MUTED, font=MONO)

# Edge vs cloud
ey = 266
tw2 = (W - 2 * M - 40) / 2
for k, (t, v, sub, st, c) in enumerate((("Edge · ObjectBox", "v4", "1,284 objects", "Authoritative", OK),
                                        ("Cloud mirror", "v3", "1,280 objects", "Unreachable", WARN))):
    x = M + k * (tw2 + 40)
    card(f, x, ey, tw2, 84, r=14)
    D.text(f, x + 14, ey + 12, t, 12, "Medium", TX2)
    D.text(f, x + 14, ey + 32, v, 20, "Bold", TX, font=MONO)
    D.text(f, x + 48, ey + 38, sub, 11, "Regular", MUTED)
    D.ellipse(f, x + 18, ey + 68, 3.5, c)
    D.text(f, x + 27, ey + 61, st, 11.5, "Semi Bold", c)
ax = M + tw2 + 20
D.path(f, [(ax - 12, ey + 42), (ax + 10, ey + 42)], WARN, 1.5, dashes=[3, 3])
D.path(f, [(ax + 5, ey + 37), (ax + 10, ey + 42), (ax + 5, ey + 47)], WARN, 1.5)
D.label(f, ax - 20, ey + 18, 40, 14, "4", 11, "Bold", WARN, MONO)

# Outbox
oy = 362
card(f, M, oy, W - 2 * M, 252)
section(f, oy + 16, "Outbox", "4 pending · 1 conflict", x=M + 16, w=W - 2 * M - 32)
for k, (op, ent, meta, st, c) in enumerate((("UPSERT", "OntologyEntity · TRK-07", "v4 vs cloud v3", "Conflict", CRIT),
                                           ("INSERT", "TelemetryFrame · UAS-01", "1.2 KB · 8 s", "Pending", WARN),
                                           ("UPSERT", "MissionPlan · COA-A", "3.8 KB · 14 s", "Pending", WARN),
                                           ("INSERT", "OntologyEdge · SPOT-19", "0.6 KB · 22 s", "Pending", WARN),
                                           ("UPSERT", "OntologyEntity · ZONE-4B", "acked by mirror", "Synced", OK))):
    ry = oy + 46 + k * 40
    if k: D.path(f, [(M + 16, ry - 4), (W - M - 16, ry - 4)], LINE, 1)
    D.text(f, M + 16, ry + 2, f"#{914 - k}", 11, "Regular", MUTED, font=MONO)
    D.text(f, M + 56, ry, ent, 12.5, "Semi Bold", TX if k < 4 else TX2)
    D.text(f, M + 56, ry + 17, f"{op} · {meta}", 10.5, "Regular", MUTED, font=MONO)
    if k == 0:
        D.text(f, M, ry + 8, "Resolve", 12.5, "Semi Bold", PRIMARY, w=W - 2 * M - 16, align="right")
        chip(f, W - M - 78, ry + 4, st, c, h=24, size=11, right=True)
    else:
        chip(f, W - M - 16, ry + 4, st, c, h=24, size=11, right=True)

# Scenario injects
iy = 626
card(f, M, iy, W - 2 * M, 168)
section(f, iy + 16, "Scenario injects", "Demo controls", x=M + 16, w=W - 2 * M - 32)
gw = (W - 2 * M - 40) / 2
for k, (s, sym) in enumerate((("Pop-up RF threat", lambda p, x, y: D.icon(p, "sensors", x - 11, y - 11, 22, HOSTILE)),
                              ("Conflicting report", lambda p, x, y: D.icon(p, "warning", x - 11, y - 11, 22, UNKNOWN)),
                              ("Sensor burst ×25", lambda p, x, y: D.icon(p, "satellite_alt", x - 11, y - 11, 22, FRIEND)),
                              ("Restore link", lambda p, x, y: D.icon(p, "sync", x - 11, y - 11, 22, OK)))):
    gx = M + 16 + (k % 2) * (gw + 8)
    gy = iy + 50 + (k // 2) * 54
    D.rect(f, gx, gy, gw, 46, SURF3, 12, name="Inject " + s)
    sym(f, gx + 22, gy + 23)
    D.text(f, gx + 42, gy + 14, s, 13, "Medium", TX if k < 3 else OK)
nav_bar(f, "Sync")

# ==============================================================================
# 00 · Foundations — tokens, type, symbology, components (maps to core/ui)
# ==============================================================================
FW, FH = 5 * W + 4 * 64, 640
f = D.frame("00 · Foundations", 0, H + 120, FW, FH, BG)
D.text(f, 40, 36, "Wingman C2 — Foundations", 28, "Bold", TX)
D.text(f, 40, 76, "Material 3 dark · 8 dp grid · 16 dp margins · color carries meaning, not decoration · MIL-STD-2525-inspired affiliation colors on the map only",
       14, "Regular", TX2)

def swatch_group(x, y, title, items):
    overline(f, x, y, title)
    for k, (n, h) in enumerate(items):
        sx = x + k * 112
        D.rect(f, sx, y + 24, 96, 64, h, 12, stroke=LINE)
        D.text(f, sx, y + 96, n, 12.5, "Semi Bold", TX)
        D.text(f, sx, y + 114, h, 11, "Regular", MUTED, font=MONO)
swatch_group(40, 124, "Surfaces", (("background", BG), ("surface", SURF), ("surface-2", SURF2), ("surface-3", SURF3), ("outline", LINE)))
swatch_group(40, 284, "Text", (("on-surface", TX), ("on-surface-2", TX2), ("muted", MUTED)))
swatch_group(660, 124, "Semantic", (("primary", PRIMARY), ("ai", AI), ("ok", OK), ("caution", WARN), ("critical", CRIT)))
overline(f, 660, 284, "Affiliation (map)")
for k, (n, h, sym) in enumerate((("friendly", FRIEND, sym_friend), ("hostile", HOSTILE, sym_hostile), ("unknown", UNKNOWN, sym_unknown), ("neutral", NEUTRAL, sym_neutral))):
    sx = 660 + k * 112
    D.rect(f, sx, 308, 96, 64, MAPBG, 12, stroke=LINE)
    sym(f, sx + 48, 340, 20)
    D.text(f, sx, 380, n, 12.5, "Semi Bold", TX)
    D.text(f, sx, 398, h, 11, "Regular", MUTED, font=MONO)

# type scale
tx0 = 1300
overline(f, tx0, 124, "Type · Inter for UI, Menlo for data")
for k, (n, s, st, fnt, sample) in enumerate((("Title", 22, "Semi Bold", SANS, "Battlespace"), ("Headline", 18, "Semi Bold", SANS, "TRK-07 · S-band emitter"),
                                             ("Card title", 15, "Semi Bold", SANS, "Courses of action"), ("Body", 14, "Regular", SANS, "Recon TRK-07 from 200 m standoff"),
                                             ("Label", 12, "Medium", SANS, "Fused from RF ping · SPOT-19"), ("Overline", 11, "Semi Bold", SANS, "COMMANDER'S INTENT"),
                                             ("Data", 15, "Bold", MONO, "162 m  22 m/s  042°"))):
    yy = 152 + k * 40
    D.text(f, tx0, yy + 4, f"{n} · {s:g}", 11, "Regular", MUTED, font=MONO)
    D.text(f, tx0 + 140, yy, sample, s, st, TX, font=fnt, ls=8 if n == "Overline" else 0)

# components
cx0 = 40; cy0 = 450
overline(f, cx0, cy0, "Components")
x = cx0
for s, kind in (("Plan recon", "filled"), ("Replan", "tonal"), ("View lineage", "outlined"), ("Return to base", "danger")):
    w_ = est_width(s, 14, "Semi Bold") + 48
    button(f, x, cy0 + 28, w_, 44, s, kind); x += w_ + 12
x += 24
for s, c in (("Online · 42 ms", OK), ("Degraded · 380 ms", WARN), ("Denied · 4 queued", WARN)):
    _, w_ = chip(f, x, cy0 + 36, s, c, dot=True); x += w_ + 10
x += 24
for s, c in (("Recommended", OK), ("Pending", WARN), ("Conflict", CRIT), ("NOVA", AI)):
    _, w_ = chip(f, x, cy0 + 36, s, c, size=11.5); x += w_ + 10
x += 24
_, w_ = chip(f, x, cy0 + 36, "Standoff ≥ 200 m", PRIMARY, check=True, size=11.5); x += w_ + 10
chip(f, x, cy0 + 36, "near ZONE-4B ≤ 500 m", AI, mono=True, style="Regular", size=11)
# nav bar sample
nb = D.clip(f, cx0, cy0 + 96, W, 80, SURF, r=12, name="Nav bar sample")
tw = W / len(TABS)
for i, (name, ic) in enumerate(TABS):
    c_ = tw * i + tw / 2
    on = i == 0
    if on: D.rect(nb, c_ - 32, 12, 64, 32, PRIMARY, 16, a=0.18)
    ic(nb, c_ - 12, 16, PRIMARY if on else TX2)
    D.label(nb, tw * i, 50, tw, 16, name, 12, "Semi Bold" if on else "Medium", TX if on else TX2)
D.text(f, cx0 + W + 24, cy0 + 110, "5 destinations = 5 screens, 1:1. The DDIL banner and link chip are global:\nevery screen reads the same LinkState, so an operator never has to visit Sync to know they're offline.",
       13, "Regular", TX2, lh=1.5)

overline(f, 1300, 546, "Icons · Material Symbols (SVG → vector)")
for k, n in enumerate(("map", "hub", "route", "drone", "sync", "search", "layers", "my_location", "sensors", "satellite_alt", "warning", "edit")):
    D.icon(f, n, 1300 + k * 44, 574, 24, TX2)
D.text(f, 1300, 612, "Basemap: Nano Banana terrain render, dimmed 28% under a 1 km grid (stand-in for offline MBTiles).", 12, "Regular", MUTED)

# ---------------------------------------------------------------- wire prototype + write
by_name = {n: i for n, i in D.frames}
for frame, ids in NAV.items():
    D.nav(ids, by_name[frame])
D.write(sys.argv[1])
print("wrote", sys.argv[1], "nodes:", len(D.nodes))

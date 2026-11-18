import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

P = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ROOT = os.path.join(P, "..")
FONT = os.path.join(ROOT, "core/designsystem/src/main/res/font")
OUT = os.path.join(P, "readme")
BG, SURF, LINE, TX, TX2, MUTED = "#0A0E13", "#11171F", "#232D3A", "#E7ECF3", "#A3AFBF", "#6B7889"
PRIMARY, AI, OK, WARN, CRIT = "#8AB4FF", "#B79CFF", "#4ADE80", "#F5A524", "#FF6B6B"
SHOTS = ["01-battlespace", "02-intel", "03-plan", "04-mission", "05-sync"]
LABELS = ["Battlespace", "Intel · NOVA", "Plan · SMARTS", "Mission", "Sync · DDIL"]
ACCENTS = [PRIMARY, AI, OK, WARN, CRIT]


def font(name, size, weight=None):
    f = ImageFont.truetype(os.path.join(FONT, name), size)
    if weight:
        try:
            f.set_variation_by_axes([weight])
        except Exception:
            pass
    return f


def rgba(hex_, a=255):
    h = hex_.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def backdrop(w, h):
    im = Image.new("RGBA", (w, h), rgba(BG))
    grid = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    g = ImageDraw.Draw(grid)
    for x in range(0, w, 48):
        g.line([(x, 0), (x, h)], fill=rgba(LINE, 70), width=1)
    for y in range(0, h, 48):
        g.line([(0, y), (w, y)], fill=rgba(LINE, 70), width=1)
    im.alpha_composite(grid)
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse([w * 0.18, -h * 0.55, w * 0.82, h * 0.55], fill=rgba(PRIMARY, 46))
    d.ellipse([w * 0.62, h * 0.35, w * 1.15, h * 1.25], fill=rgba(AI, 26))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(160)))
    fade = Image.linear_gradient("L").resize((w, h)).point(lambda v: int(v * 0.55))
    im.alpha_composite(Image.composite(Image.new("RGBA", (w, h), rgba(BG)), Image.new("RGBA", (w, h), (0, 0, 0, 0)), fade))
    return im


def phone(path, w, radius):
    src = Image.open(path).convert("RGBA")
    h = round(src.height * w / src.width)
    src = src.resize((w, h), Image.LANCZOS)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius, fill=255)
    card = Image.new("RGBA", (w + 12, h + 12), (0, 0, 0, 0))
    ImageDraw.Draw(card).rounded_rectangle([0, 0, w + 11, h + 11], radius + 6, fill=rgba("#05080C"), outline=rgba(LINE), width=2)
    card.paste(src, (6, 6), mask)
    return card


def shadowed(canvas, card, x, y, blur=36, alpha=150):
    sh = Image.new("RGBA", (card.width + blur * 4, card.height + blur * 4), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([blur * 2, blur * 2 + 18, blur * 2 + card.width, blur * 2 + card.height + 18], 60, fill=(0, 0, 0, alpha))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)), (x - blur * 2, y - blur * 2))
    canvas.alpha_composite(card, (x, y))


def pill(d, x, y, text, f, color):
    tw = d.textlength(text, font=f)
    d.rounded_rectangle([x, y, x + tw + 40, y + 48], 24, fill=rgba(SURF, 235), outline=rgba(color, 150), width=2)
    d.ellipse([x + 16, y + 19, x + 26, y + 29], fill=rgba(color))
    d.text((x + 32, y + 24), text, font=f, fill=rgba(TX2), anchor="lm")
    return x + tw + 56


def hero():
    W, H = 2400, 1500
    im = backdrop(W, H)
    d = ImageDraw.Draw(im)
    icon = Image.open(os.path.join(P, "brand/icon-512.png")).convert("RGBA").resize((168, 168), Image.LANCZOS)
    title, sub, mono = font("inter.ttf", 108, 700), font("inter.ttf", 40, 400), font("jetbrains_mono.ttf", 26)
    tw = d.textlength("Wingman C2", font=title)
    x0 = round((W - (168 + 40 + tw)) / 2)
    shadowed(im, icon, x0, 92, blur=24, alpha=120)
    d.text((x0 + 208, 176), "Wingman C2", font=title, fill=rgba(TX), anchor="lm")
    d.text((W / 2, 318), "Offline-first tactical command & control for Android", font=sub, fill=rgba(TX2), anchor="mm")
    tags = [("Kotlin · Compose · Material 3", PRIMARY), ("ObjectBox HNSW vectors", OK), ("Explainable autonomy", AI), ("DDIL-resilient sync", WARN)]
    widths = [d.textlength(t, font=mono) + 56 for t, _ in tags]
    x = (W - sum(widths) - 20 * (len(tags) - 1)) / 2
    for (t, c), w in zip(tags, widths):
        pill(d, x, 380, t, mono, c)
        x += w + 20
    pw, gap = 404, 48
    left = (W - (pw + 12) * 5 - gap * 4) // 2
    lab = font("jetbrains_mono.ttf", 24)
    for i, name in enumerate(SHOTS):
        card = phone(os.path.join(P, "screenshots", name + ".png"), pw, 46)
        x = left + i * (pw + 12 + gap)
        y = 520 + (0 if i == 2 else 34 if i in (1, 3) else 68)
        shadowed(im, card, x, y)
        cx = x + card.width / 2
        d.ellipse([cx - 82, y - 44, cx - 70, y - 32], fill=rgba(ACCENTS[i]))
        d.text((cx - 60, y - 38), LABELS[i].upper(), font=lab, fill=rgba(MUTED), anchor="lm")
    return im.convert("RGB")


def brand():
    W, H, sizes = 2400, 900, [288, 192, 128, 96]
    im = backdrop(W, H)
    d = ImageDraw.Draw(im)
    splash = phone(os.path.join(P, "screenshots/splash.png"), 340, 40)
    total = splash.width + 140 + sum(sizes) + 64 * (len(sizes) - 1)
    x = (W - total) // 2
    shadowed(im, splash, x, (H - splash.height) // 2)
    x += splash.width + 140
    icon = Image.open(os.path.join(P, "brand/icon-512.png")).convert("RGBA")
    lab = font("jetbrains_mono.ttf", 26)
    for s in sizes:
        shadowed(im, icon.resize((s, s), Image.LANCZOS), x, (H - s) // 2 - 30, blur=20, alpha=110)
        d.text((x + s / 2, (H + 288) // 2 + 10), f"{s}px", font=lab, fill=rgba(MUTED), anchor="mm")
        x += s + 64
    return im.convert("RGB")


def modules():
    W, H = 1200, 790
    sans, mono = "Inter, -apple-system, Segoe UI, Helvetica, Arial, sans-serif", "JetBrains Mono, SF Mono, Menlo, Consolas, monospace"
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
         '<defs><pattern id="g" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0V24" fill="none" stroke="#232D3A" stroke-opacity=".45"/></pattern>',
         '<marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10z" fill="#6B7889"/></marker>',
         '<marker id="ai" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0 0L10 5L0 10z" fill="#4ADE80"/></marker></defs>',
         f'<rect width="{W}" height="{H}" rx="24" fill="{BG}"/><rect width="{W}" height="{H}" rx="24" fill="url(#g)"/>']

    def box(x, y, w, h, title, sub, color, fill=SURF, r=12, size=17):
        o.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{color}" stroke-opacity=".75" stroke-width="1.5"/>')
        cy = y + h / 2 - (9 if sub else 0)
        o.append(f'<text x="{x + w / 2}" y="{cy + 6}" text-anchor="middle" font-family="{mono}" font-size="{size}" font-weight="600" fill="{TX}">{title}</text>')
        if sub:
            o.append(f'<text x="{x + w / 2}" y="{cy + 28}" text-anchor="middle" font-family="{sans}" font-size="13" fill="{TX2}">{sub}</text>')

    def group(x, y, w, h, label, color):
        o.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="18" fill="#0D131A" stroke="{LINE}" stroke-width="1.5"/>')
        o.append(f'<circle cx="{x + 24}" cy="{y + 26}" r="5" fill="{color}"/><text x="{x + 38}" y="{y + 31}" font-family="{mono}" font-size="13" letter-spacing="1.5" fill="{MUTED}">{label}</text>')

    def line(x1, y1, x2, y2, dashed=False, color=MUTED, marker="a"):
        d = ' stroke-dasharray="6 6"' if dashed else ""
        o.append(f'<path d="M{x1} {y1}L{x2} {y2}" stroke="{color}" stroke-width="1.6"{d} marker-end="url(#{marker})"/>')

    o.append(f'<text x="40" y="52" font-family="{sans}" font-size="22" font-weight="700" fill="{TX}">Module graph</text>')
    o.append(f'<text x="196" y="52" font-family="{mono}" font-size="14" fill="{MUTED}">21 Gradle modules · arrows point at dependencies</text>')
    box(420, 80, 360, 64, "app", "composition root · Hilt · Navigation 3", PRIMARY, fill="#16243A")

    group(40, 184, 1120, 214, "FEATURE/*  ·  PRESENTATION → DOMAIN", PRIMARY)
    feats = [("battlespace", PRIMARY), ("intel", AI), ("plan", OK), ("mission", WARN), ("sync", CRIT)]
    fw, fg = 196, 22
    for i, (n, c) in enumerate(feats):
        x = 64 + i * (fw + fg)
        o.append(f'<text x="{x + fw / 2}" y="{252}" text-anchor="middle" font-family="{sans}" font-size="16" font-weight="600" fill="{c}">{n}</text>')
        box(x, 266, fw, 46, "presentation", None, c, size=14, r=10)
        line(x + fw / 2, 312, x + fw / 2, 334)
        box(x, 336, fw, 42, "domain", None, c, fill="#1B1530", size=14, r=10)
    line(600, 144, 600, 184)

    gy, gh = 462, 280
    group(40, gy, 300, gh, "UI KIT  ·  ANDROID", PRIMARY)
    box(64, gy + 54, 252, 58, "core:designsystem", "tokens · type · components", PRIMARY)
    box(64, gy + 126, 252, 58, "core:ui", "TacticalMap · graph · symbols", PRIMARY)
    box(64, gy + 198, 252, 58, "core:navigation", "typed routes", PRIMARY)

    group(364, gy, 472, gh, "PURE KOTLIN  ·  JVM", AI)
    box(388, gy + 54, 424, 58, "core:domain", "9 ports + shared use cases", AI, fill="#1B1530")
    box(388, gy + 126, 424, 58, "core:algorithms", "NLP · fusion · spatial index · A* planning · geo", AI, fill="#1B1530")
    box(388, gy + 198, 204, 58, "core:model", "pure data", AI, fill="#1B1530")
    box(608, gy + 198, 204, 58, "core:common", "clock · dispatchers", AI, fill="#1B1530")

    group(860, gy, 300, gh, "ADAPTERS  ·  ON-DEVICE", OK)
    box(884, gy + 54, 252, 58, "core:data", "repositories · runtime · sync", OK, fill="#10261A")
    box(884, gy + 126, 252, 58, "core:database", "ObjectBox · HNSW · outbox", OK, fill="#10261A")
    box(884, gy + 198, 252, 58, "core:simulation", "UAS · feeds · mirror · link", OK, fill="#10261A")

    line(300, 398, 220, gy)
    line(600, 398, 600, gy)
    o.append(f'<path d="M780 112H1180V{gy + 83}H1138" fill="none" stroke="{MUTED}" stroke-width="1.6" marker-end="url(#a)"/>')
    line(884, gy + 83, 814, gy + 83, dashed=True, color=OK, marker="ai")
    o.append(f'<text x="849" y="{gy + 74}" text-anchor="middle" font-family="{mono}" font-size="11" fill="{OK}">impl</text>')
    o.append(f'<text x="40" y="{H - 18}" font-family="{mono}" font-size="12" fill="{MUTED}">feature modules see only ports  ·  core:data implements them  ·  app wires both through Hilt</text>')
    o.append("</svg>")
    return "\n".join(o)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    hero().save(os.path.join(OUT, "hero.png"), optimize=True)
    brand().save(os.path.join(OUT, "brand.png"), optimize=True)
    open(os.path.join(OUT, "modules.svg"), "w").write(modules())
    print("wrote", OUT)

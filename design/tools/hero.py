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


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    hero().save(os.path.join(OUT, "hero.png"), optimize=True)
    brand().save(os.path.join(OUT, "brand.png"), optimize=True)
    print("wrote", OUT)

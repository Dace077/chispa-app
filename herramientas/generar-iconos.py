"""
Genera TODOS los iconos de Chispa a partir de una sola definicion.

    python herramientas/generar-iconos.py

Produce:
  app/src/main/res/mipmap-*/ic_launcher_foreground.png   (adaptativo, 108dp)
  app/src/main/res/mipmap-*/ic_launcher_monochrome.png   (tema dinamico A13+)
  app/src/main/res/mipmap-*/ic_launcher.png              (legacy, squircle)
  app/src/main/res/mipmap-*/ic_launcher_round.png        (legacy, circulo)
  play/graficos/icono-512.png                            (ficha de Play)
  play/graficos/destacado-1024x500.png                   (grafico destacado)

Por que un script y no un PNG suelto en el repo: son 22 archivos que tienen que
mantenerse coherentes entre si. Cambiar el pico o mover una letra a mano en 22
sitios acaba, sin falta, en un icono distinto en una densidad.

NOTA SOBRE EL FORMATO. El primer plano va en PNG y no en vector drawable porque
lleva texto, y los vector drawable de Android no saben dibujar texto: solo
paths. Convertir las letras a curvas daria un XML enorme e ilegible. El PNG a
432x432 (xxxhdpi) sobra para cualquier pantalla.

TIPOGRAFIA. DejaVu Sans Bold, de licencia libre y redistribuible. Se descarto
Segoe UI Black a proposito: es de Microsoft y su licencia no ampara rasterizarla
dentro del logotipo de un producto que se publica.
"""
import math
import os
import re
import sys
from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(RAIZ, "app", "src", "main", "res")
PLAY = os.path.join(RAIZ, "play", "graficos")

FUENTE = os.path.join(
    os.path.dirname(sys.executable), "..", "Lib", "site-packages",
    "matplotlib", "mpl-data", "fonts", "ttf", "DejaVuSans-Bold.ttf"
)
if not os.path.exists(FUENTE):
    import matplotlib
    FUENTE = os.path.join(os.path.dirname(matplotlib.__file__),
                          "mpl-data", "fonts", "ttf", "DejaVuSans-Bold.ttf")

V = 108          # lienzo del icono adaptativo
VISIBLE = 72     # ...del que el launcher solo ensena esto
MARGEN = (V - VISIBLE) / 2
SS = 8           # supersampling

VIOLETA_0, VIOLETA_1 = (0x3A, 0x2B, 0xB5), (0x1A, 0x11, 0x45)
AMBAR_0, AMBAR_1 = (0xFF, 0xC9, 0x4D), (0xFF, 0x6B, 0x5A)
AMBAR = (0xFF, 0xB0, 0x20, 255)
TEAL = (0x3E, 0xD0, 0xBC, 255)
BLANCO = (255, 255, 255, 255)
TINTA = (0x1A, 0x11, 0x45, 255)

# ---------------------------------------------------------------------------
#  Geometria del colibri. Coordenadas en el lienzo de 108.
# ---------------------------------------------------------------------------
CUERPO = ("M26,52 C26,40 36,31 49,31 C61,31 69,38 69,47 C69,60 57,69 44,69 "
          "C33,69 26,62 26,52 Z")
PICO = "M66,40 L87,48 L66,46.5 Z"
ALA = "M53,55 C46,45 38,35 30,30 C28,43 34,55 46,62 Z"
OJO = ("M56,38 C60,38 64,42 64,46.5 C64,51 60,55 56,55 C51,55 48,51 48,46.5 "
       "C48,42 51,38 56,38 Z")
BRILLO = ("M58,42 C60,42 61,43.5 61,45 C61,46.5 60,48 58,48 C56,48 55,46.5 "
          "55,45 C55,43.5 56,42 58,42 Z")

# El ave, encogida y subida para dejar sitio al letrero debajo.
#
# El conjunto (ave + dos lineas) va centrado en la VENTANA VISIBLE, no en el
# lienzo de 108: ocupa de y=28 a y=80, cuyo centro es 54. Cuadrarlo respecto al
# lienzo completo dejaba el logotipo alto y un palmo de aire muerto abajo.
AVE = (0.46, 28.0, 13.7)

TITULO = "SPEAK"
SUBTITULO = "ENGLISH"

#: Las dos lineas se ajustan al MISMO ancho. Es lo que hace que un logotipo
#: apilado se lea como una pieza y no como dos textos sueltos.
ANCHO_LETRERO = 52
Y_TITULO = 59
Y_SUBTITULO = 74


# ---------------------------------------------------------------------------
#  Mini renderizador de pathData (M/L/C/Q/Z)
# ---------------------------------------------------------------------------
def _bez3(p0, p1, p2, p3, n=28):
    return [(
        (1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t*t*p2[0] + t**3*p3[0],
        (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t*t*p2[1] + t**3*p3[1],
    ) for t in [i/n for i in range(1, n+1)]]


def parse_path(d):
    tokens = re.findall(r'[MLCQZmlcqz]|-?\d*\.?\d+', d)
    subpaths, pts, cur, start, i, cmd = [], [], (0., 0.), (0., 0.), 0, None
    while i < len(tokens):
        t = tokens[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in 'Zz':
                if pts:
                    subpaths.append(pts); pts = []
                cur = start
                continue
        need = {'M': 2, 'L': 2, 'C': 6, 'Q': 4}[cmd.upper()]
        nums = [float(tokens[i+k]) for k in range(need)]
        i += need
        if cmd.upper() == 'M':
            if pts:
                subpaths.append(pts)
            cur = start = (nums[0], nums[1]); pts = [cur]
        elif cmd.upper() == 'L':
            cur = (nums[0], nums[1]); pts.append(cur)
        elif cmd.upper() == 'C':
            p3 = (nums[4], nums[5])
            pts += _bez3(cur, (nums[0], nums[1]), (nums[2], nums[3]), p3)
            cur = p3
    if pts:
        subpaths.append(pts)
    return subpaths


def _degradado(px, c0, c1):
    img = Image.new('RGB', (px, px))
    p = img.load()
    for y in range(px):
        for x in range(px):
            t = min(1.0, max(0.0, (x + y) / (2.0 * px)))
            p[x, y] = tuple(int(c0[k] + (c1[k]-c0[k])*t) for k in range(3))
    return img


def pinta(base, d, color=None, grad=None, xf=None):
    px = base.size[0]
    mask = Image.new('L', (px, px), 0)
    md = ImageDraw.Draw(mask)
    for sp in parse_path(d):
        if len(sp) < 3:
            continue
        if xf:
            s, dx, dy = xf
            sp = [(x*s+dx, y*s+dy) for x, y in sp]
        md.polygon([(x/V*px, y/V*px) for x, y in sp], fill=255)
    capa = _degradado(px, *grad).convert('RGBA') if grad else Image.new('RGBA', (px, px), color)
    base.paste(capa, (0, 0), mask)


def _fuente_que_cabe(texto, ancho_objetivo, px):
    """Busca el cuerpo mas grande que cabe en `ancho_objetivo` (unidades de 108)."""
    objetivo_px = ancho_objetivo / V * px
    tam = 4
    while tam < 400:
        f = ImageFont.truetype(FUENTE, tam)
        if f.getbbox(texto)[2] - f.getbbox(texto)[0] > objetivo_px:
            return ImageFont.truetype(FUENTE, max(4, tam - 1))
        tam += 1
    return ImageFont.truetype(FUENTE, tam)


def dibuja_primer_plano(px, mono=False):
    """El ave + el letrero, sobre transparente, en el lienzo completo de 108."""
    img = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    if mono:
        pinta(img, ALA, BLANCO, xf=AVE)
        pinta(img, CUERPO, BLANCO, xf=AVE)
        pinta(img, PICO, BLANCO, xf=AVE)
    else:
        pinta(img, ALA, TEAL, xf=AVE)
        pinta(img, CUERPO, BLANCO, xf=AVE)
        pinta(img, PICO, AMBAR, xf=AVE)
        pinta(img, OJO, TINTA, xf=AVE)
        pinta(img, BRILLO, BLANCO, xf=AVE)

    d = ImageDraw.Draw(img)
    d.text((54/V*px, Y_TITULO/V*px), TITULO, anchor="mm",
           font=_fuente_que_cabe(TITULO, ANCHO_LETRERO, px),
           fill=BLANCO if mono else AMBAR)
    d.text((54/V*px, Y_SUBTITULO/V*px), SUBTITULO, anchor="mm",
           font=_fuente_que_cabe(SUBTITULO, ANCHO_LETRERO, px), fill=BLANCO)

    if mono:
        # El ojo se perfora para que la silueta no sea una mancha ciega.
        hueco = Image.new('L', (px, px), 255)
        hd = ImageDraw.Draw(hueco)
        for sp in parse_path(OJO):
            s, dx, dy = AVE
            hd.polygon([((x*s+dx)/V*px, (y*s+dy)/V*px) for x, y in sp], fill=0)
        alpha = img.getchannel('A')
        img.putalpha(Image.composite(alpha, Image.new('L', (px, px), 0), hueco))
    return img


def dibuja_fondo(px):
    img = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    pinta(img, f'M0,0 L{V},0 L{V},{V} L0,{V} Z', grad=(VIOLETA_0, VIOLETA_1))
    return img


def _mascara(px, forma):
    m = Image.new('L', (px*4, px*4), 0)
    d = ImageDraw.Draw(m)
    if forma == 'circulo':
        d.ellipse([0, 0, px*4-1, px*4-1], fill=255)
    else:  # squircle (superelipse n=4)
        r = px*4/2
        for y in range(px*4):
            dy = abs(y - r + .5)/r
            if dy > 1:
                continue
            dx = (1 - dy**4)**.25
            d.line([(r-dx*r, y), (r+dx*r, y)], fill=255)
    return m.resize((px, px), Image.LANCZOS)


def icono_completo(px, forma):
    """Icono final: fondo + primer plano, recortado a la ventana visible."""
    s = px * SS
    lienzo = int(s * V / VISIBLE)
    img = dibuja_fondo(lienzo)
    img.alpha_composite(dibuja_primer_plano(lienzo))
    k = lienzo / V
    img = img.crop((int(MARGEN*k), int(MARGEN*k),
                    int((MARGEN+VISIBLE)*k), int((MARGEN+VISIBLE)*k)))
    img = img.resize((px, px), Image.LANCZOS)
    if forma:
        img.putalpha(_mascara(px, forma))
    return img


def guardar(img, *ruta):
    destino = os.path.join(*ruta)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    img.save(destino)
    return os.path.relpath(destino, RAIZ)


DENSIDADES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def transformar_path(d, xf):
    """
    Reescribe un pathData aplicando (escala, dx, dy), conservando las curvas.

    Sirve para emitir el icono de notificacion como vector a partir de los
    MISMOS paths que el icono grande. Escribirlo a mano en otro sitio es
    garantizar que un dia el pico cambie en un icono y en el otro no.
    """
    s, dx, dy = xf

    def rep(m):
        x, y = float(m.group(1)), float(m.group(2))
        return f"{x*s+dx:.2f},{y*s+dy:.2f}"

    return re.sub(r'(-?\d*\.?\d+),(-?\d*\.?\d+)', rep, d).strip()


def _caja(paths):
    """Caja que envuelve a un conjunto de paths."""
    xs, ys = [], []
    for d in paths:
        for sp in parse_path(d):
            for x, y in sp:
                xs.append(x)
                ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def _encuadrar(paths, destino, margen):
    """
    Transformacion que mete `paths` centrados dentro de un lienzo cuadrado.

    Se calcula de la caja real de los paths en vez de a ojo: el encuadre del
    icono de launcher NO sirve aqui, porque alli el ave esta subida para dejar
    sitio al letrero y al reescalarla saldria diminuta y pegada al borde.
    """
    x0, y0, x1, y1 = _caja(paths)
    util = destino - 2 * margen
    s = util / max(x1 - x0, y1 - y0)
    tx = margen - x0 * s + (util - (x1 - x0) * s) / 2
    ty = margen - y0 * s + (util - (y1 - y0) * s) / 2
    return s, tx, ty


def escribir_icono_notificacion():
    """
    Icono de notificacion: silueta blanca sobre transparente, como exige
    Android (el sistema tira el color y se queda con el alfa).

    Va el colibri a secas, sin el letrero: a 24dp "SPEAK ENGLISH" seria una
    mancha gris, y una notificacion se mira de reojo en la barra de estado.
    """
    xf = _encuadrar([ALA, CUERPO, PICO], destino=24, margen=1.5)
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  GENERADO POR herramientas/generar-iconos.py — no editar a mano.

  Silueta del colibri, sin el letrero del icono de launcher: a 24dp el texto
  no se leeria. Las coordenadas salen de los mismos paths que el icono grande,
  reencuadradas para llenar el lienzo de 24.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFFFF">

    <!-- Ala -->
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{transformar_path(ALA, xf)}" />

    <!-- Cuerpo con el ojo perforado (evenOdd deja el hueco) -->
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{transformar_path(CUERPO, xf)} {transformar_path(OJO, xf)}" />

    <!-- Pico de aguja -->
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{transformar_path(PICO, xf)}" />

</vector>
'''
    destino = os.path.join(RES, "drawable", "ic_notification.xml")
    with open(destino, "w", encoding="utf-8") as f:
        f.write(xml)
    return os.path.relpath(destino, RAIZ)

if __name__ == "__main__":
    hechos = []

    for dpi, factor in DENSIDADES.items():
        carpeta = os.path.join(RES, f"mipmap-{dpi}")

        # Adaptativo: el primer plano ocupa los 108dp completos.
        px108 = int(108 * factor)
        fg = dibuja_primer_plano(px108 * SS).resize((px108, px108), Image.LANCZOS)
        hechos.append(guardar(fg, carpeta, "ic_launcher_foreground.png"))

        mono = dibuja_primer_plano(px108 * SS, mono=True).resize((px108, px108), Image.LANCZOS)
        hechos.append(guardar(mono, carpeta, "ic_launcher_monochrome.png"))

        # Legacy (Android 7 y 7.1, que no tienen icono adaptativo).
        px48 = int(48 * factor)
        hechos.append(guardar(icono_completo(px48, 'squircle'), carpeta, "ic_launcher.png"))
        hechos.append(guardar(icono_completo(px48, 'circulo'), carpeta, "ic_launcher_round.png"))

    # Ficha de Play: 512x512 sin mascara (Play redondea por su cuenta).
    hechos.append(guardar(icono_completo(512, None).convert('RGB'), PLAY, "icono-512.png"))

    # Grafico destacado 1024x500.
    W, H = 1024, 500
    destacado = _degradado(max(W, H), VIOLETA_0, VIOLETA_1).resize((W, H)).convert('RGB')
    marca = icono_completo(300, 'squircle')
    destacado.paste(marca, (70, 100), marca)
    d = ImageDraw.Draw(destacado)
    d.text((410, 170), "Chispa", font=ImageFont.truetype(FUENTE, 92), fill=(255, 255, 255))
    d.text((414, 288), "SPEAK ENGLISH", font=ImageFont.truetype(FUENTE, 40), fill=AMBAR[:3])
    d.text((416, 350), "Gratis, sin anuncios y sin internet",
           font=ImageFont.truetype(FUENTE, 27), fill=(0xC9, 0xC2, 0xFB))
    hechos.append(guardar(destacado, PLAY, "destacado-1024x500.png"))

    # Icono de notificación, como vector y desde los mismos paths.
    hechos.append(escribir_icono_notificacion())

    print(f"{len(hechos)} archivos generados:")
    for h in hechos:
        print("  ", h)

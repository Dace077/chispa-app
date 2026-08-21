"""
Audita la coherencia de TODOS los ejercicios del curso.

    python herramientas/auditar-ejercicios.py            # informe
    python herramientas/auditar-ejercicios.py --csv      # volcado para revisar

Busca lo que `validar-contenido.ps1` no puede ver: ese script comprueba que un
ejercicio no se rompa al cargar; este comprueba que **tenga sentido**.

El fallo que lo motivo: preguntas cuyo enunciado pide el significado en espanol
y cuyas opciones estan en ingles. El alumno responde bien y la app le dice que
fallo, o peor, aprende algo falso. Un ejercicio asi no rompe el build, no falla
ningun test y no lo detecta el validador: se sirve tan tranquilo.

QUE COMPRUEBA
  1. Desajuste de idioma entre lo que se pide y lo que se responde.
  2. Opciones de idiomas mezclados: si la correcta es la unica en espanol,
     se acierta sin saber ingles.
  3. `direction` que miente (hace que se lea en voz alta el idioma que no es).
  4. Opciones duplicadas de hecho, o mas de una defendible como correcta.
  5. Enunciados vacios o ambiguos ("¿Cual es correcta?" sin contexto).
"""
import json
import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
import unicodedata

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content")

# ---------------------------------------------------------------------------
#  Deteccion de idioma
#
#  No hace falta un modelo: con textos de curso, unas listas de palabras
#  funcionales y unos marcadores ortograficos bastan y son auditables.
# ---------------------------------------------------------------------------
ES_MARCAS = set("ñáéíóúü¿¡")
ES_PALABRAS = {
    "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al",
    "que", "es", "son", "está", "estan", "están", "esta", "soy", "eres", "somos",
    "en", "por", "para", "con", "sin", "sobre", "como", "cómo", "cuando", "cuándo",
    "donde", "dónde", "porque", "pero", "muy", "más", "mas", "yo", "tú", "tu",
    "él", "ella", "nosotros", "ellos", "ellas", "usted", "mi", "su", "se", "le",
    "lo", "me", "te", "nos", "hay", "tiene", "tengo", "tienes", "hacer", "hace",
    "ser", "estar", "ir", "voy", "vas", "va", "vamos", "van", "quiero", "quieres",
    "puedo", "puedes", "gracias", "buenos", "buenas", "días", "dias", "noches",
    "tarde", "señor", "señora", "qué", "quién", "cuál", "cuáles", "significa",
    "traduce", "elige", "completa", "escribe", "ordena", "no", "sí", "también",
}
EN_PALABRAS = {
    "the", "a", "an", "is", "are", "was", "were", "am", "be", "been", "being",
    "of", "to", "in", "on", "at", "for", "with", "from", "by", "about",
    "i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them",
    "my", "your", "his", "its", "our", "their", "this", "that", "these", "those",
    "do", "does", "did", "have", "has", "had", "will", "would", "can", "could",
    "should", "must", "may", "might", "there", "here", "what", "who", "which",
    "when", "where", "why", "how", "and", "or", "but", "not", "very", "some",
    "good", "morning", "thank", "thanks", "welcome", "please", "sorry", "yes",
}
# Palabras que existen igual en los dos idiomas: no cuentan para nada.
AMBIGUAS = {"no", "a", "me", "son", "en", "un", "e", "o", "y", "si", "he", "van", "sin", "as", "la", "mi", "tu", "su", "ella", "ha"}


def normaliza(t):
    return re.sub(r"[^\w\s']", " ", t.lower(), flags=re.UNICODE)


# Lexicos construidos con el vocabulario DEL PROPIO CURSO (~930 pares en/es
# declarados en los `vocab` de las lecciones y en las lecturas). Es mucho mas
# fiable que adivinar por terminaciones: la primera version daba "drawer" por
# espanol porque acaba en -er, como un infinitivo.
EN_LEX, ES_LEX = set(), set()


def _cargar_lexicos():
    def anota(destino, frase):
        for w in normaliza(frase or "").split():
            if len(w) > 1:
                destino.add(w)

    for archivo in os.listdir(CONTENIDO):
        if not archivo.endswith(".json"):
            continue
        try:
            d = json.load(open(os.path.join(CONTENIDO, archivo), encoding="utf-8"))
        except Exception:
            continue

        for u in d.get("units", []):
            for l in u.get("lessons", []):
                for v in l.get("vocab", []):
                    anota(EN_LEX, v.get("en"))
                    anota(ES_LEX, v.get("es"))
        for r in d.get("readings", []):
            for s in r.get("sentences", []):
                anota(EN_LEX, s.get("en"))
                anota(ES_LEX, s.get("es"))

    EN_LEX.update(EN_PALABRAS)
    ES_LEX.update(ES_PALABRAS)
    # Lo que aparece en los dos lados no distingue nada.
    comunes = EN_LEX & ES_LEX
    EN_LEX.difference_update(comunes)
    ES_LEX.difference_update(comunes)


def idioma(texto):
    """Devuelve 'es', 'en' o '?'. Ante la duda, '?': un falso positivo cuesta
    mas que un falso negativo, porque enterraria los hallazgos de verdad."""
    if not texto or not texto.strip():
        return "?"
    t = texto.strip()
    if any(c in ES_MARCAS for c in t.lower()):
        return "es"

    palabras = [w for w in normaliza(t).split() if w]
    if not palabras:
        return "?"
    utiles = [w for w in palabras if w not in AMBIGUAS]

    es = sum(1 for w in utiles if w in ES_PALABRAS) * 2 + sum(1 for w in utiles if w in ES_LEX)
    en = sum(1 for w in utiles if w in EN_PALABRAS) * 2 + sum(1 for w in utiles if w in EN_LEX)

    if es > en:
        return "es"
    if en > es:
        return "en"
    return "?"


def mayoria(textos):
    ids = [idioma(t) for t in textos]
    es, en = ids.count("es"), ids.count("en")
    if es > en:
        return "es"
    if en > es:
        return "en"
    return "?"


# ---------------------------------------------------------------------------
#  Que idioma PIDE el enunciado
# ---------------------------------------------------------------------------
PIDE_ES = [
    r"qué significa", r"que significa", r"significado de", r"cómo se traduce",
    r"traduce al español", r"traducción al español",
]
PIDE_EN = [
    r"cómo se dice", r"como se dice", r"traduce al inglés",
    r"di en inglés", r"escribe en inglés",
]
#: Preguntas SOBRE el ingles, no EN ingles. "¿Como se pronuncia la letra E en
#: ingles?" se responde en espanol; la primera version lo marcaba como fallo por
#: ver "en ingles" en el enunciado.
METALINGUISTICO = re.compile(
    r"(cómo se pronuncia|cómo suena|cuántas sílabas|dónde cae el acento|por qué)",
    re.IGNORECASE,
)


def pide(prompt):
    """Idioma que el enunciado espera como respuesta, o None si no se declara."""
    if not prompt:
        return None
    if METALINGUISTICO.search(prompt):
        return None
    p = prompt.lower()
    for r in PIDE_EN:
        if re.search(r, p):
            return "en"
    for r in PIDE_ES:
        if re.search(r, p):
            return "es"
    return None


# ---------------------------------------------------------------------------
#  Auditoria
# ---------------------------------------------------------------------------
class Hallazgo:
    def __init__(self, archivo, leccion, idx, tipo, gravedad, motivo, detalle=""):
        self.archivo, self.leccion, self.idx = archivo, leccion, idx
        self.tipo, self.gravedad, self.motivo, self.detalle = tipo, gravedad, motivo, detalle

    def __str__(self):
        return (f"[{self.gravedad}] {self.archivo} · {self.leccion} · #{self.idx} ({self.tipo})\n"
                f"    {self.motivo}\n    {self.detalle}")


def _claves_duplicadas(ruta):
    """
    Claves repetidas dentro de un mismo objeto JSON.

    `json.load` se queda con la última y no dice nada, así que un
    "grammarTopicId" escrito dos veces borra el primero en silencio. Pasó de
    verdad al etiquetar temas de gramática: la lección ya tenía uno y el nuevo
    quedó tapado sin que fallara ni el parseo ni el validador.
    """
    repetidas = []

    def revisar(pares):
        vistas = set()
        for k, _ in pares:
            if k in vistas:
                repetidas.append(k)
            vistas.add(k)
        return dict(pares)

    with open(ruta, encoding="utf-8") as f:
        json.load(f, object_pairs_hook=revisar)
    return repetidas


def _auditar_kids(hallazgos):
    """
    Chispa Kids: que no haya dos palabras con el mismo dibujo.

    Paso de verdad: "girl" y "sister" salieron las dos con el mismo emoji. Si
    la voz dice "sister" y en pantalla hay dos dibujos identicos, el nino no
    puede acertar salvo por suerte, y como aqui no hay texto que lo aclare, no
    tiene forma de entender por que falla.
    """
    ruta = os.path.join(CONTENIDO, "kids.json")
    if not os.path.exists(ruta):
        return
    datos = json.load(open(ruta, encoding="utf-8"))
    for mundo in datos.get("worlds", []):
        vistos = {}
        for it in mundo.get("items", []):
            arte = it.get("art", "")
            if not arte:
                hallazgos.append(Hallazgo(
                    "kids.json", mundo.get("id", "?"), 0, it.get("kind", "-"), "GRAVE",
                    "Elemento sin dibujo",
                    f"{it.get('id')} no tiene 'art'"
                ))
                continue
            if arte in vistos:
                hallazgos.append(Hallazgo(
                    "kids.json", mundo.get("id", "?"), 0, it.get("kind", "-"), "GRAVE",
                    "Dos palabras con el mismo dibujo: la pregunta no tiene respuesta",
                    f"'{it.get('en')}' y '{vistos[arte]}' usan {arte}"
                ))
            vistos[arte] = it.get("en")


def _auditar_placement(hallazgos):
    """
    El test de nivel tiene que medir, no premiar al que pulsa siempre la misma
    letra.

    Se comprobó en el emulador: con 12 de 18 respuestas en la opción A y
    ninguna en la D, pulsar siempre la primera colocaba a cualquiera en C2. Y
    eso no se puede deshacer luego, porque `retakePlacement` solo deja SUBIR de
    nivel para no cerrarle contenido a nadie.
    """
    ruta = os.path.join(CONTENIDO, "placement.json")
    if not os.path.exists(ruta):
        return
    datos = json.load(open(ruta, encoding="utf-8"))
    preguntas = datos.get("questions", [])
    reparto = {}
    for q in preguntas:
        ops = q.get("options") or []
        if q.get("answer") in ops:
            reparto[ops.index(q["answer"])] = reparto.get(ops.index(q["answer"]), 0) + 1

    if not preguntas:
        return
    esperado = len(preguntas) / 4
    # Con tan pocas preguntas no se puede exigir un reparto exacto, pero que una
    # posición doble a la media ya hace rentable adivinar.
    for pos, veces in sorted(reparto.items()):
        if veces > esperado * 1.8:
            hallazgos.append(Hallazgo(
                "placement.json", "-", pos, "-", "GRAVE",
                "La respuesta correcta se repite demasiado en la misma posición",
                f"posición {pos}: {veces} de {len(preguntas)} (lo esperable son {esperado:.0f})"
            ))
    faltan = [p for p in range(4) if reparto.get(p, 0) == 0]
    if faltan:
        hallazgos.append(Hallazgo(
            "placement.json", "-", 0, "-", "GRAVE",
            "Hay posiciones que nunca son la respuesta correcta",
            f"nunca acierta la posición {faltan}"
        ))


def auditar():
    _cargar_lexicos()
    hallazgos = []

    _auditar_placement(hallazgos)
    _auditar_kids(hallazgos)

    for archivo in sorted(os.listdir(CONTENIDO)):
        if not archivo.endswith(".json") or archivo in (
            "index.json", "placement.json", "grammar.json", "readings.json"
        ):
            continue
        ruta = os.path.join(CONTENIDO, archivo)
        for clave in _claves_duplicadas(ruta):
            hallazgos.append(Hallazgo(
                archivo, "-", 0, "-", "GRAVE",
                "Clave repetida en el JSON: se pierde el primer valor en silencio",
                f"'{clave}' aparece dos veces en el mismo objeto"
            ))
        d = json.load(open(ruta, encoding="utf-8"))

        for u in d.get("units", []):
            for l in u.get("lessons", []):
                vocab_en = {v["en"].strip().lower(): v.get("es", "").strip()
                            for v in l.get("vocab", []) if v.get("en")}

                for i, e in enumerate(l.get("exercises", [])):
                    t = e.get("type")
                    add = lambda g, m, det="": hallazgos.append(
                        Hallazgo(archivo, l["id"], i, t, g, m, det))

                    prompt = (e.get("prompt") or "").strip()
                    answer = (e.get("answer") or "").strip()
                    opts = [o.strip() for o in e.get("options", [])]

                    # ---------- OPCION MULTIPLE ----------
                    if t == "multiple_choice":
                        esperado = pide(prompt)
                        lang_opts = mayoria(opts)
                        lang_ans = idioma(answer)
                        lang_prompt = idioma(prompt)

                        # 1. El enunciado dice el idioma y las opciones no lo cumplen
                        if esperado and lang_opts != "?" and lang_opts != esperado:
                            add("GRAVE",
                                f"El enunciado pide la respuesta en '{esperado}' pero las opciones están en '{lang_opts}'",
                                f"prompt: {prompt!r}  opciones: {opts}")

                        # 2. Enunciado en ingles suelto (sin pregunta) = "que significa".
                        #    Sus opciones deberian ser espanol.
                        elif (not esperado and lang_prompt == "en"
                              and "?" not in prompt and "¿" not in prompt
                              and lang_opts == "en"):
                            add("REVISAR",
                                "Enunciado en inglés sin pregunta y opciones en inglés: no se sabe qué se pide",
                                f"prompt: {prompt!r}  opciones: {opts}")

                        # 3. Opciones de idiomas mezclados: la correcta canta
                        idiomas_opts = [idioma(o) for o in opts]
                        conocidos = [x for x in idiomas_opts if x != "?"]
                        if len(set(conocidos)) > 1 and lang_ans != "?":
                            solitaria = sum(1 for x in idiomas_opts if x == lang_ans) == 1
                            if solitaria:
                                add("GRAVE",
                                    "La respuesta correcta es la única en su idioma: se acierta sin saber inglés",
                                    f"opciones: {list(zip(opts, idiomas_opts))}  correcta: {answer!r}")
                            else:
                                add("REVISAR", "Opciones con idiomas mezclados",
                                    f"opciones: {list(zip(opts, idiomas_opts))}")

                        # 4. direction que miente
                        dirn = (e.get("direction") or "").lower()
                        if dirn.startswith("en") and lang_prompt == "es":
                            add("GRAVE",
                                "direction empieza por 'en' pero el enunciado está en español: se leería en voz alta con voz inglesa",
                                f"prompt: {prompt!r}")

                        # 5. SIN INSTRUCCION.
                        #
                        # Es el fallo que motivo esta auditoria: 99 ejercicios no
                        # decian que habia que hacer, y la app caia en "Elige la
                        # opcion correcta", que no informa de nada. El alumno veia
                        # "Tengo hambre" con cuatro frases en ingles y no sabia si
                        # le pedian traducir o corregir la gramatica.
                        hint = (e.get("hint") or "").strip()
                        autoexplica = ("?" in prompt or "¿" in prompt
                                       or re.search(r"(…|\.\.\.|:|significa)\s*$|significa", prompt))
                        if not hint and not autoexplica:
                            add("GRAVE",
                                "Sin instrucción: el enunciado no es una pregunta y no hay 'hint'",
                                f"prompt: {prompt!r}  opciones: {opts}")

                        if len({o.lower() for o in opts}) != len(opts):
                            add("REVISAR",
                                "Opciones que solo se diferencian en mayúsculas",
                                f"{opts}")

                    # ---------- TRADUCIR ----------
                    elif t == "translate":
                        dirn = (e.get("direction") or "").lower()
                        hacia_ingles = dirn != "en_es"
                        lp, la = idioma(prompt), idioma(answer)

                        if hacia_ingles and la == "es" and lp == "en":
                            add("GRAVE",
                                "Marcado como traducir AL INGLÉS pero el enunciado está en inglés y la respuesta en español",
                                f"prompt: {prompt!r} -> answer: {answer!r}")
                        elif not hacia_ingles and la == "en" and lp == "es":
                            add("GRAVE",
                                "Marcado como traducir AL ESPAÑOL pero el enunciado está en español y la respuesta en inglés",
                                f"prompt: {prompt!r} -> answer: {answer!r}")
                        elif lp != "?" and la != "?" and lp == la:
                            add("REVISAR",
                                f"Enunciado y respuesta parecen el mismo idioma ('{lp}')",
                                f"prompt: {prompt!r} -> answer: {answer!r}")

                    # ---------- HUECO ----------
                    elif t == "fill_in_blank":
                        sent = (e.get("sentence") or "")
                        if "___" not in sent:
                            add("GRAVE", "La frase no tiene el hueco '___'", f"{sent!r}")
                        if opts and answer and answer not in opts:
                            add("GRAVE", "La respuesta no está entre las opciones", f"{answer!r} no en {opts}")
                        if idioma(sent) == "es":
                            add("REVISAR", "La frase a completar parece estar en español", f"{sent!r}")

                    # ---------- ESCUCHAR Y ESCRIBIR ----------
                    elif t == "listen_and_type":
                        audio = (e.get("audioText") or answer)
                        if idioma(audio) == "es":
                            add("GRAVE", "El audio a transcribir está en español", f"{audio!r}")
                        tr = (e.get("translation") or "").strip()
                        if tr and idioma(tr) == "en":
                            add("REVISAR", "La traducción de apoyo parece estar en inglés", f"{tr!r}")

                    # ---------- PAREJAS ----------
                    elif t == "matching_pairs":
                        # Un ejercicio de parejas NO tiene por que ser una
                        # traduccion: hay de sinonimos (require/need), de formas
                        # verbales (work/worked), de colocaciones (meet/a deadline)
                        # y de contracciones (gonna/going to). Solo se exige que
                        # los dos lados esten en idiomas distintos cuando el
                        # enunciado dice que es una traduccion.
                        # "Une britanico con su equivalente" NO es traduccion:
                        # empareja ingles britanico con americano (flat/apartment).
                        es_traduccion = re.search(
                            r"(traducción|traduce|con su significado)",
                            prompt, re.IGNORECASE
                        )
                        for j, par in enumerate(e.get("pairs", [])):
                            if len(par) != 2:
                                add("GRAVE", f"Pareja #{j} no tiene dos lados", f"{par}")
                                continue
                            if not es_traduccion:
                                continue
                            a, b = idioma(par[0]), idioma(par[1])
                            if a != "?" and b != "?" and a == b:
                                add("GRAVE",
                                    f"Pareja #{j}: el enunciado pide traducción pero los dos "
                                    f"lados están en el mismo idioma ('{a}')",
                                    f"{par}")

                    # ---------- ORDENAR PALABRAS ----------
                    elif t == "word_order":
                        palabras = e.get("words", [])
                        if answer and palabras:
                            objetivo = sorted(w.lower().strip(" .,!?") for w in answer.split())
                            dadas = sorted(w.lower().strip(" .,!?") for w in palabras)
                            if objetivo != dadas:
                                add("GRAVE",
                                    "Las fichas no forman exactamente la respuesta",
                                    f"words={palabras}  answer={answer!r}")

                    # ---------- REPETIR EN VOZ ALTA ----------
                    elif t == "speak_and_repeat":
                        frase = (e.get("answer") or e.get("audioText") or prompt)
                        if idioma(frase) == "es":
                            add("GRAVE", "La frase a pronunciar está en español", f"{frase!r}")

    return hallazgos


if __name__ == "__main__":
    h = auditar()
    graves = [x for x in h if x.gravedad == "GRAVE"]
    revisar = [x for x in h if x.gravedad == "REVISAR"]

    if "--csv" in sys.argv:
        import csv
        salida = os.path.join(RAIZ, "auditoria-ejercicios.csv")
        with open(salida, "w", newline="", encoding="utf-8-sig") as f:
            w = csv.writer(f)
            w.writerow(["gravedad", "archivo", "leccion", "idx", "tipo", "motivo", "detalle"])
            for x in h:
                w.writerow([x.gravedad, x.archivo, x.leccion, x.idx, x.tipo, x.motivo, x.detalle])
        print(f"Volcado en {salida}")

    for x in graves:
        print(x); print()
    print("=" * 70)
    print(f"GRAVES:  {len(graves)}")
    print(f"REVISAR: {len(revisar)}")
    sys.exit(1 if graves else 0)

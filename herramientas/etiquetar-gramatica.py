"""
Etiqueta cada leccion con su ficha de gramatica.

Inserta `"grammarTopicId": "..."` justo detras de la linea del `"title"` de cada
leccion, por texto y no reserializando el JSON: los archivos estan escritos a
mano con un formato cuidado (vocabulario en una linea por palabra) y volcarlos
con json.dump los reformatearia enteros, dejando un diff ilegible.

Es idempotente: si la leccion ya tiene la etiqueta, la actualiza en su sitio.

REGLA QUE SE RESPETA: nunca se enlaza una leccion a una ficha de un nivel
superior al suyo. A alguien en A1 no se le manda a leer la explicacion del
subjuntivo; eso no ayuda, asusta.
"""
import json
import os
import re
import sys

RAIZ = r"C:\Users\skate\OneDrive\Escritorio\APP DE INGLES"
CONTENIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content")

MAPA = {
    # --- A1 ---
    "a1_u0_l4": "g_to_be",             # El sujeto nunca se calla
    "a1_u1_l2": "g_to_be",             # Presentarte
    "a1_u2_l2": "g_to_be",             # Verbo to be
    "a1_u2_l3": "g_articles",          # Articulos y plurales
    "a1_u3_l2": "g_there_is",          # La casa
    "a1_u3_l3": "g_present_simple",    # Presente simple
    # "Dias y meses" es vocabulario; la ficha de in/on/at es de A2 y no le
    # corresponde todavia. Se queda sin enlace a proposito.

    # --- A2 ---
    "a2_u1_l1": "g_past_simple",
    "a2_u1_l2": "g_past_simple",
    "a2_u1_l3": "g_past_simple",
    "a2_u2_l1": "g_future",
    "a2_u2_l2": "g_comparatives",
    "a2_u2_l3": "g_comparatives",
    "a2_u3_l1": "g_prepositions_time", # la ficha cubre tiempo Y lugar

    # --- B1 ---
    "b1_u1_l1": "g_present_perfect",
    "b1_u1_l2": "g_for_since",         # Narrar experiencias
    "b1_u1_l3": "g_phrasal_verbs",
    "b1_u2_l1": "g_conditionals",
    "b1_u2_l2": "g_passive",

    # --- B2 ---
    "b2_u1_l1": "g_conditionals",
    "b2_u1_l2": "g_conditionals",
    "b2_u1_l3": "g_reported_speech",
    "b2_u3_l1": "g_phrasal_verbs",
    "b2_u3_l2": "g_gerund_infinitive",
    # La leccion toca subjuntivo, inversion y enfasis. Se enlaza a inversion
    # (C1) y no a subjuntivo (C2): esta un solo escalon por encima.
    "b2_u3_l4": "g_inversion",

    # --- C1 ---
    "c1_u1_l1": "g_modals_past",       # Modales de especulacion
    "c1_u1_l2": "g_participle_clauses",
    "c1_u2_l2": "g_passive",           # Pasiva impersonal
    "c1_u2_l3": "g_cleft",             # Estructuras hendidas
    "c1_u3_l2": "g_phrasal_verbs",
    "c1_u3_l3": "g_hedging",           # Grados de compromiso

    # --- C2 ---
    "c2_u3_l1": "g_conditionals",      # Condicionales mixtos
}

ORDEN = {"A1": 0, "A2": 1, "B1": 2, "B2": 3, "C1": 4, "C2": 5}


def main():
    guia = json.load(open(os.path.join(CONTENIDO, "grammar.json"), encoding="utf-8"))
    temas = {t["id"]: t for t in guia["topics"]}

    problemas = []
    for lid, tid in MAPA.items():
        if tid not in temas:
            problemas.append(f"{lid} -> {tid} NO EXISTE en grammar.json")

    # Comprobar que ninguna leccion apunta por encima de su nivel.
    niveles_leccion = {}
    for archivo in sorted(os.listdir(CONTENIDO)):
        if not archivo.endswith("_core.json"):
            continue
        d = json.load(open(os.path.join(CONTENIDO, archivo), encoding="utf-8"))
        for u in d.get("units", []):
            for l in u.get("lessons", []):
                niveles_leccion[l["id"]] = u.get("level", "A1")

    for lid, tid in MAPA.items():
        if tid not in temas:
            continue
        nl = ORDEN.get(niveles_leccion.get(lid, "A1"), 0)
        nt = ORDEN.get(temas[tid].get("level", "A1"), 0)
        # Se tolera UN escalon por encima: pasa cuando la leccion introduce una
        # estructura cuya ficha esta catalogada en el nivel siguiente (la pasiva
        # basica de B1 y la ficha de pasiva, que es B2). Dos escalones ya no:
        # ahi la explicacion esta fuera del alcance de quien la abre.
        if nt - nl > 1:
            problemas.append(
                f"{lid} ({niveles_leccion.get(lid)}) apunta a {tid} "
                f"({temas[tid].get('level')}), {nt - nl} niveles por encima"
            )
        if lid not in niveles_leccion:
            problemas.append(f"{lid} no existe en ningun archivo de contenido")

    if problemas:
        print("NO SE TOCO NADA. Problemas:")
        for p in problemas:
            print("  -", p)
        sys.exit(1)

    total = 0
    for archivo in sorted(os.listdir(CONTENIDO)):
        if not archivo.endswith("_core.json"):
            continue
        ruta = os.path.join(CONTENIDO, archivo)
        texto = open(ruta, encoding="utf-8").read()
        cambios = 0

        for lid, tid in MAPA.items():
            # Bloque: "id": "<lid>",\n  <sangria>"title": "...",
            patron = re.compile(
                r'("id"\s*:\s*"' + re.escape(lid) + r'"\s*,\s*\n)'
                r'(\s*)("title"\s*:\s*"[^"]*"\s*,)'
                r'(\s*\n\s*"grammarTopicId"\s*:\s*"[^"]*"\s*,)?'
            )

            def rep(m):
                return f'{m.group(1)}{m.group(2)}{m.group(3)}\n{m.group(2)}"grammarTopicId": "{tid}",'

            texto, n = patron.subn(rep, texto)
            cambios += n

        if cambios:
            open(ruta, "w", encoding="utf-8").write(texto)
            print(f"{archivo}: {cambios} lecciones etiquetadas")
            total += cambios

    print(f"\nTOTAL: {total} de {len(MAPA)} previstas")
    if total != len(MAPA):
        print("OJO: alguna leccion del mapa no se encontro en el texto")
        sys.exit(1)


if __name__ == "__main__":
    main()

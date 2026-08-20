"""
Valida TODOS los simulacros del TOEFL.

    python herramientas/validar-simulacros.py

Un simulacro con un fallo no es como una leccion con un fallo. El alumno mira
el puntaje y decide con el: si se presenta al examen, si paga la cuota, si
espera otro semestre. Un examen mal calibrado le da un numero falso y toma esa
decision con el.

QUE COMPRUEBA

  1. Cuenta exacta por seccion: 50 / 40 / 50. Si falta una, la app ni lo sirve.
  2. Reparto de la respuesta correcta. Es lo mas facil de romper y lo mas
     dificil de ver: en la primera version del simulacro 1, el 69 % de las
     respuestas eran la B. Marcando siempre B se sacaba un 69 %.
  3. Opciones duplicadas o vacias.
  4. Que toda pregunta traiga explicacion: sin ella el simulacro no ensena.
  5. Preguntas repetidas ENTRE examenes: diez simulacros que comparten
     preguntas son menos de diez simulacros.
  6. En error_id, que el enunciado marque los cuatro fragmentos (A)(B)(C)(D)
     y que coincidan con las opciones.
"""
import collections
import json
import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content")

ESPERADO = {"listening": 50, "structure": 40, "reading": 50}
TOTAL = sum(ESPERADO.values())

#: Cuanto puede desviarse el reparto de respuestas del 25 % ideal.
#: 8 puntos sobre 140 preguntas es ruido; mas que eso ya es un patron
#: aprovechable por alguien que no sepa ingles.
TOLERANCIA = 8


def cargar():
    indice = os.path.join(CONTENIDO, "toefl_examenes.json")
    if not os.path.exists(indice):
        return []
    ids = json.load(open(indice, encoding="utf-8")).get("files", [])
    examenes = []
    for eid in ids:
        ruta = os.path.join(CONTENIDO, f"{eid}.json")
        if not os.path.exists(ruta):
            examenes.append((eid, None))
            continue
        examenes.append((eid, json.load(open(ruta, encoding="utf-8"))))
    return examenes


def preguntas(examen):
    for s in examen.get("sections", []):
        for p in s.get("parts", []):
            for q in p.get("questions", []):
                yield s["id"], p, q


def validar():
    problemas = []
    examenes = cargar()
    if not examenes:
        print("No hay simulacros declarados en toefl_examenes.json")
        return []

    vistos_global = {}

    for eid, examen in examenes:
        if examen is None:
            problemas.append(f"[{eid}] declarado en el indice pero el archivo no existe")
            continue

        # --- 1. cuenta por seccion ---
        cuenta = collections.Counter()
        for sid, _p, _q in preguntas(examen):
            cuenta[sid] += 1
        for sid, n in ESPERADO.items():
            if cuenta.get(sid, 0) != n:
                problemas.append(
                    f"[{eid}] seccion {sid}: {cuenta.get(sid, 0)} preguntas, se esperaban {n}"
                )

        letras = collections.Counter()
        ids_locales = collections.Counter()

        for sid, parte, q in preguntas(examen):
            qid = q.get("id", "?")
            ids_locales[qid] += 1
            opts = q.get("options", [])
            resp = q.get("answer", -1)

            # --- 3. opciones ---
            if len(opts) != 4:
                problemas.append(f"[{eid}] {qid}: {len(opts)} opciones, deben ser 4")
                continue
            if any(not str(o).strip() for o in opts):
                problemas.append(f"[{eid}] {qid}: alguna opcion esta vacia")
            if len({str(o).strip().lower() for o in opts}) != 4:
                problemas.append(f"[{eid}] {qid}: opciones duplicadas")
            if not 0 <= resp <= 3:
                problemas.append(f"[{eid}] {qid}: answer={resp} fuera de rango")
                continue
            letras[resp] += 1

            # --- 4. explicacion ---
            if not str(q.get("explanation", "")).strip():
                problemas.append(f"[{eid}] {qid}: sin explicacion")

            # --- 6. error_id bien formado ---
            if parte.get("kind") == "error_id":
                marcas = re.findall(r"\(([ABCD])\)", q.get("stem", ""))
                if marcas != ["A", "B", "C", "D"]:
                    problemas.append(
                        f"[{eid}] {qid}: el enunciado no marca (A)(B)(C)(D) en orden -> {marcas}"
                    )

            # --- 5. repetidas entre examenes ---
            firma = (q.get("stem", "").strip().lower(),
                     tuple(sorted(str(o).strip().lower() for o in opts)))
            if firma[0] and firma in vistos_global and vistos_global[firma] != eid:
                problemas.append(
                    f"[{eid}] {qid}: pregunta repetida de {vistos_global[firma]}"
                )
            vistos_global[firma] = eid

        for qid, n in ids_locales.items():
            if n > 1:
                problemas.append(f"[{eid}] id repetido dentro del examen: {qid} x{n}")

        # --- 2. reparto de respuestas ---
        ideal = TOTAL / 4
        for pos in range(4):
            desvio = abs(letras.get(pos, 0) - ideal)
            if desvio > TOLERANCIA:
                problemas.append(
                    f"[{eid}] reparto sesgado: la {'ABCD'[pos]} sale "
                    f"{letras.get(pos, 0)} veces de {TOTAL} (ideal {ideal:.0f})"
                )

        print(f"{eid}: {sum(cuenta.values())} preguntas · reparto "
              f"{ {'ABCD'[k]: v for k, v in sorted(letras.items())} }")

    return problemas


if __name__ == "__main__":
    fallos = validar()
    print()
    if fallos:
        print(f"PROBLEMAS ({len(fallos)}):")
        for f in fallos:
            print("  -", f)
        sys.exit(1)
    print("SIN PROBLEMAS: todos los simulacros estan completos y equilibrados.")

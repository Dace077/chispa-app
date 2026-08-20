"""
Pone una instruccion clara a las opciones multiples que no la tienen.

    python herramientas/poner-instrucciones.py --dry     # ver que haria
    python herramientas/poner-instrucciones.py           # aplicarlo

EL PROBLEMA. 99 de los 173 ejercicios de opcion multiple no traian ni pregunta
ni `hint`. La app entonces muestra "Elige la opcion correcta", que es cierto y
no sirve de nada: el alumno ve

    Tengo hambre
    [ I am hungry ] [ I have hungry ] [ I have hunger ] [ Me hungry ]

y no sabe si le piden traducir, corregir la gramatica o elegir la mas natural.
Peor aun al reves: ve una palabra inglesa suelta con cuatro opciones en espanol
y puede entender que le piden lo contrario de lo que le piden.

LA SOLUCION. Se rellena `hint`, que la app ya pinta como instruccion del
ejercicio (`Exercise.instruction()`), deduciendo que se pide a partir del
idioma REAL del enunciado y de las opciones -- no del campo `direction`, que en
este contenido miente en 23 casos.

Se toca solo el contenido y ni una linea de Kotlin: la instruccion es parte del
ejercicio, y ahi es donde un autor la va a buscar.
"""
import importlib.util
import json
import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content")

_spec = importlib.util.spec_from_file_location(
    "auditor", os.path.join(os.path.dirname(os.path.abspath(__file__)), "auditar-ejercicios.py"))
_aud = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_aud)
_aud._cargar_lexicos()

idioma, mayoria = _aud.idioma, _aud.mayoria

#: Enunciados que ya se explican solos aunque no lleven signo de interrogacion.
AUTOEXPLICATIVO = re.compile(r"(…|\.\.\.|:|significa|quiere decir)\s*$|significa", re.IGNORECASE)


def instruccion_para(prompt, opciones):
    """La instruccion que le falta, o None si el enunciado ya se explica."""
    p = (prompt or "").strip()
    if "?" in p or "¿" in p:
        return None
    if AUTOEXPLICATIVO.search(p):
        return None

    lp, lo = idioma(p), mayoria(opciones)

    if lo == "es" and lp in ("en", "?"):
        return "¿Qué significa?"
    if lo == "en" and lp in ("es", "?"):
        return "¿Cómo se dice en inglés?"
    if lo == "en" and lp == "en":
        return "¿Cuál es correcta?"
    return None


def main():
    dry = "--dry" in sys.argv
    cambios = []

    for archivo in sorted(os.listdir(CONTENIDO)):
        if not archivo.endswith(".json") or archivo in (
            "index.json", "placement.json", "grammar.json", "readings.json"
        ):
            continue
        ruta = os.path.join(CONTENIDO, archivo)
        texto = open(ruta, encoding="utf-8").read()
        d = json.loads(texto)
        nuevos = 0

        for u in d.get("units", []):
            for l in u.get("lessons", []):
                for i, e in enumerate(l.get("exercises", [])):
                    if e.get("type") != "multiple_choice":
                        continue
                    if (e.get("hint") or "").strip():
                        continue

                    prompt = (e.get("prompt") or "").strip()
                    opts = [o.strip() for o in e.get("options", [])]
                    ins = instruccion_para(prompt, opts)
                    if not ins:
                        continue

                    cambios.append((archivo, l["id"], i, prompt, ins))

                    if not dry:
                        # Inserta "hint" justo detras del "prompt" de ESTE ejercicio.
                        # Se localiza por el texto exacto del prompt escapado a JSON,
                        # que es unico dentro del archivo en la practica.
                        aguja = json.dumps(prompt, ensure_ascii=False)
                        patron = re.compile(
                            r'(\n(\s*)"prompt"\s*:\s*' + re.escape(aguja) + r'\s*,)'
                        )
                        reemplazo = (
                            lambda mm: f'{mm.group(1)}\n{mm.group(2)}"hint": '
                                       f'{json.dumps(ins, ensure_ascii=False)},'
                        )
                        texto, n = patron.subn(reemplazo, texto, count=1)
                        nuevos += n

        if not dry and nuevos:
            json.loads(texto)          # no escribir nunca un JSON roto
            open(ruta, "w", encoding="utf-8").write(texto)
            print(f"{archivo}: {nuevos} instrucciones añadidas")

    print()
    for a, lid, i, p, ins in cambios:
        print(f'  {lid} #{i}  {p[:46]!r:50} -> {ins!r}')
    print(f"\nTOTAL: {len(cambios)} ejercicios")
    if dry:
        print("(simulacion: no se ha escrito nada)")


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""
Escribe lecciones nuevas en los JSON de contenido sin tocar lo que ya había.

Por qué no se reserializa el archivo entero
-------------------------------------------
Se intentó, y no se puede: el estilo de los archivos está escrito a mano y no
sigue una regla mecánica. Hay arrays de textos en una línea de 161 caracteres y
otros partidos a partir de 110, según lo que le pareciera legible a quien lo
escribió. Cualquier reserialización global convertiría un cambio de veinte
líneas en un diff de mil, que es la forma más rápida de que nadie vuelva a
revisar un cambio de contenido.

Así que aquí se hace lo contrario: se genera **solo el bloque nuevo**, con el
estilo de la casa, y se inserta como texto en el array de lecciones de la
unidad indicada. Lo que ya existía se queda byte a byte como estaba.
"""
from __future__ import annotations

import json
import os
import re

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content")

# Objetos con solo estas claves caben en una línea.
CLAVES_EN_LINEA = (
    {"en", "es"}, {"en", "es", "note"}, {"en", "es", "ipa"},
    {"en", "es", "ipa", "note"}, {"speaker", "text"},
)


def _es_escalar(v) -> bool:
    return isinstance(v, (str, int, float, bool)) or v is None


def _en_linea(v) -> str:
    texto = json.dumps(v, ensure_ascii=False, separators=(", ", ": "))
    # Los objetos de una línea llevan espacio por dentro de las llaves, como
    # están escritos a mano: { "en": "red", "es": "rojo" }.
    if isinstance(v, dict) and texto != "{}":
        texto = "{ " + texto[1:-1] + " }"
    return texto


def volcar(valor, nivel: int = 0, ancho: int = 100) -> str:
    """Serializa con el estilo del contenido: compacto donde cabe."""
    sangria = "  " * nivel
    dentro = "  " * (nivel + 1)

    if isinstance(valor, dict):
        if set(valor.keys()) in CLAVES_EN_LINEA and all(_es_escalar(v) for v in valor.values()):
            return _en_linea(valor)
        if not valor:
            return "{}"
        partes = [
            f'{dentro}{json.dumps(k, ensure_ascii=False)}: {volcar(v, nivel + 1, ancho)}'
            for k, v in valor.items()
        ]
        return "{\n" + ",\n".join(partes) + "\n" + sangria + "}"

    if isinstance(valor, list):
        if not valor:
            return "[]"
        if all(_es_escalar(v) for v in valor):
            plano = _en_linea(valor)
            if len(sangria) + len(plano) <= ancho:
                return plano
            partes = [f"{dentro}{_en_linea(v)}" for v in valor]
            return "[\n" + ",\n".join(partes) + "\n" + sangria + "]"
        partes = [f"{dentro}{volcar(v, nivel + 1, ancho)}" for v in valor]
        return "[\n" + ",\n".join(partes) + "\n" + sangria + "]"

    return _en_linea(valor)


def insertar_lecciones(archivo: str, unidad_id: str, lecciones: list[dict]) -> int:
    """
    Añade [lecciones] al final del array `lessons` de [unidad_id].

    Trabaja sobre el texto: localiza la unidad, encuentra el cierre de su
    array de lecciones contando llaves y pega ahí el bloque nuevo. Devuelve
    cuántas se insertaron.
    """
    ruta = os.path.join(CONTENIDO, archivo)
    texto = open(ruta, encoding="utf-8").read()

    marca = re.search(r'"id":\s*"' + re.escape(unidad_id) + r'"', texto)
    if not marca:
        raise SystemExit(f"No se encontró la unidad {unidad_id} en {archivo}")

    inicio = texto.index('"lessons": [', marca.end())
    cursor = texto.index("[", inicio)

    # Se recorre el array contando corchetes, saltándose lo que va en cadenas.
    profundidad = 0
    i = cursor
    en_cadena = False
    escapado = False
    while i < len(texto):
        c = texto[i]
        if en_cadena:
            if escapado:
                escapado = False
            elif c == "\\":
                escapado = True
            elif c == '"':
                en_cadena = False
        elif c == '"':
            en_cadena = True
        elif c == "[":
            profundidad += 1
        elif c == "]":
            profundidad -= 1
            if profundidad == 0:
                break
        i += 1

    # `i` es el ] que cierra "lessons". Antes hay una línea con la sangría.
    fin_ultima = texto.rindex("}", cursor, i) + 1
    sangria_leccion = "        "  # dentro de units[].lessons[]

    bloques = []
    for leccion in lecciones:
        cuerpo = volcar(leccion, nivel=len(sangria_leccion) // 2)
        bloques.append(sangria_leccion + cuerpo)

    nuevo = texto[:fin_ultima] + ",\n" + ",\n".join(bloques) + texto[fin_ultima:]

    # Red de seguridad: si el resultado no es JSON válido, no se escribe nada.
    json.loads(nuevo)
    open(ruta, "w", encoding="utf-8", newline="").write(nuevo)
    return len(lecciones)

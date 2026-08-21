# -*- coding: utf-8 -*-
"""
Pasa el español del curso a español de México / neutro americano.

Por qué existe
--------------
El contenido se escribió en español peninsular y eso no es un detalle de estilo:
"voy a coger un café" es vulgar en México, y "chaqueta" también lo es en el habla
coloquial. Un alumno mexicano leyendo eso no aprende inglés, se ríe o se ofende.
Lo mismo, en menor grado, con ordenador, billete, fontanero, camarero, ascensor,
móvil, céntimo, conducir, andando y el "vale" interjección.

Cómo funciona
-------------
Reemplazo literal sobre el texto crudo de los JSON, nunca reserializando: así los
archivos conservan su formato y el diff es legible. Las reglas son cadenas
exactas y no expresiones regulares sueltas, porque "conducta", "cognitivo" y
"conductor" (el del autobús, que es correcto) no se deben tocar.

Es idempotente: pasarlo dos veces no cambia nada la segunda vez.

Uso:  python herramientas/neutralizar-espanolismos.py [--simular]
"""
from __future__ import annotations

import glob
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content")

# (antes, después). El orden importa: las frases largas van antes que las
# palabras sueltas que contienen.
REGLAS: list[tuple[str, str]] = [
    # --- coger: el que de verdad importa -----------------------------------
    ("Hoy está lluvioso, coge un paraguas.", "Hoy está lluvioso, lleva un paraguas."),
    ("Deberías coger una chaqueta, hace frío fuera.",
     "Deberías llevar una chamarra, hace frío afuera."),
    ("Échale un ojo a mi bolsa mientras cojo un café.",
     "Échale un ojo a mi bolsa mientras voy por un café."),
    ("Voy a coger un café, ¿quieres uno?", "Voy por un café, ¿quieres uno?"),
    ("Normalmente cojo el autobús, pero hoy voy andando.",
     "Normalmente tomo el autobús, pero hoy voy caminando."),
    ("Todas las mañanas cojo el autobús número 42.",
     "Todas las mañanas tomo el autobús número 42."),
    ("Cojo el autobús a las siete y cuarto.", "Tomo el autobús a las siete y cuarto."),
    ("Ah. ¿Qué tren cojo?", "Ah. ¿Qué tren tomo?"),
    ("Yo cojo la nórdica", "Yo tomo la nórdica"),
    ("O la cojo ahora", "O la tomo ahora"),
    ("Duele más cuando cojo aire.", "Duele más cuando tomo aire."),
    ("No pregunta qué puede coger.", "No pregunta qué puede llevarse."),
    ("Podría haber cogido trabajos pequeños", "Podría haber tomado trabajos pequeños"),
    ("Podría haber cogido esos trabajos.", "Podría haber tomado esos trabajos."),
    ("le vas cogiendo el gusto", "le vas tomando el gusto"),
    # take it with a grain of salt
    ("coger con pinzas", "tomar con pinzas"),
    ("cogerlo con pinzas", "tomarlo con pinzas"),
    ("Coge ese informe con pinzas.", "Toma ese informe con pinzas."),
    ("Yo cogería esas cifras con pinzas.", "Yo tomaría esas cifras con pinzas."),
    ("Coge esa noticia con pinzas", "Toma esa noticia con pinzas"),

    # --- chaqueta ----------------------------------------------------------
    ('{ "en": "jacket", "es": "chaqueta" }', '{ "en": "jacket", "es": "chamarra" }'),
    ("Su chaqueta estaba completamente mojada", "Su chamarra estaba completamente mojada"),

    # --- objetos y oficios --------------------------------------------------
    ("ordenador", "computadora"),
    ("fontanero", "plomero"),
    ("Camarero", "Mesero"),
    ("camarero", "mesero"),
    ("ascensor", "elevador"),
    ("céntimo", "centavo"),
    ("Gafas de leer", "Lentes de leer"),
    ("cargadores de móvil", "cargadores de celular"),
    ("Cargadores de móvil", "Cargadores de celular"),
    ("con el móvil", "con el celular"),
    ("desde cualquier móvil", "desde cualquier celular"),

    # --- billete ------------------------------------------------------------
    ("Quisiera un billete de ida y vuelta a Dublín.",
     "Quisiera un boleto de ida y vuelta a Dublín."),
    ('"billete, boleto"', '"boleto"'),
    ("¿Necesito otro billete?", "¿Necesito otro boleto?"),
    ("No, con el mismo vale.", "No, con el mismo boleto."),

    # --- conducir -----------------------------------------------------------
    ("El año que viene voy a aprender a conducir.",
     "El año que viene voy a aprender a manejar."),
    ("Él sabe conducir.", "Él sabe manejar."),
    ("No estoy acostumbrado a conducir por la izquierda.",
     "No estoy acostumbrado a manejar por la izquierda."),
    ("iría conduciendo", "iría manejando"),

    # --- vosotros -----------------------------------------------------------
    ('"es": "tú, usted, vosotros"', '"es": "tú, usted, ustedes"'),
    ("'you' vale para tú, usted, vosotros y ustedes",
     "'you' sirve para tú, usted y ustedes"),
    ("puede que me una a vosotros.", "puede que me una a ustedes."),

    # --- andando ------------------------------------------------------------
    ("Volvemos a casa andando despacio.", "Volvemos a casa caminando despacio."),
    ("a favor de ir andando", "a favor de ir caminando"),
    ("Se demuestra andando", "Se demuestra en la práctica"),

    # --- "vale" como interjección ------------------------------------------
    ('"Vale. Uno, por favor."', '"Está bien. Uno, por favor."'),
    ('"Vale. Puede tomar estas."', '"Está bien. Puede tomar estas."'),
    ('"Vale. Gracias. ¿Cuánto es?"', '"Está bien. Gracias. ¿Cuánto es?"'),
    ("Vale, eso no es bueno.", "Uy, eso no es bueno."),
    ("sigue siendo simplemente 'vale'", "sigue siendo simplemente 'de acuerdo'"),

    # --- dinero y trámite ---------------------------------------------------
    ("costes", "costos"),
    ("coste", "costo"),
    ("merece la pena", "vale la pena"),
    ("El coche está aparcado detrás del edificio.",
     "El coche está estacionado detrás del edificio."),
    ("Aparquemos el coche", "Estacionemos el coche"),

    # --- vivienda -----------------------------------------------------------
    ('"piso, apartamento"', '"departamento"'),
    ('"es": "piso"', '"es": "departamento"'),
    ("¿Ese sitio cerca de tu piso?", "¿Ese lugar cerca de tu departamento?"),
    ("Soy Marta, del piso 4B.", "Soy Marta, del departamento 4B."),
    ("El piso estaba en la cuarta planta.", "El departamento estaba en el cuarto piso."),
    ("Mientras vaciaba el piso de su madre", "Mientras vaciaba el departamento de su madre"),

    # --- jerga: la lección enseña jerga británica, pero se explica en
    #     español de México; "tío" y "hecho polvo" no dicen nada aquí.
    ('"hecho polvo (UK)"', '"muerto de cansancio (UK)"'),
    ("Estoy hecho polvo", "Estoy muerto de cansancio"),
    ("Aunque hecho polvo:", "Aunque muerto de cansancio:"),
    ('"colega, tío"', '"amigo, carnal"'),
    ("¿Qué tal, colega?", "¿Qué tal, amigo?"),
    ("¿te apetece una cerveza", "¿se te antoja una cerveza"),
    ("¿Te apetece una cerveza", "¿Se te antoja una cerveza"),
    ("JO: Estupendo. Nos vemos.", "JO: Excelente. Nos vemos."),

    # --- otros peninsularismos suaves --------------------------------------
    ("estaba enfadado consigo mismo", "estaba enojado consigo mismo"),
    ("hace frío fuera", "hace frío afuera"),
]


def aplicar(texto: str) -> tuple[str, list[tuple[str, int]]]:
    cambios = []
    for antes, despues in REGLAS:
        n = texto.count(antes)
        if n:
            texto = texto.replace(antes, despues)
            cambios.append((antes, n))
    return texto, cambios


def main() -> int:
    simular = "--simular" in sys.argv
    total = 0
    for ruta in sorted(glob.glob(os.path.join(CONTENIDO, "*.json"))):
        original = open(ruta, encoding="utf-8").read()
        nuevo, cambios = aplicar(original)
        if not cambios:
            continue
        nombre = os.path.basename(ruta)
        for antes, n in cambios:
            print(f"  {nombre}: {n}x  {antes[:70]}")
            total += n
        if not simular:
            open(ruta, "w", encoding="utf-8", newline="").write(nuevo)

    print(f"\n{total} sustituciones" + (" (simulacro, no se escribió nada)" if simular else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

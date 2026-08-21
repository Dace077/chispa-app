"""
Verifica TODA la cadena de migraciones de Room sin necesitar emulador.

Construye una base v1 de verdad con SQLite, le mete datos de un usuario con
progreso, aplica en orden el SQL de todas las migraciones (1->2, 2->3, ...) y
comprueba dos cosas:

  1. Que el esquema resultante es EXACTAMENTE el que Room espera en la ultima
     version exportada (mismas columnas, tipos, NOT NULL, claves primarias,
     defaults e indices). Es la misma comparacion que hace Room al abrir la
     base; si algo no cuadra, la app revienta con IllegalStateException en el
     arranque.

  2. Que el progreso del usuario sigue ahi despues de migrar.

Se parte de la v1 a proposito, y no de la penultima: quien lleve la app
instalada desde el principio recorre la cadena entera de una vez.
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

RAIZ = Path(r"C:\Users\skate\OneDrive\Escritorio\APP DE INGLES")
ESQUEMAS = RAIZ / "app/schemas/com.chispa.ingles.data.db.ChispaDatabase"
MIGRACION = RAIZ / "app/src/main/java/com/chispa/ingles/data/db/Migrations.kt"

fallos = []


def cargar(version):
    return json.loads((ESQUEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]


def sql_de(entidad, plantilla):
    return plantilla.replace("${TABLE_NAME}", entidad["tableName"])


def ultima_version():
    """La version mas alta exportada por Room."""
    return max(int(f.stem) for f in ESQUEMAS.glob("*.json"))


def sql_de_la_migracion():
    """Saca los execSQL del Kotlin, en orden de aparicion en el archivo.

    Vale para toda la cadena porque las migraciones se escriben en orden
    ascendente en Migrations.kt; si alguna vez se escriben desordenadas, esta
    comprobacion lo cazara al fallar el ALTER sobre una tabla inexistente.
    """
    texto = MIGRACION.read_text(encoding="utf-8")
    sentencias = []
    # execSQL("...") de una linea
    for m in re.finditer(r'execSQL\(\s*"((?:[^"\\]|\\.)*)"\s*\)', texto):
        sentencias.append((m.start(), m.group(1)))
    # execSQL(""" ... """.trimIndent())
    for m in re.finditer(r'execSQL\(\s*"""(.*?)"""', texto, re.S):
        sentencias.append((m.start(), m.group(1)))
    sentencias.sort()
    return [s for _, s in sentencias]


# --- 1. Levantar una base v1 tal y como la tiene un usuario real -------------
v1 = cargar(1)
con = sqlite3.connect(":memory:")
for e in v1["entities"]:
    con.execute(sql_de(e, e["createSql"]))
    for i in e.get("indices", []):
        con.execute(sql_de(e, i["createSql"]))

# Un usuario con meses de progreso encima.
con.execute(
    "INSERT INTO user_profile (id, motive, placementLevel, onboardingDone, placementDone,"
    " totalXp, dailyGoalXp, currentStreak, longestStreak, lastGoalDay, lastActiveDay,"
    " streakFreezes, lastFreezeWeek, hearts, heartsUpdatedAt, createdAt)"
    " VALUES (1,'trabajo','B1',1,1, 8450, 30, 47, 63, 20300, 20300, 2,'2026-W30', 5, 0, 1700000000000)"
)
con.execute(
    "INSERT INTO lesson_progress VALUES ('a1_l1','a1_u1','a1_core', 3, 5, 100, 60, 1700000000000)"
)
con.execute(
    "INSERT INTO srs_card VALUES ('hello','hello','hola','a1_l1','a1_u1','A1', 4, 1700000000000, 9, 1, 1700000000000, 0)"
)
con.execute("INSERT INTO daily_activity VALUES (20300, 120, 4, 60, 55, 1)")
con.execute("INSERT INTO achievement VALUES ('racha_30', 1700000000000)")
con.commit()

antes = {
    "xp": con.execute("SELECT totalXp FROM user_profile").fetchone()[0],
    "racha": con.execute("SELECT currentStreak FROM user_profile").fetchone()[0],
    "lecciones": con.execute("SELECT COUNT(*) FROM lesson_progress").fetchone()[0],
    "tarjetas": con.execute("SELECT COUNT(*) FROM srs_card").fetchone()[0],
    "logros": con.execute("SELECT COUNT(*) FROM achievement").fetchone()[0],
}

# --- 2. Aplicar la migracion -------------------------------------------------
DESTINO = ultima_version()
sentencias = sql_de_la_migracion()
print(f"Aplicando {len(sentencias)} sentencias para llegar de la v1 a la v{DESTINO}...\n")
for s in sentencias:
    try:
        con.execute(s)
    except sqlite3.Error as err:
        fallos.append(f"SQL invalido: {err}\n    {' '.join(s.split())[:120]}")
con.commit()

# --- 3. Comparar con lo que Room espera en la ultima version -----------------
v2 = cargar(DESTINO)

TIPO = {"TEXT": "TEXT", "INTEGER": "INTEGER", "REAL": "REAL", "BLOB": "BLOB"}


def normalizar_default(d):
    if d is None:
        return None
    d = d.strip()
    while d.startswith("(") and d.endswith(")"):
        d = d[1:-1].strip()
    return d.strip("'")


for e in v2["entities"]:
    tabla = e["tableName"]
    filas = con.execute(f"PRAGMA table_info(`{tabla}`)").fetchall()
    if not filas:
        fallos.append(f"[{tabla}] la tabla no existe despues de migrar")
        continue

    real = {f[1]: {"tipo": f[2], "notnull": f[3], "default": f[4], "pk": f[5]} for f in filas}

    for campo in e["fields"]:
        nombre = campo["columnName"]
        if nombre not in real:
            fallos.append(f"[{tabla}] falta la columna '{nombre}'")
            continue
        r = real[nombre]
        if r["tipo"].upper() != campo["affinity"].upper():
            fallos.append(
                f"[{tabla}.{nombre}] tipo {r['tipo']} pero Room espera {campo['affinity']}"
            )
        if bool(r["notnull"]) != bool(campo["notNull"]):
            fallos.append(
                f"[{tabla}.{nombre}] notNull={bool(r['notnull'])} pero Room espera {bool(campo['notNull'])}"
            )
        esperado = normalizar_default(campo.get("defaultValue"))
        obtenido = normalizar_default(r["default"])
        if esperado != obtenido:
            fallos.append(
                f"[{tabla}.{nombre}] default {obtenido!r} pero Room espera {esperado!r}"
            )

    sobran = set(real) - {c["columnName"] for c in e["fields"]}
    if sobran:
        fallos.append(f"[{tabla}] columnas de mas: {sorted(sobran)}")

    pk_real = [f[1] for f in sorted(filas, key=lambda x: x[5]) if f[5] > 0]
    pk_esperada = e["primaryKey"]["columnNames"]
    if pk_real != pk_esperada:
        fallos.append(f"[{tabla}] PK {pk_real} pero Room espera {pk_esperada}")

    indices_reales = {
        r[1] for r in con.execute(f"PRAGMA index_list(`{tabla}`)").fetchall()
        if not r[1].startswith("sqlite_autoindex")
    }
    for i in e.get("indices", []):
        if i["name"] not in indices_reales:
            fallos.append(f"[{tabla}] falta el indice '{i['name']}'")

# --- 4. El progreso tiene que seguir ahi -------------------------------------
despues = {
    "xp": con.execute("SELECT totalXp FROM user_profile").fetchone()[0],
    "racha": con.execute("SELECT currentStreak FROM user_profile").fetchone()[0],
    "lecciones": con.execute("SELECT COUNT(*) FROM lesson_progress").fetchone()[0],
    "tarjetas": con.execute("SELECT COUNT(*) FROM srs_card").fetchone()[0],
    "logros": con.execute("SELECT COUNT(*) FROM achievement").fetchone()[0],
}
if antes != despues:
    fallos.append(f"SE PERDIO PROGRESO: antes={antes} despues={despues}")

nuevo = con.execute("SELECT studentName, avatarId FROM user_profile").fetchone()
if nuevo != ("", "chispa"):
    fallos.append(f"Los valores por defecto del usuario ya existente no son los esperados: {nuevo}")

# --- Resultado ---------------------------------------------------------------
print(f"Progreso antes:   {antes}")
print(f"Progreso despues: {despues}")
print(f"Usuario existente hereda: studentName={nuevo[0]!r}, avatarId={nuevo[1]!r}\n")

if fallos:
    print(f"FALLOS ({len(fallos)}):")
    for f in fallos:
        print("  -", f)
    sys.exit(1)

print(f"OK: la base migrada de la v1 a la v{DESTINO} coincide con lo que Room espera y no se perdio nada.")

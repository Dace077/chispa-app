# Subir Chispa a Google Play

Todo lo que hay que rellenar, ya escrito. Lee primero las dos advertencias:
son las dos cosas que pueden salir mal de forma difícil de deshacer.

---

## 🔴 ADVERTENCIA 1 — La clave de firma

El APK que se repartía por GitHub **ya se ha retirado** (tenía 0 descargas), así
que no hay nadie a quien dejar aislado. Puedes elegir cualquiera de las dos
opciones de firma sin romper nada.

Aun así, **guarda `chispa-release.jks` y su contraseña en un sitio seguro que no
sea solo este ordenador**. En cuanto publiques:

- Si usas tu propia clave y la pierdes, **no podrás volver a actualizar la app
  nunca**. Ni Google puede arreglarlo.
- Si dejas que Google genere la clave de firma (Play App Signing), Google la
  custodia y tú solo necesitas conservar la **clave de subida**, que sí se puede
  restablecer. **Para empezar de cero, esta opción es la más segura.**

El nombre del paquete, `com.chispa.ingles`, **es permanente para siempre** y no
se puede cambiar después.

## 🔴 ADVERTENCIA 2 — Los 12 probadores durante 14 días

Si tu cuenta de desarrollador es **personal** (no de empresa) y la creaste
después de noviembre de 2023, Google **no te deja publicar en producción**
hasta que:

1. Hagas una **prueba cerrada** con **al menos 12 probadores**
2. Que esos 12 sigan apuntados **14 días seguidos**
3. Y luego solicites acceso a producción

No es opcional y no se puede saltar. Planifícalo: necesitas 12 personas con
cuenta de Google dispuestas a instalarla y dejarla puesta dos semanas.

**Empieza por ahí el primer día**, no al final. Mientras corren esos 14 días
puedes ir rellenando todo lo demás.

---

## Archivos listos para subir

```
play/
  app-release.aab          ← el que se sube a Play (NO el .apk)
  graficos/
    icono-512.png          512×512, icono de la ficha
    destacado-1024.png     1024×500, gráfico destacado
  capturas/                capturas de pantalla del teléfono
```

El `.aab` se regenera con:

```bash
./gradlew :app:bundleRelease
```

Sale en `app/build/outputs/bundle/release/app-release.aab`.

⚠️ **Play no acepta APK para apps nuevas**: se sube el `.aab`.

Google Play es ahora el **único** canal de distribución. El release de GitHub y
su APK se retiraron, y la app ya no enlaza a ninguna descarga externa: el botón
de Ajustes abre la ficha de Play. Eso también evita el problema de política que
tenía antes, porque Play prohíbe que una app suya se actualice por otra vía.

---

## Ficha de Play Store (copia y pega)

**Nombre de la app** (máx. 30 caracteres)

```
Chispa: Aprende Inglés
```

**Descripción corta** (máx. 80 caracteres)

```
Inglés de cero a C2. Sin anuncios, sin cuenta y funciona sin conexión.
```

**Descripción completa** (máx. 4000 caracteres) — ver `play/descripcion.txt`

**Categoría**: Educación
**Etiquetas**: Educación, Idiomas, Aprendizaje
**Correo de contacto**: jorgeyu07@gmail.com ← *revisa si quieres usar otro; será público*
**Sitio web**: https://github.com/Dace077/chispa-app
**Política de privacidad**: https://dace077.github.io/chispa-app/privacidad.html

---

## Seguridad de los datos (el formulario largo)

Responde así. Es la verdad y es fácil de defender: **la app no declara el
permiso de Internet**, así que es técnicamente incapaz de enviar nada.

| Pregunta | Respuesta |
|---|---|
| ¿Tu app recoge o comparte alguno de los tipos de datos requeridos? | **No** |
| ¿Se cifran los datos en tránsito? | *(no aplica, no hay tránsito)* |
| ¿Pueden los usuarios pedir que se eliminen sus datos? | **Sí** — desinstalar la app lo borra todo |

**Sobre el micrófono**: Google pregunta por datos *recogidos*, y el audio de
Chispa no se recoge. No se graba en ningún archivo, no se guarda y no se envía:
se lo pasa al reconocedor de voz del propio Android y se descarta. Por eso la
respuesta correcta es «no».

---

## Clasificación del contenido

Cuestionario de IARC. Categoría **Educación / Referencia**. Responde **No** a
todo: violencia, sexo, lenguaje soez, drogas, apuestas, compras, comunicación
entre usuarios, ubicación compartida, contenido generado por usuarios.

Resultado esperado: **PEGI 3 / Everyone / Para todos**.

---

## Público objetivo: **familiar (niños y adultos)**

**Decisión tomada: se declara público mixto y se entra en el programa
*Diseñado para Familias*.** Es lo que la app es de verdad desde que existe
Chispa Kids: el curso A1–C2 para adultos y una etapa para niños de 2 a 5 años,
con su propia puerta en la pantalla de inicio.

Declarar «13 años o más» habría sido más rápido, pero sería falso: la ficha y la
propia app ofrecen una sección infantil. Play trata eso como declaración
incorrecta, y se paga con retirada, no con un aviso.

### Qué marcar en Play Console

En **Contenido de la app → Público objetivo y contenido**:

1. **Grupos de edad**: marcar los tramos infantiles (**5 y menos**, **6-8**,
   **9-12**) **y** los de adultos (**13-15**, **16-17**, **18 y más**).
2. Play preguntará si la app está **dirigida a niños**: la respuesta es que va
   dirigida a niños **y** a adultos (público mixto).
3. Eso activa el programa **Diseñado para Familias**, que exige confirmar el
   cumplimiento de sus políticas.

### Por qué Chispa cumple sin tener que cambiar nada

Cada requisito del programa, y por qué ya se cumple:

| Requisito de Familias | Chispa |
|---|---|
| Sin anuncios de terceros | No hay ninguna librería de publicidad en el proyecto |
| Sin compras integradas | No existe ninguna |
| Sin recogida de datos personales | No se recoge nada, y **no hay permiso de INTERNET**: no habría por dónde enviarlo |
| Sin cuentas ni inicio de sesión | No hay cuenta |
| Sin chat ni contenido de otros usuarios | No hay ninguna función social |
| Sin enlaces externos ni navegador | La app no puede abrir la red |
| Contenido apropiado para la edad | Curso de idiomas; clasificación esperada PEGI 3 / Everyone |
| Política de privacidad accesible | `docs/privacidad.html` vía GitHub Pages |

La única función que sale de la app es **compartir el PDF** de la constancia o
del informe, y la dispara el adulto desde su Perfil, con el selector del propio
sistema.

### Lo que hay que asumir

- **La revisión tarda más** que una app normal: el programa de Familias añade
  una revisión de contenido aparte.
- Si en el futuro se añadiera cualquier cosa que recoja datos, anuncios o
  enlaces externos, **habría que revisar el cumplimiento antes de publicar**,
  no después.

---

## El aviso de «símbolos de depuración»

Al subir el `.aab`, Play Console avisa:

> Este App Bundle contiene código nativo, pero no has subido símbolos de
> depuración.

**Es informativo y se puede publicar igual.** Conviene saber por qué aparece,
para no perder tiempo intentando arreglarlo otra vez:

- El proyecto **no tiene NDK ni código nativo propio**. Las dos librerías que
  detecta Play vienen de dependencias de AndroidX:
  `libandroidx.graphics.path.so` (la usa Compose por dentro) y
  `libdatastore_shared_counter.so` (de DataStore).
- La solución habitual —`ndk { debugSymbolLevel = "FULL" }` en el buildType de
  release— **aquí no hace nada**. Se probó: el `.aab` sale idéntico, sin ninguna
  entrada de símbolos. Esa opción solo empaqueta símbolos de librerías que
  compila el propio proyecto, y las `.so` de AndroidX llegan ya *stripped*
  dentro de sus AAR: no hay símbolos que subir.
- La única forma de tenerlos sería compilar esas dependencias desde su código
  fuente, que no compensa por un aviso.

Consecuencia práctica: si algún día un fallo ocurre **dentro** de esas dos
librerías, el informe de Play llegará sin nombres de función. Como no es código
nuestro, tampoco habría mucho que hacer con él.

---

## Anuncios

**¿Contiene anuncios? → No.** Y es verificable: no hay ninguna librería de
publicidad en el proyecto.

---

## Antes de darle a publicar, comprueba

- [ ] La clave de firma es la tuya (Advertencia 1)
- [ ] `versionCode` es mayor que el de la subida anterior — ahora va por **12**
- [ ] Has probado el `.aab` de verdad, no solo el `.apk` de debug
- [ ] La política de privacidad carga en el navegador desde la URL pública
- [ ] Las capturas no enseñan datos de prueba raros ni la barra de depuración
- [ ] El correo de contacto es uno que quieras hacer público

---

## Después de publicar

Cada versión nueva es **una sola subida**: el `.aab` a Play. Nada más.

El `versionCode` tiene que subir siempre; Play rechaza cualquier entrega con un
número igual o menor al anterior. Va por **15**.

Tus usuarios se actualizan **solos**, en segundo plano, sin hacer nada. El
despliegue es escalonado: puede tardar hasta un día en llegar a todo el mundo,
así que no te alarmes si publicas y no lo ves al momento en tu propio teléfono.

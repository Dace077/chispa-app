# Replica las reglas de ContentRepository.toDomain() para detectar ejercicios que
# la app descartaría en silencio (devuelve null y desaparecen sin avisar).
#
# Correr después de tocar cualquier JSON de contenido:
#     powershell -ExecutionPolicy Bypass -File herramientas\validar-contenido.ps1
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$dir = Join-Path $raiz 'app\src\main\assets\content'
$problems = @()
$checked = 0

function Bad($file, $lesson, $idx, $type, $why) {
    $script:problems += [pscustomobject]@{ File=$file; Lesson=$lesson; Idx=$idx; Type=$type; Why=$why }
}

Get-ChildItem $dir -Filter *.json | Where-Object { $_.Name -notin @('index.json','placement.json') } | ForEach-Object {
    $file = $_.Name
    $j = Get-Content $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($u in $j.units) {
        foreach ($l in $u.lessons) {
            $i = 0
            foreach ($e in $l.exercises) {
                $checked++
                $t = $e.type
                switch ($t) {
                    'multiple_choice' {
                        # direction "en_es" hace que la app LEA EL ENUNCIADO EN VOZ
                        # ALTA con voz inglesa. Si el enunciado esta en espanol, se
                        # oye espanol pronunciado como si fuera ingles.
                        if ($e.direction -eq 'en_es' -and "$($e.prompt)" -match '[¿¡ñáéíóú]') {
                            Bad $file $l.id $i $t "en_es con enunciado en espanol: se leeria con voz inglesa"
                        }
                        $opts = @($e.options | ForEach-Object { $_.Trim() } | Select-Object -Unique)
                        if ($opts.Count -lt 2) { Bad $file $l.id $i $t "menos de 2 opciones" }
                        elseif (-not $e.answer) { Bad $file $l.id $i $t "sin answer" }
                        elseif ($opts -notcontains $e.answer.Trim()) { Bad $file $l.id $i $t "answer '$($e.answer)' no esta en options" }
                    }
                    'translate' {
                        if (-not $e.answer) { Bad $file $l.id $i $t "sin answer" }
                        if (-not $e.prompt) { Bad $file $l.id $i $t "sin prompt" }
                    }
                    'listen_and_type' {
                        if (-not $e.audioText -and -not $e.answer) { Bad $file $l.id $i $t "sin audioText ni answer" }
                    }
                    'word_order' {
                        if (-not $e.answer) { Bad $file $l.id $i $t "sin answer" }
                        else {
                            $pool = @($e.words | Where-Object { $_ -and $_.Trim() })
                            if ($pool.Count -lt 2) { $pool = @($e.answer -split ' ' | Where-Object { $_ }) }
                            if ($pool.Count -lt 2) { Bad $file $l.id $i $t "menos de 2 palabras" }
                            # Las fichas deben poder reconstruir exactamente la answer.
                            if ($e.words) {
                                $normWords = (($e.words -join ' ') -replace '[^\p{L}\p{N} ]', '').ToLower() -replace '\s+', ' '
                                $normAns   = ($e.answer -replace '[^\p{L}\p{N} ]', '').ToLower() -replace '\s+', ' '
                                $keyWords = (($normWords.Trim() -split ' ') | Sort-Object) -join '|'
                                $keyAns   = (($normAns.Trim()   -split ' ') | Sort-Object) -join '|'
                                if ($keyWords -ne $keyAns) {
                                    Bad $file $l.id $i $t "fichas='$normWords' vs answer='$normAns'"
                                }
                            }
                        }
                    }
                    'speak_and_repeat' {
                        if (-not $e.prompt -and -not $e.answer) { Bad $file $l.id $i $t "sin frase" }
                    }
                    'matching_pairs' {
                        $pairs = @($e.pairs | Where-Object { $_.Count -ge 2 })
                        if ($pairs.Count -lt 2) { Bad $file $l.id $i $t "menos de 2 parejas validas" }
                    }
                    'fill_in_blank' {
                        if (-not $e.sentence) { Bad $file $l.id $i $t "sin sentence" }
                        elseif ($e.sentence -notmatch '___') { Bad $file $l.id $i $t "la frase no contiene ___" }
                        if (-not $e.answer) { Bad $file $l.id $i $t "sin answer" }
                        elseif ($e.options -and @($e.options).Count -ge 2) {
                            $o = @($e.options | ForEach-Object { $_.Trim() })
                            if ($o -notcontains $e.answer.Trim()) { Bad $file $l.id $i $t "answer no esta en options -> se perderian los botones" }
                        }
                    }
                    { $_ -in 'tip','reading','culture_note' } {
                        if (-not $e.body) { Bad $file $l.id $i $t "sin body" }
                    }
                    default { Bad $file $l.id $i $t "TIPO DESCONOCIDO: la app lo descartaria" }
                }
                $i++
            }
        }
    }
}

Write-Output "Ejercicios comprobados: $checked"

# --------------------------------------------------------------------------
# Biblioteca de lectura. Se carga aparte del curriculo (no esta en index.json)
# y el lector la tokeniza palabra a palabra, asi que una frase vacia o un id
# repetido rompen el resaltado sin dar ningun error visible.
# --------------------------------------------------------------------------
$niveles = @('A1','A2','B1','B2','C1','C2')
$categorias = @('STORY','ARTICLE','DIALOGUE','LETTER')
$rutaLecturas = Join-Path $dir 'readings.json'
$frases = 0

if (Test-Path $rutaLecturas) {
    $lib = Get-Content $rutaLecturas -Raw -Encoding UTF8 | ConvertFrom-Json
    $vistos = @{}
    foreach ($r in $lib.readings) {
        $id = $r.id
        if ($vistos.ContainsKey($id)) { Bad 'readings.json' $id 0 'reading' "id repetido: solo se veria una" }
        $vistos[$id] = $true

        if ($r.level -notin $niveles)      { Bad 'readings.json' $id 0 'reading' "nivel '$($r.level)' desconocido" }
        if ($r.category -notin $categorias) { Bad 'readings.json' $id 0 'reading' "categoria '$($r.category)' -> caeria a STORY" }
        if (-not $r.title)   { Bad 'readings.json' $id 0 'reading' 'sin titulo' }
        if (-not $r.summary) { Bad 'readings.json' $id 0 'reading' 'sin resumen (la tarjeta sale vacia)' }
        if (@($r.sentences).Count -lt 3) { Bad 'readings.json' $id 0 'reading' 'menos de 3 frases' }

        $i = 0
        foreach ($s in $r.sentences) {
            $frases++
            if (-not $s.en -or -not $s.en.Trim()) { Bad 'readings.json' $id $i 'sentence' 'frase inglesa vacia' }
            if (-not $s.es -or -not $s.es.Trim()) { Bad 'readings.json' $id $i 'sentence' 'sin traduccion' }
            # El lector parte por espacios: sin ninguno no hay palabras que tocar.
            elseif ($s.en -notmatch '\S') { Bad 'readings.json' $id $i 'sentence' 'sin palabras reconocibles' }
            $i++
        }

        # Un dialogo puro sin hablantes se pinta como un relato y se pierde quien
        # dice que. No se exige que TODAS las frases lleven speaker: un relato
        # con dialogo intercalado es una forma valida y comun.
        $conHablante = @($r.sentences | Where-Object { $_.speaker }).Count
        if ($r.category -eq 'DIALOGUE' -and $conHablante -eq 0) {
            Bad 'readings.json' $id 0 'reading' 'categoria DIALOGUE pero ninguna frase tiene speaker'
        }

        $g = 0
        foreach ($w in $r.glossary) {
            if (-not $w.en -or -not $w.en.Trim()) { Bad 'readings.json' $id $g 'glossary' 'entrada sin ingles' }
            if (-not $w.es -or -not $w.es.Trim()) { Bad 'readings.json' $id $g 'glossary' "'$($w.en)' sin traduccion" }
            $g++
        }
    }

    Write-Output "Lecturas comprobadas: $($lib.readings.Count) ($frases frases)"
    $porNivel = $lib.readings | Group-Object level | Sort-Object Name |
        ForEach-Object { "$($_.Name)=$($_.Count)" }
    Write-Output ("  por nivel: " + ($porNivel -join '  '))
    foreach ($n in $niveles) {
        if ($lib.readings.level -notcontains $n) { Write-Output "  AVISO: $n no tiene ninguna lectura" }
    }
} else {
    Write-Output "AVISO: no existe readings.json"
}

# --------------------------------------------------------------------------
# Guia de gramatica. toDomain() descarta en silencio los temas sin id, sin
# titulo o sin cuerpo, y los errores tipicos a los que les falta la correccion.
# --------------------------------------------------------------------------
$rutaGramatica = Join-Path $dir 'grammar.json'
if (Test-Path $rutaGramatica) {
    $g = Get-Content $rutaGramatica -Raw -Encoding UTF8 | ConvertFrom-Json
    $vistosG = @{}
    $sinKeywords = @()
    foreach ($t in $g.topics) {
        $id = $t.id
        if (-not $id)              { Bad 'grammar.json' '?' 0 'topic' 'sin id: se descarta' ; continue }
        if ($vistosG.ContainsKey($id)) { Bad 'grammar.json' $id 0 'topic' 'id repetido' }
        $vistosG[$id] = $true

        if (-not $t.title)          { Bad 'grammar.json' $id 0 'topic' 'sin titulo: se descarta' }
        if ($t.level -notin $niveles) { Bad 'grammar.json' $id 0 'topic' "nivel '$($t.level)' desconocido" }
        if (-not $t.area)           { Bad 'grammar.json' $id 0 'topic' 'sin area: caeria a General' }
        if (-not $t.question)       { Bad 'grammar.json' $id 0 'topic' 'sin pregunta (la tarjeta sale sosa)' }
        if (-not $t.explanation -and @($t.examples).Count -eq 0) {
            Bad 'grammar.json' $id 0 'topic' 'sin explicacion ni ejemplos: se descarta'
        }
        if (@($t.keywords).Count -eq 0) { $sinKeywords += $id }

        $m = 0
        foreach ($e in $t.mistakes) {
            if (-not $e.wrong -or -not $e.right) {
                Bad 'grammar.json' $id $m 'mistake' 'le falta wrong o right: no se mostraria'
            }
            $m++
        }
        foreach ($x in $t.examples) {
            if (-not $x.en) { Bad 'grammar.json' $id 0 'example' 'ejemplo sin ingles' }
            if (-not $x.es) { Bad 'grammar.json' $id 0 'example' "'$($x.en)' sin traduccion" }
        }
        # Un related que no existe no rompe nada, pero es un enlace muerto.
        foreach ($r in $t.related) {
            if ($g.topics.id -notcontains $r) {
                Bad 'grammar.json' $id 0 'related' "apunta a '$r', que no existe"
            }
        }
    }

    Write-Output "Temas de gramatica: $($g.topics.Count)"
    $porNivelG = $g.topics | Group-Object level | Sort-Object Name |
        ForEach-Object { "$($_.Name)=$($_.Count)" }
    Write-Output ("  por nivel: " + ($porNivelG -join '  '))
    if ($sinKeywords.Count -gt 0) {
        Write-Output "  AVISO: sin palabras clave de busqueda: $($sinKeywords -join ', ')"
    }
} else {
    Write-Output "AVISO: no existe grammar.json"
}

if ($problems.Count -eq 0) {
    Write-Output "SIN PROBLEMAS: nada se perderia al cargar."
} else {
    Write-Output "PROBLEMAS ENCONTRADOS: $($problems.Count)"
    $problems | Format-Table -AutoSize
}

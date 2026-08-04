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
if ($problems.Count -eq 0) {
    Write-Output "SIN PROBLEMAS: ningun ejercicio se perderia al cargar."
} else {
    Write-Output "PROBLEMAS ENCONTRADOS: $($problems.Count)"
    $problems | Format-Table -AutoSize
}

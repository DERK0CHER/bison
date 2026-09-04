# Kartenformat

Verbindlich für alles, was Bison importiert. Wo diese Datei und ein Kartenset auseinandergehen,
gilt diese Datei — die Implementierung folgt ihr, nicht umgekehrt.

## Dateiaufbau

Eine Textdatei, **UTF-8**, mit oder ohne BOM, Zeilenenden LF oder CRLF (beides wird gelesen).

```
# Name des Kartensets

Freier Text. Alles vor der ersten ##-Überschrift wird ignoriert — hier steht,
was du dir selbst notieren willst.

## Subsection: MATLAB / Syntax

---
type: syntax
front: Lege einen Spaltenvektor mit 3, 6 und 2 an. Ausgabe unterdrücken.
logik: Semikolon trennt Zeilen innerhalb der eckigen Klammern.
back: d = [3;6;2];
alt: d = [3; 6; 2];
tags: matlab, syntax
```

| Element | Regel |
| --- | --- |
| `# Titel` | Die **erste** `# `-Zeile ist der Name des Sets. Weitere werden ignoriert. |
| Kopf | Alles vor der ersten `## `-Zeile ist Dokumentation und wird nicht gelesen. |
| `## Name` | Beginnt eine Subsection. Ein führendes `Subsection:` wird abgeschnitten, `## Subsection: C / Logik` und `## C / Logik` sind dasselbe. |
| `---` | Trennt Karten. **Nur** wenn die Zeile nichts anderes enthält. |
| Karte | Ein Block zwischen zwei Trennern. Leere Blöcke werden übersprungen, ohne zu zählen. |

Karten vor der ersten `##`-Überschrift werden **nicht** gelesen. Jede Karte gehört zu der
Subsection, die zuletzt über ihr stand.

## Felder

Ein Feld beginnt am **Zeilenanfang** mit `name:`. Der Wert ist der Rest der Zeile plus alle
folgenden Zeilen, bis das nächste Feld beginnt.

Reservierte Feldnamen: `type`, `front`, `logik`, `back`, `alt`, `params`, `tags`, `ziel`,
`a`, `b`, `c`.

**Nur diese Namen beginnen ein Feld.** Eine Zeile `default:` mitten in einem C-Block ist deshalb
Inhalt und kein Feld — das ist der Grund für die feste Liste. Unbekannte Feldnamen (`quelle:`,
`punkte:`) werden **ignoriert**, nicht gemeldet und führen nie zum Absturz; sie landen als
Fortsetzungszeile im vorhergehenden Feld.

### Mehrzeilige Werte

```
front: Was ist x nach diesem Code?
for k = 1:10
    x(1,k) = k;
    x(2,k) = k^2;
end
logik: Zuweisung an (Zeile 1, Spalte k) baut die Matrix spaltenweise auf.
```

- Einrückung innerhalb des Wertes bleibt erhalten.
- **Leerzeilen gehören zum Wert.** Eine C-Antwort, die ihren `#include` durch eine Leerzeile vom
  `struct` trennt, behält sie. Leerzeilen am Ende eines Wertes fallen weg.
- Ein Wert, der selbst mit einem reservierten Namen anfangen soll (`back: ...` als *Text*),
  geht so nicht. Schreib ein Zeichen davor: `> back:` oder `` `back:` ``. Das ist eine bekannte
  Grenze, keine Absicht — sag Bescheid, wenn du sie brauchst, dann kommt eine Escape-Regel dazu.

### Trennzeichen und Inhalte, die sie enthalten

| Zeichen | Bedeutung | Wenn es im Inhalt vorkommt |
| --- | --- | --- |
| `\|` in `alt` | trennt Alternativen | Nicht escapebar. Eine Antwort mit `\|` (z. B. `a \| b` in C) kann derzeit nicht in `alt` stehen — schreib sie als eigene Karte oder melde es. |
| `;` in `params` | trennt Parameter | Kein Problem: `;` in MATLAB-Code steht in `front`/`back`, nicht in `params`. |
| `,` in `params` | trennt Auswahlwerte | `A=R,G,H` sind drei Werte. Ein Wert mit Komma darin geht nicht. |
| `;` und `,` in `front`, `back`, `alt`, `logik` | **nichts Besonderes** | Beliebig verwendbar. `back: X = [zeros(3,8); diag([5 5 5]), zeros(5,3)];` ist völlig in Ordnung. |
| `---` mitten im Text | würde trennen | Nur wenn es allein auf der Zeile steht. `printf("---");` ist unproblematisch. |

## Kartentypen

`type` ist Pflicht und muss einer von sieben sein. Ein unbekannter Typ überspringt die Karte.

| Typ | Pflichtfelder | Optional | Wie es abgefragt wird |
| --- | --- | --- | --- |
| `logik` | `type`, `front`, `back` | `logik`, `tags` | Umdrehen |
| `syntax` | `type`, `front`, `back` | `logik`, `alt`, `tags` | Tippen und vergleichen |
| `param` | `type`, `front`, `back`, `params` | `logik`, `alt`, `tags` | Tippen und vergleichen, Zahlen gewürfelt |
| `sc` | `type`, `front`, `back`, drei Optionen | `logik`, `tags` | Multiple Choice |
| `trace` | `type`, `front`, `back` | `logik`, `alt`, `tags` | Tippen und vergleichen |
| `fehler` | `type`, `front`, `back` | `logik`, `tags` | Umdrehen |
| `zeit` | `type`, `front`, `back` | `logik`, `ziel`, `tags` | Stoppuhr, danach Selbstbewertung |

`logik` ist bei jedem Typ erlaubt und wird beim Umdrehen **vor** der Lösung gezeigt; bei den
getippten Typen erscheint es zusammen mit der Lösung, nachdem abgegeben wurde.

### logik

```
---
type: logik
front: Wann schlägt eine Konkatenation `[A, B]` fehl?
back: Nebeneinander braucht gleiche Zeilenzahl, untereinander gleiche Spaltenzahl.
tags: matlab, dimensionen
```

Erster Tap zeigt `logik` (falls vorhanden), zweiter `back`. Danach „Gewusst" / „Nicht gewusst".

### syntax

```
---
type: syntax
front: Ersetze in Matrix R den Eintrag in Zeile 12, Spalte 4 durch 7. Ausgabe unterdrücken.
logik: (Zeile, Spalte). Semikolon am Ende unterdrückt.
back: R(12,4) = 7;
alt: R(12, 4) = 7;
tags: matlab, indizierung
```

### param

```
---
type: param
front: Bitweise UND: {a} AND {b}
logik: 1 nur, wenn beide Bits 1 sind.
back: {and(a,b)}
params: a=bin(0..255) ; b=bin(0..255) ; width=8
tags: zahlensysteme, bitops
```

### sc

Zwei Schreibweisen, beide gültig. Die Optionszeile unter `front`:

```
---
type: sc
front: Mit welchem Übergabemechanismus wird `void square(long *v){ *v *= *v; }` aufgerufen?
a) Call by Address  b) Call by Reference  c) Call by Value
logik: Parameter mit Stern, Adresse wird übergeben.
back: b. Call by Reference; die Funktion verändert das Original über die Adresse.
tags: c, pointer
```

oder als Felder:

```
---
type: sc
front: Mit welchem Übergabemechanismus wird square aufgerufen?
a: Call by Address
b: Call by Reference
c: Call by Value
back: b. Call by Reference; die Funktion verändert das Original.
```

- Der **erste Buchstabe von `back`** benennt die richtige Option, danach `.` oder `)`.
- Der Rest von `back` ist die Begründung und wird nach der Wahl angezeigt.
- Die Optionszeile wird aus `front` **entfernt**, damit die Frage nichts doppelt sagt.
- **Die App mischt die Optionen bei jeder Anzeige** und zeigt deshalb keine Buchstaben. Wer sich
  „die zweite von oben" merkt, hat nichts gelernt — deswegen ist das so.

### trace

```
---
type: trace
front: Was ist x nach diesem Code?
for k = 1:10
    x(1,k) = k;
end
logik: Zuweisung an (Zeile 1, Spalte k) baut spaltenweise auf.
back: Matrix 1x10 mit den Zahlen 1 bis 10.
tags: matlab
```

### fehler

```
---
type: fehler
front: Ziel 8x8. Versuch `X = [zeros(3,8);diag([5,5,5,5,5]),zeros(3,5)]`. Schlägt das fehl?
logik: Untere Zeile: diag hat 5 Zeilen, zeros(3,5) hat 3.
back: Ja. Korrektur: X = [zeros(3,8); diag([5 5 5 5 5]), zeros(5,3)];
tags: matlab, blockmatrix
```

### zeit

```
---
type: zeit
ziel: 180
front: Definiere in node.h die Datenstruktur Node für eine doppelt verkettete Liste.
logik: Struktur-Tag klein, typedef-Name groß.
back: #include "flugzeug.h"

typedef struct node {
    Flugzeug i;
    struct node *next;
    struct node *prev;
} Node;
tags: c, zeit
```

Die Leerzeile zwischen `#include` und `typedef` bleibt erhalten. Die Reihenfolge der Felder ist
frei — `ziel` darf über `front` stehen.

- `ziel` ist die **Zielzeit in Sekunden**. `ziel: 180` und `ziel: 3:00` bedeuten dasselbe.
- Die Uhr läuft ab dem Anzeigen der Karte und **weiter, wenn die App im Hintergrund ist** — sie
  liest die Wanduhr, nicht die eigenen Ticks.
- „Lösung zeigen" hält sie an, dann `logik`, dann `back`, dann Selbstbewertung.
- Angezeigt werden Zielzeit, Bestzeit und die letzten fünf Zeiten. Über der Zielzeit wird die
  laufende Uhr rot.

## Vergleich beim Tippen

Gilt für `syntax`, `param`, `trace`. Verglichen wird gegen `back` **und** jede Alternative aus
`alt`; eine Übereinstimmung genügt.

**Normalisiert wird:**

- Jede Folge von Leerzeichen, Tabs und Zeilenumbrüchen wird zu **einem Leerzeichen**.
  `d = [3;6;2];` über zwei Zeilen getippt gilt.
- Leerzeichen am Anfang und Ende fallen weg.
- Typografische Anführungszeichen gelten als ihre geraden Formen: `’ ‘ ´ \`` → `'`, `“ ” „` → `"`.
  Android ersetzt den Apostroph gern selbst, und ein MATLAB-Transponierer soll daran nicht
  scheitern.

**Nicht normalisiert wird:**

- **Groß- und Kleinschreibung.** `zeros` ist eine Funktion, `Zeros` ist ein undefinierter Name.
- Leerzeichen **zwischen zwei Wortzeichen**: `d=[3;6]` ist nicht `d = [3;6]`. Wenn beides gelten
  soll, gehört die zweite Form in `alt`.
- Semikolon am Ende. Wenn die Aufgabe nichts über Ausgabe sagt, trag beide Formen in `alt` ein.

## params: Platzhalter und Ausdrücke

`params` ist eine Zeile, Einträge mit **`;`** getrennt, jeder `name=definition`.

| Definition | Bedeutung | Beispiel |
| --- | --- | --- |
| `z=2..20` | Zufälliger Integer im Bereich, Grenzen inklusive | `z=2..20` |
| `A=R,G,H` | Zufällige Auswahl aus der Liste | `A=R,G,H,X,Z` |
| `b=bin(1..255)` | Zufallszahl aus dem Bereich, als Binärstring | `b=bin(0..255)` |
| `h=hex(256..65535)` | dito, hexadezimal | `h=hex(256..65535)` |
| `o=oct(8..511)` | dito, oktal | `o=oct(8..511)` |
| `n=(c1+c2)` | Abgeleitet: Ausdruck über bereits gewürfelte Werte | `n=(c1+c2)` |
| `width=8` | **Sonderfall.** Breite für Binär-, Hex- und Oktalausgaben | `width=8` |

Reihenfolge zählt: Abgeleitete Werte dürfen nur benutzen, was **über** ihnen steht. `width` wird
zuerst gelesen, egal wo es steht.

**Ohne `width` wird nicht aufgefüllt.** `n=8..511` mit `{oct(n)}` ergibt `12`, nicht `012`. Mit
`width=8` ergibt `{bin(n)}` acht Stellen: `00001010`.

### Ausdrücke in `{...}`

Ersetzt werden Platzhalter in `front`, `back`, `alt` und `logik` — **nur bei `type: param`.**
Bei allen anderen Typen sind geschweifte Klammern gewöhnliche Zeichen, damit
`void square(long *v){ *v *= *v; }` heil bleibt.

| Ausdruck | Ergebnis | Beispiel |
| --- | --- | --- |
| `{z}` | der Wert | `{A}({z},{s}) = {v};` |
| `{v*2}`, `{n+1}`, `{a-b}`, `{a/b}`, `{a%b}` | Ganzzahlarithmetik | `{v*2}` |
| `{min(x1,x2)}`, `{max(x1,x2)}` | kleinerer/größerer Wert | `{min(x1,x2)}` |
| `{abs(x)}` | Betrag | `{abs(x1)}` |
| `{bin(n)}`, `{hex(n)}`, `{oct(n)}` | Zahl in der Basis, auf `width` aufgefüllt | `{hex(b)}` |
| `{dec(b)}` | Binärstring als Dezimalzahl | `{dec(b)}` |
| `{and(a,b)}`, `{or(a,b)}`, `{xor(a,b)}`, `{not(a)}` | bitweise, Ergebnis als Binärstring der Breite `width` | `{and(a,b)}` |
| `{shr(a,k)}`, `{shl(a,k)}` | Verschiebung, Ergebnis auf `width` beschnitten | `{shr(a,k)}` |

- **Hexadezimal kommt in Großbuchstaben** (`1F4A`), weil die Karten es so lehren. Wenn du
  `printf("%x")` abfragst, trag die Kleinschreibung in `alt` ein.
- Ein Binärstring zählt in Rechnungen als Zahl: `{dec(b)}` auf `1010` ist `10`.
- Eine Auswahl wie `A=R,G,H` ist **keine** Zahl und kann nicht gerechnet werden.

### Wenn ein Ausdruck ungültig ist

Der Ausdruck bleibt **wörtlich stehen**, so wie er geschrieben wurde. Eine Karte, die
`{min(x1,x9)}` zeigt, ist sichtbar kaputt; eine, die stillschweigend `0` zeigt, ist es nicht —
und würde ein Semester lang gegen eine richtige Antwort gewertet. Die Karte wird also angezeigt
und nicht übersprungen.

## Was der Importer bei Fehlern macht

**Niemals still verwerfen.** Nach dem Import steht auf dem Bildschirm, wie viele Karten erkannt
und wie viele übersprungen wurden, und welcher Leser die Datei genommen hat (`KARTENSET`).

Eine Karte wird übersprungen, wenn:

- `type` fehlt oder unbekannt ist,
- `front` fehlt,
- `back` fehlt,
- bei `sc`: keine drei Optionen gefunden werden, oder der erste Buchstabe von `back` keine davon
  benennt.

Alles andere führt nie zum Überspringen. Unbekannte Felder werden ignoriert.

> **Offen:** Zeilennummer und Grund je übersprungener Karte stehen noch nicht in der App, nur die
> Zahl. Ist eingeplant.

## Änderungen am Format

| Datum | Änderung |
| --- | --- |
| 2026-09-04 | Leerzeilen innerhalb eines Feldwertes bleiben erhalten (vorher verworfen). `sc`-Optionen dürfen auch je auf einer eigenen Zeile stehen. |
| 2026-09-04 | `zeit` und `ziel` aufgenommen. `sc` akzeptiert zusätzlich Felder `a`/`b`/`c`; `sc`-Karten werden beim Import zu normalen Multiple-Choice-Fragen des Bestands, die Optionen werden gemischt und ohne Buchstaben gezeigt. Typografische Anführungszeichen zählen beim Vergleich als gerade. Sonderzeichenleiste um `- + / %` ergänzt. |
| 2026-09-03 | Erste Fassung: `logik`, `syntax`, `param`, `sc`, `trace`, `fehler`; Felder `type`, `front`, `logik`, `back`, `alt`, `params`, `tags`. |

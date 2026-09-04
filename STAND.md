# Stand, 4. September 2026

Notiz zum Weiterarbeiten. Was gebaut wurde, was schiefging, was ungeprüft ist.

## Kurz

**`main` ist grün.** Lauf `33909993850` auf Commit `6de916d` ist durch, beide Jobs — der Build
mit den Unit-Tests und die Gesten auf dem Emulator. Das Rolling Release `bison-latest` hält
jetzt das APK von diesem Stand.

Der Umbau darunter ist trotzdem halbfertig: `:core` steht und trägt, `:desktop` ist angelegt
und unvollständig und wird von keinem Job gebaut. Was fehlt, steht weiter unten.

Auf `main` seit `cf61493`:

| Commit | Was |
| --- | --- |
| `da29add` | `:core` — die Regeln ziehen in ein Modul, das kein Android kennt |
| `d59f5f5` | Fortschrittsformat (`Progress`), Karten-id, `statusOf` |
| `4527558` | zwei zerschossene Escapes; Palette und Markdown-Leser in den Core |
| `6de916d` | zwei Aufrufstellen, die der Modulschnitt hinterlassen hat |

## Die Fehler, der Reihe nach

Vier CI-Läufe, vier Fehlerursachen. Alle vier behoben und im grünen Lauf bestätigt. Sie stehen
hier, weil drei davon beim nächsten Modulschnitt genauso wiederkommen.

### 1. Smart Cast über eine Modulgrenze

```
CardRounds.kt:99:27 Smart cast to 'String' is impossible,
because 'logic' is a public API property declared in different module.
```

Kotlin trägt einen Null-Check nicht über eine Modulgrenze, wenn es in die Eigenschaft nicht
hineinsehen kann — sie könnte beim nächsten Aufruf etwas anderes zurückgeben. Innerhalb eines
Moduls ging das jahrelang gut und fiel deshalb erst beim Schnitt auf.

*Behoben in `d59f5f5`:* `val logic = card.logic` einmal auslesen, dann damit arbeiten.

*Erwarten:* dieselbe Meldung an jeder weiteren Stelle, die eine nullbare Eigenschaft aus
`:core` prüft und danach benutzt. Sichere Aufrufe (`?.let`) und `.orEmpty()` sind nicht
betroffen; ein nacktes `x.y` nach `if (x.y != null)` schon.

### 2. und 3. Zwei zerschossene Zeichenketten

```
Task.kt:188:52     Syntax error: Expecting '"'
Progress.kt:405:18 Incorrect character literal
```

Beides stammt **nicht aus dem Code, sondern aus dem Werkzeug**: mehrzeilige Ersetzungen über
`perl -pe` und Heredocs. Aus `"\n"` wurde ein echter Zeilenumbruch mitten in einem
String-Literal, aus `'\\'` ein halbes Zeichenliteral.

*Behoben in `4527558`,* danach mit dem Editor statt über die Shell geschrieben.

*Die eigentliche Lehre:* drei der vier Fehlschläge kommen aus Shell-Ersetzungen über
mehrzeiligen Kotlin-Quelltext mit Backslashes darin. Das ist eine Abkürzung, die sich jedes Mal
rächt. Für alles mit `\` oder `"` darin gehört ein Editor benutzt.

### 4. Zwei Aufrufstellen, die der Modulschnitt übrig ließ

```
SortDragTest.kt:52  actual type is 'Context!', but 'File' was expected
ProgressTest.kt ×12 actual type is 'Deck', but 'List<Deck>' was expected
```

`DeckStore` hat beim Umzug nach `:core` seinen `Context`-Konstruktor verloren — Android kennt
das Modul nicht mehr. `MainActivity` wurde nachgezogen, **der Instrumentierungstest nicht**:
beim Prüfen wurde nur `src/test` angesehen, nicht `src/androidTest`. Der zweite Fehler war ein
Testhelfer von mir, der eine einzelne `Deck` zurückgab, wo `Progress` eine Liste nimmt.

*Behoben in `6de916d`.*

*Beim nächsten Schnitt:* `grep -rn` über **alle drei** Quellordner (`main`, `test`,
`androidTest`) und über `:app` **und** `:core`.

## Was geprüft ist

`:core:test` läuft in der CI vor allem anderen und ist im grünen Lauf durchgekommen. Damit sind
belegt:

- `Progress` — Export, Einlesen, Zusammenführen, CSV, und dass zwei Abende auf zwei Geräten
  einer werden statt einer von beiden zu gewinnen
- `Markdown` im Core, aus dem beide Oberflächen lesen
- `Task.cardId` / `filedAs` / `type` in allen fünf Kartenarten
- `statusOf`, an das `Card.status` jetzt delegiert

Nicht belegt, weil noch nichts es benutzt: dass das Format über eine echte Verbindung durchgeht.
Der Server ist geschrieben und nie gelaufen.

## `:desktop` ist unvollständig

Angelegt, im `settings.gradle.kts` eingetragen, **von keinem CI-Job gebaut**. Es fehlt genug,
dass `:desktop:compileKotlin` sicher scheitert — deshalb baut es auch niemand.

Da:

- `build.gradle.kts` — Compose Multiplatform 1.12.0, hängt an `:core`
- `Look.kt` — Palette, Typografie, `CardText`, Knöpfe mit der Taste darauf
- `Home.kt` — wo die Dateien auf einem PC liegen (`%APPDATA%\Bison`), Sync-Code, Port
- `Sync.kt` — HTTP-Server aus dem JDK, ein Austausch in beide Richtungen

Fehlt:

- `Main.kt` — Fenster, Zustand, Startbildschirm mit der Übersicht pro Subsection
- `Study.kt` — die Kartenrunden für alle sieben Typen, mit der Tastatur bedienbar
- ein CI-Job auf `windows-latest`, der `:desktop:createDistributable` ausführt und das Ergebnis
  als Zip hochlädt (`Bison.exe` mit mitgelieferter Laufzeit, kein Java nötig, kein WiX)
- auf der Handy-Seite: ein Sync-Bildschirm und Klartext-HTTP für die lokale Adresse

**Ungeprüft und wahrscheinlich die nächste Hürde:** ob Compose Multiplatform 1.12.0 mit dem
Kotlin zusammengeht, das AGP 9.0.1 mitbringt (2.3.21). Das entscheidet sich beim ersten Lauf,
der `:desktop` anfasst.

## Entscheidungen, die nicht noch einmal aufgemacht werden müssen

**Was synchronisiert wird, sind Versuche, nicht Zustände.** Ein Versuch ist eine Tatsache mit
einem Zeitpunkt, also ist Zusammenführen eine Vereinigung und keine Entscheidung. Zehn Karten in
der Bahn und fünf am Schreibtisch sind fünfzehn und nicht die des Geräts, das sich zuletzt
gemeldet hat. Box und Sekunden reisen **nicht** mit, sie werden aus der zusammengeführten
Historie neu gezogen — eine mitgeschickte Box müsste ausgewählt werden, und Auswählen wirft
einen der beiden Abende weg.

**Die Karten-id ist `sha1(subsection + "\n" + front)`.** Sie folgt aus der Karte allein, damit
beide Geräte unabhängig auf denselben Namen kommen. Sie hängt **nicht** an der Antwort: ein
Tippfehler in der Musterlösung darf die Historie nicht kosten. Dafür ist `filedAs` von
`identity` getrennt.

**Daraus folgt eine Bedingung, die nirgends eine Fehlermeldung erzeugt:** Handy und Rechner
müssen dieselbe Kartendatei einlesen. Zwei Stände von `karten_swt2.md` ergeben stillschweigend
zwei getrennte Historien.

**Der Rechner ist der Server, das Handy meldet sich.** Der Rechner steht still, hat eine
ablesbare Adresse und eine Tastatur daneben. Sechsstelliger Code im Header, weil drei Leute im
selben Raum das laufen haben können und ein Handy sonst in die Wiederholung eines Kommilitonen
hineinsynchronisiert.

**`org.json` ist im Core `compileOnly`.** Auf Android liefert die Plattform es; ins APK darf es
nicht daneben. Genau dafür liefen drei Tests früher unter Robolectric — die laufen jetzt als
gewöhnliche JVM-Tests.

**Palette und Markdown-Leser liegen im Core.** Handy und Rechner zeichnen mit verschiedenen
Compose-Artefakten und können sich weder `Color` noch `AnnotatedString` teilen — wohl aber, was
eine Farbe ist und wo ein Codestück anfängt.

## Offen aus Phase 1

- **Punkt 6, Export:** `Progress` kann JSON und CSV, aber **keine Oberfläche ruft es auf**. Es
  fehlen die Knöpfe, das Teilen-Blatt und der Weg nach Downloads.
- **Punkt 2, Rest:** Übersicht pro Subsection mit letzter Zeit, Bestzeit und Anzahl je Karte.
- Der Importzähler soll **erwartet gegen importiert** zeigen, nicht nur erkannt gegen
  übersprungen.

## Eine offene Frage an den Auftraggeber

Der Vergleichertest über die ganze Kartendatei braucht das Set als Fixture. Das Repo ist
**öffentlich**. Also: Set hinein, Repo auf privat (dann bricht die Installation per Link), oder
Test nur lokal?

## Fürs nächste Mal

Der Umbau lief direkt auf `main`, und drei Läufe hintereinander standen dort rot. Ausgegangen
ist es gut, aber wer in diesen Stunden gezogen hat, bekam einen Stand, der nicht kompilierte.
Der nächste Schnitt dieser Größe gehört auf einen Zweig — die CI baut `claude/**` genauso.

Wenn ein Stand doch einmal rot festhängt und keine Zeit zum Nachziehen bleibt, setzt

```bash
git revert --no-commit <letzter-gruener>..HEAD && git commit -m "revert: Umbau zurueck, bis er grün ist"
```

zurück, ohne die Historie zu verlieren; die Arbeit steht dann in den Commits und kann auf einem
Zweig fertiggemacht werden.

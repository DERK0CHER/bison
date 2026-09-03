# Roadmap

Die zehn Punkte in der Reihenfolge, in der sie gestellt wurden — Reihenfolge ist Priorität.
Erledigtes steht mit dabei, damit klar ist, worauf aufgesetzt wird.

Alle zehn Punkte sind gebaut. Was hier als erledigt steht, ist gebaut und durch Tests belegt.
Was als **ungeprüft** markiert ist, hat noch keinen Test — seit die CI einen Emulator-Job hat,
heißt das „noch nicht geschrieben" und nicht mehr „geht hier nicht".

| Punkt | Stand |
| --- | --- |
| 1 Code-Editor-Karte | erledigt |
| 2 Zeilen-Sortieren + Hochstufung | erledigt |
| 3 Tipp-Antwort exakt | erledigt |
| 4 Generator-Karten | erledigt |
| 5 Trace-Karten | erledigt |
| 6 Bilder | erledigt |
| 7 Klausurmodus | erledigt |
| 8 FSRS/SM-2 | erledigt (SM-2) |
| 9 Tags als Filter | erledigt |
| 10 Import aus Datei | erledigt, CSV bewusst nicht |

---

## 1. Code-Editor-Karte — **erledigt**

> Vorderseite: Aufgabentext plus Funktionssignatur oder Rumpf mit Lücke (`>>> Hier fehlt was`).
> Rückseite: mehrzeiliges Monospace-Eingabefeld, Tab-Einrückung, Autokorrektur und
> Auto-Capitalisierung hart abgeschaltet, Sonderzeichenleiste
> (`{ } ( ) [ ] ; * & -> == != < >`). Nach Absenden: Musterlösung daneben, zeilenweiser Diff mit
> farbiger Markierung. Selbstbewertung pro Zeile: richtig / Syntaxfehler (−0,25) / Semantikfehler
> (−0,5), App rechnet Punkte wie in der Klausur.

`ui/CodeRound.kt`, `ui/CodeParts.kt`, `domain/LineDiff.kt`, `domain/Marking.kt`, `model/Task.kt`.

- Lücke ist `CodeTask.GAP` = `>>> Hier fehlt was`.
- Tastatur: `KeyboardCapitalization.None`, `autoCorrectEnabled = false`,
  `KeyboardType.Ascii` — alle drei, weil einzeln keine reicht.
- Tab: `⇥` in der Leiste fügt vier Leerzeichen ein. Zusätzlich übernimmt Return die Einrückung
  der Vorzeile (`autoIndent`), was in der Praxis mehr bringt als die Tab-Taste.
- Diff ist LCS, nicht Index gegen Index: eine vergessene Zeile kostet **eine** Diff-Zeile.
- Bewertung durch Antippen der Zeile, zyklisch richtig → Syntax → Semantik. Vorbelegt: passende
  Zeilen `richtig`, alles andere `Semantik`.
- Punkte: jede Zeile der Musterlösung ist einen Punkt wert, Anzeige `8,25 / 10`.
- **Entscheidung, die du kippen kannst:** als *richtig* für die Box zählt nur ein Durchgang
  **ohne jeden Abzug**. Ein −0,25 in zehn Zeilen halbiert also die Box. `Marking.clean`.
- Die Vorderseite ist inzwischen zweigeteilt: `prompt` ist Prosa, `given` ist Code und wird in
  Monospace gesetzt (`ui/CodeParts.kt`, `GivenCode`). Das war der Layoutfehler aus den
  Screenshots.

## 2. Zeilen-Sortieren-Modus — **erledigt**

> Musterlösung in Zeilen zerlegt, gemischt, per Drag in Reihenfolge bringen. Die App soll eine
> Karte automatisch vom Sortier- in den Schreibmodus hochstufen, sobald sie zweimal fehlerfrei
> sortiert wurde.

`ui/SortRound.kt`, `Card.mode`, `Card.SORTS_TO_WRITE = 2`, `StudySession.sorted(clean)`.

- Greifen per Langdruck, Zeilen tauschen sobald der Finger eine Zeilenhöhe überschritten hat.
- Zeilen haben feste Höhe (`ROW_HEIGHT = 46.dp`) und scrollen seitwärts statt umzubrechen.
- Eine vermurkste Sortierung setzt den Zähler auf 0 zurück.
- **Die Drag-Geste ist geprüft** — auf einem echten Android 14 im Emulator, CI-Job „Gesten auf
  einem Emulator" (`app/src/androidTest/…/SortDragTest.kt`). Der Test schreibt ein Set in den
  Speicher der App, startet sie und baut die Touch-Events von Hand: Druck, halten über den
  System-Long-Press hinaus, dann Bewegungen im Frame-Abstand. Nichts davon bringt eine eigene
  Uhr mit, und genau daran war der JVM-Versuch dreimal gescheitert (Main-Clock vorspulen,
  Event-Zeit vorspulen, beides — die Zeilen blieben exakt eine Zeilenhöhe auseinander stehen).
  Der gescheiterte Test steht als `@Ignore` in `SortRoundTest`, damit nachvollziehbar bleibt,
  was nicht geht.
- Dabei kam ein **echter Fehler** heraus: jede Zeile hatte ihre Geste an ihrer *Position*
  verschlüsselt (`pointerInput(task, position)`, Zeilen ohne `key`), also hat der erste Tausch
  die Modifier neu verschlüsselt und die laufende Geste weggeworfen. Auf dem Gerät: Zeile
  anfassen, sie rutscht **genau ein** Feld, danach passiert nichts mehr, bis man loslässt.
  Jetzt `key(line)` und `pointerInput(task, line)` — und der Emulator bestätigt es.
- Die Rechnung „Finger 180 px weiter → Zeile zwei Plätze tiefer" liegt als `RowDrag` in
  `domain/` und ist einzeln getestet.

## 3. Tipp-Antwort mit exaktem Vergleich — **erledigt**

> Normalisiert Whitespace, sonst exakt. Alternative Musterlösungen pro Karte erlaubt
> (`d=[3 6 2 5 9]'` und `d=[3;6;2;5;9]`).

`ui/TypeRound.kt`, `LineDiff.sameLine`, `CardMode.Type`, `CodeTask.isOneLiner`.

- Eine Karte, deren Musterlösung **eine Zeile** ist, landet im Einzeiler-Modus statt im Editor:
  ein Feld, kein Diff, keine Selbstbewertung — die App entscheidet.
- Normalisierung: Whitespace neben allem, was kein Buchstabe und keine Ziffer ist, fällt weg
  (`d=[3;6]` = `d = [3;6]`). Zwischen zwei Wortzeichen bleibt er stehen, weil er dort trennt
  (`int a` ≠ `inta`, `[3 6]` ≠ `[36]`). Groß- und Kleinschreibung zählt.
- Alternativen weiter über `alt:`.
- Screenshots: `11-type-line.png`, `12-type-wrong.png`.

## 4. Generator-Karten für Zahlensysteme — **erledigt**

> Kartentyp mit Parametern (Basis von, Basis nach, Bitbreite, Operation AND/OR/XOR/Shift), App
> würfelt Operanden bei jedem Aufruf neu und berechnet die Lösung selbst. Gleiches für
> `printf("%4x", a*b)`-Ausgaben.

`model/Generated.kt`, `domain/Generator.kt`, `ui/GeneratedRound.kt`, `CardMode.Generate`.

- Drei Sorten: `convert` (Basis → Basis), `bits` (zwei Zahlen, ein Operator), `printf`.
- Importformat: `type: gen` mit `kind:`, `op:`, `from:`, `to:`, `bits:`, `format:`. Steht im
  README, inklusive Beispielen.
- Die Zahlen kommen aus einem **Seed**, nicht aus der Uhr: der Seed ist die Rundennummer. Sonst
  würfelt jeder Frame neu und die Frage ändert sich unter dem Finger. Nebeneffekt: die
  Screenshots sind zwischen zwei Läufen vergleichbar.
- Antwortvergleich als **Zahl**, wenn sie als Zahl lesbar ist: `0f`, `f`, `0x0F`, `0000 1111`
  sind dieselbe Antwort. Nur bei `printf` zählt der Text, weil dort die Nullen die Aufgabe sind
  — führende und schließende Leerzeichen einer Breitenangabe (`%4x`) werden aber verziehen, die
  sind auf einer Telefontastatur nicht zumutbar.
- Fortschritt hängt an der Karte, nicht an der Instanz: die Box zählt, wie oft diese *Sorte*
  Aufgabe gerechnet wurde.
- Screenshot: `15-generated.png`.

## 5. Trace-Karten — **erledigt**

> Programm vorne, Ausgabe wird getippt. Zusätzlich ein Single-Choice-Modus mit genau drei
> Optionen, weil die Klausur so aussieht.

Fällt aus 1, 3 und der Vorderseitentrennung heraus:

- Drei-Optionen-SC: `type: choice` mit einem Codeblock unter `front:`. Der Block ist jetzt
  `given` und wird in Monospace gesetzt — vorher war ein Programm auf einer Multiple-Choice-Karte
  in der Fließtextschrift, also unlesbar. Screenshot: `13-trace.png`.
- Ausgabe tippen: `type: code` mit dem Programm unter `front:` und der Ausgabe als `back:`.
  Einzeilige Ausgabe landet automatisch im Einzeiler-Modus.

## 6. Bild auf Vorder- und Rückseite — **erledigt**

> Damit "Activity Chart → C" (Bild vorne, Editor hinten) und "C → Activity Chart" (Code vorne,
> Lösungsbild hinten, Zeichnen auf Papier, Selbstbewertung) funktionieren.

`data/ImageStore.kt`, `Task.image`, `model/Task.kt` → `SketchTask`, `ui/CardPicture.kt`,
`ui/RevealRound.kt`, `CardMode.Reveal`.

- `image:` geht bei **jedem** Kartentyp — das ist die Richtung „Bild vorne, Editor hinten".
- `type: sketch` mit `answerimage:` (oder `answer:` für Prosa) ist die andere Richtung: Aufgabe,
  „Lösung zeigen", „Hatte ich" / „Hatte ich nicht". Zugleich der schlichte Karteikartenmodus,
  den die App nicht hatte.
- Bilder liegen in `filesDir/images/`, in der Karte steht nur der Dateiname. Der relative Pfad
  aus der ursprünglichen Idee geht **nicht**: der Dateidialog liefert eine `content://`-URI, aus
  der kein Nachbarverzeichnis erreichbar ist. Deshalb Mehrfachauswahl im Import-Screen (Schritt
  3) und Ablage unter dem Dateinamen.
- Namen werden aufgeräumt (klein, nur `a-z0-9.-_`), damit `Activity Chart.PNG` und
  `activity-chart.png` dieselbe Datei finden und keine Karte aus dem Verzeichnis herauszeigen
  kann.
- Große Bilder werden beim Laden heruntergerechnet (`ImageStore.load`), sonst liegt ein
  Handyfoto mit 60 MB im Speicher für eine Karte von 400 px Breite.
- Der `ImageStore` hängt an einem CompositionLocal (`LocalImages`) statt durch fünf Signaturen
  gereicht zu werden. Ohne ihn — also in jedem Test — zeichnet die Karte die Fehlt-Platte.
- Screenshots: `16-sketch.png`, `17-sketch-answer.png`.

## 7. Klausurmodus — **erledigt**

> Gewichtete Ziehung nach Subsection (z. B. 25 SC, 4 Theorie, 5 Programmieraufgaben, 20 MATLAB),
> 120-Minuten-Countdown, kein Umdrehen bis zur Abgabe, Auswertung mit Punkten pro Block.

`domain/Exam.kt`, `ui/ExamSetupScreen.kt`, `ui/ExamScreen.kt`. Einstieg: Knopf „Klausur" unter
den Bereichen.

- Ziehung nach Plan, ein Slider pro Bereich plus Zeit. Ein Bereich mit zu wenigen Karten gibt,
  was er hat, statt den Start zu verweigern.
- Während der Klausur sagt die App **nichts**: angekreuzt wird grau, kein Richtig/Falsch, und
  man kann vor und zurück blättern.
- Die Uhr zählt eigene Ticks und nimmt die Wanduhr, wann immer die weniger sagt. Nur Ticks
  würde stehen bleiben, während die App weggelegt ist; nur Wanduhr würde in einem Test nie
  ablaufen. **Ungeprüft:** ob Android die Ticks im Hintergrund wirklich anhält.
- Bewertet wird nach der Abgabe und nur, was die App nicht selbst kann: geschriebene Funktionen
  zeilenweise (dieselbe Bewertung wie im Lernmodus, jetzt geteilt statt zweimal geschrieben),
  Zeichnungen per Augenschein.
- **Kein Rückschreiben in die Boxen.** Eine Probeklausur ist eine Messung; eine, die verschiebt,
  was sie misst, ist weniger wert als keine.
- Punkte: geschriebene Funktion = eine pro Musterlösungszeile, alles andere = eine. Das ist
  geraten; eine Klausur mit anderer Gewichtung bräuchte das im Plan.
- Ergebnisse werden **nicht gespeichert** — es gibt keinen Verlauf. Wäre ein eigener Store.
- Screenshots: `18-exam-setup.png`, `19-exam.png`, `20-exam-result.png`.

## 8. Spaced Repetition — **erledigt**

> FSRS oder SM-2 mit Statistik pro Subsection, Leech-Erkennung (Karte 5× falsch → separate
> Liste), und Zeit pro Karte protokollieren.

`domain/Schedule.kt`, `Card.due/interval/ease/lapses/seconds`, Speicherversion 7.

- **SM-2, nicht FSRS.** FSRS ist besser, und zwar weil es siebzehn Parameter an eine
  Antworthistorie anpasst, die diese App nicht hat und die ein Semester bräuchte. SM-2 braucht
  nur die Karte selbst und ist ein Dutzend Zeilen. Beide hängen an derselben Stelle ein, falls
  die Historie irgendwann existiert.
- **Bewertet wird die Sitzung, nicht die Antwort.** SM-2 bewertet eine Wiederholung, und eine
  Wiederholung ist hier nicht eine Frage: die Sitzung fragt eine Karte, bis sie mehrfach
  hintereinander saß. Also zählt, ob sie *irgendwo* in der Sitzung falsch war.
- Intervalle: 1 Tag, 6 Tage, danach × Ease. Ease startet bei 2,5, steigt um 0,1, fällt um 0,2,
  Boden 1,3, Deckel 2,8; Intervall gedeckelt bei 180 Tagen.
- Eine fällige Karte kommt bei `REVIEW_BOX = 3` zurück, also eine richtige Antwort vor dem Ziel
  der ersten Runde — dadurch wird sie früh gefragt und danach normal mitgeschleift.
- **Leech**: 5× verloren → eigene Liste (Knopf im Bereichs-Screen) und automatisch `hard`,
  wodurch sie innerhalb einer Sitzung doppelt so oft drankommt. Die eigentliche Antwort auf eine
  Leech ist trotzdem, die Karte neu zu schreiben.
- **Zeit pro Karte** wird mitgeschrieben und bei 5 Minuten gedeckelt (`MAX_SECONDS`), sonst
  behauptet eine weggelegte Karte zwei Stunden. Angezeigt als „34 min geübt" pro Thema.
- Deck-Liste und Bereiche zeigen „12 fällig" bzw. „in 3 Tagen wieder".
- Wenn **nichts** fällig ist und du trotzdem draufdrückst, wird alles gelernt statt einer leeren
  Sitzung (`MainActivity.toStudy`). Der Plan sagt, was sich lohnt, nicht ob du darfst.
- Alte Dateien haben kein Datum → alles sofort fällig. Für ein Set, das seit Wochen daliegt, ist
  das die richtige Antwort.
- **Statistik pro Subsection** ist bewusst klein: fällig, sicher, Fortschritt, Zeit. Ein Verlauf
  über Tage bräuchte einen zweiten Speicher.

## 9. Tags als Filter — **erledigt**

> Klausurjahr und Aufgabentyp, damit "alle Node_Delete-Varianten" oder "alles aus WS24"
> filterbar ist.

`Deck.tags`, `Deck.cardsTagged`, `ui/SubtopicScreen.kt`, `Screen.Study.tags`.

- Die Tags stehen unter den Bereichen auf demselben Screen; Auswahl macht aus dem Knopf darunter
  eine Sitzung über genau diese Karten.
- **Zwei gewählte Tags heißen „beide", nicht „eins von beiden".** WS24 + Node_Delete ist die
  gemeinte Verengung; zwei Jahre gleichzeitig ergeben null Karten, und das sagt der Knopf vorher
  an statt hinterher. Falls dir „oder" lieber ist: `Deck.cardsTagged`, ein Wort.
- Ein Thema mit nur einem Bereich öffnet jetzt den Bereichs-Screen, **wenn** es Tags hat — sonst
  wäre der Filter für genau die Sets unerreichbar, die am ehesten welche tragen.
- Screenshot: `14-tags.png`.

## 10. Import aus Markdown/CSV — **erledigt** (CSV bewusst nicht)

> Ich schreibe die Karten am Desktop, nicht auf dem Handy. Eine Datei pro Subsection, Karten
> getrennt durch `---`, Felder `type:`, `front:`, `back:`, `alt:`, `tags:`.

`importer/CardFileParser.kt`, `importer/CardImport.kt`, `ui/ImportScreen.kt`, `MainActivity`.

- Einlesen jetzt auch **aus einer Datei** über denselben Dateidialog wie Sichern/Laden. Der
  Catch-all-MIME-Typ steht mit in der Liste, weil eine `.txt` vom Desktop oft als
  `application/octet-stream` ankommt und sonst ausgegraut wäre.
- Zwischenablage bleibt, beide Wege enden im selben Parser und in derselben Vorschau.
- CSV ist bewusst nicht gebaut: Code in CSV-Zellen ist dasselbe Escaping-Problem wie in JSON.

---

## Was noch ungeprüft ist

Seit es den Emulator-Job gibt, ist das keine Liste von Unmöglichkeiten mehr, sondern eine von
ungeschriebenen Tests. Jeder Punkt hier ließe sich in `app/src/androidTest/` beantworten.

- **Töne** (`audio/Feedback.kt`): synthetisierte Sinustöne, aufsteigende Quinte für richtig,
  fallende Terz für falsch. Frequenzen und Längen sind vier Zahlen in `RIGHT`/`WRONG`. Dass die
  Lautstärketasten sie erreichen, ist über `USAGE_MEDIA` plus `volumeControlStream` gelöst — die
  Ursache ist belegt, die Wirkung nicht.
- **Farbblitz** (`FLASH_PEAK = 0.16f`, `FLASH_IN = 110`, `FLASH_OUT = 420` in `StudyScreen.kt`).
  Roborazzi fotografiert erst nach Ende der Animation.
- **Tastatur-Insets** im Import-Screen und in den beiden Tippmodi.
- **Der Dateidialog** beim Import: dass die Datei ankommt, ist Code; dass dein Dateimanager sie
  anbietet, ist Gerätesache.
- **Die Klausuruhr im Hintergrund.** Sie zählt Ticks über `delay`, und Android hält den
  Frame-Takt an, während das Fenster nicht sichtbar ist. Die Wanduhr-Korrektur fängt das beim
  Zurückkommen ab — aber ob die Uhr wirklich weiterläuft, während du die App weglegst, ist eine
  Gerätefrage.
- **Mitternacht.** `Schedule.today()` wird beim Öffnen eines Screens einmal gelesen. Wer um
  23:59 anfängt, lernt die Karten von gestern zu Ende. Das ist gewollt und trotzdem eine
  Stelle, an der man sich wundern kann.

## Speicherversionen

`data/DeckStore.kt`, Konstante `VERSION`. 1 flache Kartenliste, 2 Bereiche, 3 Kartentypen,
4 Code auf der Vorderseite getrennt, 5 Generator-Karten, 6 Bilder und Papierkarten, 7 Termine.
Alte Dateien werden weiter gelesen; eine Karte aus Version ≤ 3 trägt ihre ganze Vorderseite im
`prompt` und wird als Prosa gesetzt, bis die Kartendatei neu importiert wird, und eine aus ≤ 6
hat kein Datum und ist damit sofort fällig.

## Lokal weiterarbeiten

```sh
cd bison
./gradlew :app:assembleDebug             # APK
./gradlew :app:testDebugUnitTest         # Tests
./gradlew :app:recordRoborazziDebug      # Screens nach bison/screenshots/ rendern
./gradlew :app:connectedDebugAndroidTest # Gesten — braucht ein Gerät oder einen Emulator
```

Die CI hat zwei Jobs: der eine baut, testet und rendert (kein Emulator, deshalb die
Screenshots), der andere startet einen Emulator und fährt die Gesten an. Der zweite braucht
~8 Minuten und ist die einzige Stelle, an der eine echte Berührung im Spiel ist.

ktlint läuft nicht als Gradle-Task; Stil wurde von Hand nachgezogen. Wenn du es dauerhaft willst,
lohnt das ktlint-Gradle-Plugin.

## Entscheidungen, die auf Widerspruch warten

- Kein Abzug = richtig (Punkt 1). Eventuell zu streng.
- Zwei Tags heißen „beide" (Punkt 9).
- Einzeiler kommen nie in den Sortiermodus, auch nicht am Anfang (Punkt 3).
- Bewertet wird die **Sitzung**, nicht die einzelne Antwort (Punkt 8). Ein Ausrutscher am
  Anfang kostet die Karte also das lange Intervall, auch wenn sie danach zehnmal saß.
- Eine Karte kommt nach der Wiederholung bei Box 3 zurück, nicht bei 0 und nicht bei 8.
- Der Klausurmodus schreibt **nichts** zurück. Wer nach einer Probeklausur Fortschritt sehen
  will, wird enttäuscht sein — das ist Absicht.
- `ROUNDS = listOf(4, 6, 8)` und `CURVE = 1.25` — die Kurve legt 4× richtig auf 60 %, 6× auf 82 %.
- `WORKING_SET = 12` Fragen in Rotation, `NEW_EVERY = 4` Antworten bis eine neue eingemischt wird.
- Sichern/Laden **merged** statt zu überschreiben, damit ein Fehlgriff die App nicht leerräumt.

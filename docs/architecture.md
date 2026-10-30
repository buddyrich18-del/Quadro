# Architecture: Quadro (app Android, ricostruzione da zero)

Nome provvisorio "Quadro". Controllo marchio e nome sullo store non fatti. Ricostruzione clean-room: nessun codice, grafica o testo dell'app ufficiale; le funzioni nuove (grafici personalizzati, clima, link pubblici) sono aggiunte.

## Stack

| layer | choice | why |
| --- | --- | --- |
| linguaggio | Kotlin | standard Android, già scelto con l'utente |
| interfaccia | Jetpack Compose + Material 3 | meno file XML, temi semplici |
| grafici | Canvas di Compose, scritti qui | nessuna libreria da aggiornare; linee, barre, fasce, indicatore al tocco |
| logica | modulo `core` in Kotlin puro | testabile su JVM anche senza Android |
| rete | `HttpURLConnection` + coroutine | zero dipendenze |
| dati locali | JSON in SharedPreferences | pochi dati; niente database |
| chiavi API | EncryptedSharedPreferences | le chiavi non stanno nel JSON di configurazione |
| avvisi | WorkManager (controllo ogni 15 min) + notifiche locali | nessun server push necessario |
| QR | Google code scanner (`play-services-code-scanner`) | senza permesso camera e senza codice camera |
| link FusionSolar | `WebView` in una scheda | scelta dell'utente; nessun accesso alle chiamate interne della pagina |
| build | Gradle + GitHub Actions | qui mancano SDK e repository Google; l'APK lo compila GitHub |

Backend: nessuno. L'app parla direttamente con il server Emoncms dell'utente (API pubblica documentata, chiave di sola lettura) e con link pubblici scelti dall'utente.

## Schema (dati locali, non SQL)

Access rules: i dati stanno nell'archivio privato dell'app; le chiavi in archivio cifrato, indicizzate per id sorgente.

```
Source        id, name, kind (EMONCMS | PUBLIC_LINK), baseUrl, (chiave: archivio cifrato)
SeriesRef     sourceId, feedId (Emoncms) | linkFormat+timeKey+valueKey (link pubblico), label, unit, colorArgb
CardConfig    id, title, order, e una di queste varianti:
  ChartCard   kind (LINE | BAR), series[1..4], defaultPeriod, bucket (NONE|HOUR|DAY), aggregate (AVG|SUM|MIN|MAX|LAST), yMin?, yMax?
  EnergyCard  sourceId, powerFeedId (W), kwhFeedId (kWh cumulativo), unitCost, currencySymbol
  ClimateCard temperature SeriesRef, humidity SeriesRef, tempBand(min,max), humBand(min,max), alerts (soglie min/max per grandezza, attive o no)
  WebCard     url (solo https)
AlertState    cardId, quantity (TEMP|HUM), state (OK|LOW|HIGH), ultimo avviso
```

## API usate (Emoncms, sola lettura)

| path | scopo | input | output |
| --- | --- | --- | --- |
| `feed/list.json` | scegliere feed per nome e tag | chiave | elenco feed |
| `feed/value.json` | ultimo valore (potenza, temperatura, umidità) | `id` | numero |
| `feed/data.json` | serie storica | `id`, `start`, `end`, `interval`, `average` o `delta` | `[[tempo, valore], ...]` |

Autenticazione: header `Authorization: Bearer <chiave>`, così la chiave non finisce negli URL.
Link pubblici: GET con formato JSON o CSV scelto nella scheda.
Job: `AlertWorker` ogni 15 minuti (minimo di WorkManager): legge l'ultimo valore dei feed clima, valuta le soglie con isteresi, notifica solo al cambio di stato.

## The parts that bite

- **Fusi orari e ora legale:** i giorni delle barre si calcolano sul fuso del telefono, non in UTC. Per questo i kWh giornalieri si ottengono sommando delta orari, non chiedendo al server un intervallo di 86.400 s.
- **Tempi in secondi o millisecondi:** le serie possono usare l'uno o l'altro; il parser normalizza.
- **HTTP in chiaro:** un emonPi in rete locale spesso non ha HTTPS. L'app consente HTTP, con avviso nell'editor del server. Compromesso di sicurezza dichiarato.
- **Chiavi:** mai nei log, mai nel JSON di configurazione.
- **Avvisi:** WorkManager non è esatto (risparmio batteria, Doze); l'isteresi evita notifiche ripetute; Android 13+ chiede il permesso notifiche.
- **WebView:** solo https, nessun ponte JavaScript verso l'app.
- **Formati dei link pubblici:** troppo vari per indovinarli tutti; l'utente sceglie le chiavi; errori chiari.
- **Serie lunghe:** si riducono a circa 600 punti prima di disegnare.
- **Formato del QR:** non l'ho visto nell'app ufficiale; il parser è tollerante ma non verificato. L'inserimento manuale è la via sicura.

## Build order

1. **Vertical slice:** sorgente Emoncms inserita a mano, scheda Energia (S01): potenza adesso, consumo oggi, barre giornaliere, 3H/6H/D/W/M.
2. **Must-haves:** più sorgenti e più schede, interruttore kWh/costo, valuta, chiavi cifrate, QR.
3. **Novità richieste:** schede grafico a linee e a barre, scheda clima con fasce comfort, stato a colpo d'occhio e avvisi, sorgente link pubblico JSON/CSV, scheda web (FusionSolar).
4. **Should:** schermo intero, schermo sempre acceso, layout orizzontale.
5. **Rinviati:** widget nella home, cache offline, sezioni My Heatpump / Electric Flow.

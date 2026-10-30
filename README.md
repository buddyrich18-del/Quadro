# Quadro

App Android per tenere d'occhio consumi, temperatura e umidità: legge un server Emoncms, link pubblici JSON/CSV e mostra pagine web (per esempio un link FusionSolar).

Non è un'app ufficiale di Emoncms né di FusionSolar.

## Cosa fa

- **Energia:** potenza adesso, consumo di oggi, grafico a linee con 3H/6H/D/W/M, barre dei kWh giornalieri, interruttore kWh/costo.
- **Grafico personalizzato:** linee (fino a 4 serie) o barre, raggruppamento per ora o giorno, media/somma/min/max, asse regolabile. Tocca e tieni premuto per leggere i valori.
- **Temperatura e umidità:** valori attuali con tendenza, fasce comfort (zona verde nei grafici), punto di rugiada, min/max, avvisi con notifica quando si esce dai limiti.
- **Sorgenti:** feed di un server Emoncms (chiave di sola lettura) oppure qualunque link pubblico che restituisca JSON o CSV.
- **Pagina web:** mostra un link (https) dentro l'app. Non ne estrae i numeri.

## Come ottenere l'APK (senza installare nulla sul PC)

1. Crea un repository GitHub (anche privato) e carica tutto il contenuto di questa cartella.
2. Apri la scheda **Actions**, scegli **Build APK** e premi **Run workflow** (parte anche a ogni push).
3. A lavoro finito, scarica l'artefatto **quadro-debug-apk** (dentro c'è `app-debug.apk`).
4. Sul telefono: copia il file, aprilo e consenti l'installazione da fonti sconosciute quando richiesto.

Il workflow esegue prima i test del modulo `core` e poi compila. Se un test fallisce, l'APK non viene prodotto e il rapporto è nell'artefatto **quadro-test-report**.

In alternativa: apri la cartella con Android Studio e usa Build → Build APK.

L'APK è firmato con la chiave di debug: va bene per uso personale, non per il Play Store.

## Primo avvio

1. **Server → Aggiungi un server.** Indirizzo (es. `emonpi.local` o `emoncms.org`) e chiave API di sola lettura dalla pagina Account di Emoncms. Il pulsante QR prova a leggere il codice della pagina Account; se non lo riconosce, inserisci i dati a mano. Con **Prova la connessione** controlli subito che funzioni.
2. **+ Scheda** e scegli il tipo.

### Link pubblici (JSON o CSV)

Nell'editor della serie scegli "Link pubblico". Per il JSON indica il percorso dell'elenco (es. `data.points`, vuoto se la radice è già un elenco) e le chiavi di tempo e valore; per gli elenchi di coppie `[tempo, valore]` usa le posizioni 0 e 1. Per il CSV indica nome o posizione delle colonne. Tempi accettati: secondi o millisecondi Unix, ISO-8601, `gg/mm/aaaa hh:mm`.

### FusionSolar

Il link kiosk è una pagina web, non un'API: qui si può **mostrare** con la scheda "Pagina web". Per ottenere numeri da usare nei grafici serve una fonte JSON/CSV (per esempio l'API ufficiale Huawei, con le credenziali del tuo impianto, o un tuo servizio che la esponga). Chi ha il link kiosk vede i dati dell'impianto: non condividerlo.

## Cose da sapere

- **Avvisi:** Android non garantisce l'orario esatto; il controllo è circa ogni 15 minuti, e solo se il telefono ha rete. Su Android 13+ l'app chiede il permesso per le notifiche. Con il risparmio energetico molto aggressivo possono ritardare.
- **Sicurezza:** le chiavi sono in archivio cifrato e viaggiano nell'header `Authorization`, non nell'indirizzo. HTTP in chiaro è consentito perché un emonPi in rete locale spesso non ha HTTPS; l'editor ti avvisa.
- **Barre giornaliere:** si calcolano sommando consumi orari sul fuso del telefono. Per fusi con mezz'ora di scarto le barre possono essere sfalsate.
- **Pagina web:** la scheda esegue JavaScript (serve a FusionSolar) ed è limitata a indirizzi https.

## Stato della verifica

I 32 test del modulo `core` sono stati scritti e riletti, ma **non eseguiti** dove è stato creato il progetto (rete limitata). L'interfaccia non è mai stata compilata né provata su un telefono: al primo avvio del workflow potrebbero esserci errori di compilazione da correggere.

## Struttura

- `core/`: logica in Kotlin puro e testata (parser, statistiche, punto di rugiada, soglie con isteresi, URL Emoncms, configurazione).
- `app/`: interfaccia Jetpack Compose, grafici disegnati a mano, archivio cifrato delle chiavi, avvisi con WorkManager.

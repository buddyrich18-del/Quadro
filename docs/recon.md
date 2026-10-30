# Recon map: Emoncms Android (v2.3.3)

Scope: client Android che mostra i consumi di un server Emoncms (emoncms.org, emonPi o self-hosted): account, pagine My Electric e My Solar. Le funzioni oltre l'app ufficiale sono marcate come "aggiunte".
For: uso personale dell'utente (non commerciale per ora)
Date: 2026-10-30

## Correzione alla versione precedente

La prima mappa elencava 18 schermate, tra cui elenco feed, grafici, dashboard, app, input e device. **Quelle schermate non esistono nell'app Android ufficiale**: le avevo prese dalla documentazione del sito web. L'app reale è un visualizzatore di pagine My Electric e My Solar. Ora la mappa separa ciò che ho verificato nel tuo zip da ciò che arriva dalla documentazione web.

## Cosa ho guardato nello zip

Manifest, build.gradle, README, stringhe in inglese, menu, elenco dei file e delle schermate, nome della tabella del database locale, e lo screenshot `app.png`. Non ho copiato né riassunto la logica del codice. Non ho eseguito nulla.

## Fork o clone: decisione tua

- L'app è AGPL-3.0. Se riusi il suo codice, il risultato resta AGPL-3.0 e va pubblicato con i sorgenti. Una ricostruzione da zero (clean-room) non ha questo vincolo, ma richiede comunque nome e grafica tuoi.
- Il codice è datato: librerie di supporto 26, compileSdk 26, minSdk 14, e la lettura QR usa `play-services-vision`, che è obsoleta. Un fork richiede comunque una modernizzazione pesante.
- Per uso personale entrambe le strade vanno bene. Questo non è un parere legale.

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | zip dell'app (fornito dall'utente) | allegato | v2.3.3, versionCode 147, org.emoncms.myapps: fonte principale |
| 2 | docs: concetti base | https://docs.openenergymonitor.org/emoncms/coreconcepts.html | input, feed, device |
| 3 | docs: grafici | https://docs.openenergymonitor.org/emoncms/graphs.html | per le aggiunte |
| 4 | docs: dashboard builder | https://docs.openenergymonitor.org/emoncms/dashboards.html | per le aggiunte |
| 5 | docs: application dashboards | https://docs.openenergymonitor.org/emoncms/apps.html | per le aggiunte |
| 6 | docs: posting data | https://docs.openenergymonitor.org/emoncms/postingdata.html | chiavi API |
| 7 | API pubblica | https://emoncms.org/site/api.md | endpoint feed, input, device |
| 8 | blog: app Android v1 | https://blog.openenergymonitor.org/2015/12/emoncms-android-app/ | MyElectric, valuta |
| 9 | blog: app Android v2 | https://blog.openenergymonitor.org/2017/06/emoncms-android-app-v2/ | multi-account, QR |
| 10 | forum: v2 alpha | https://community.openenergymonitor.org/t/emoncms-android-app-v2-0-0-alpha-testing/3373 | richieste degli utenti |

## Core loop

Aprire l'app e vedere subito la potenza di adesso, il consumo di oggi e gli ultimi giorni, con la possibilità di passare ai costi.

## Screens

### A. App ufficiale (verificate nello zip)

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | My Electric (home) | avvio | consumo attuale e storico di una pagina | pager con indicatore; testi "Now" e "Today"; grafico potenza; pulsanti 3H 6H D W M; barre kWh con iniziali dei giorni; interruttore kWh/Cost; schermo intero; ingranaggio | server non configurato, pagina non configurata, errore connessione, "nessun dato", feed non valido, ok |
| S02 | Impostazioni pagina | S01, ingranaggio | scegliere feed e costi | nome pagina; feed potenza (W); feed kWh; scelta Wh o kWh; valuta; costo; simbolo; elimina pagina | nuova, modifica, conferma eliminazione |
| S03 | My Solar | menu laterale | produzione solare | etichetta SOLAR PV; stessa struttura di S01 | non configurata, ok |
| S04 | Feed My Solar | S03, ingranaggio | scegliere i feed solari | selettori feed | nuova, modifica |
| S05 | Menu laterale | tocco sull'icona | passare tra account, pagine e sezioni | elenco account; elenco pagine; voci My Electric / My Heating / My Solar; Settings; "Add Page" | uno o più account |
| S06 | Impostazioni | S05 | account e opzioni generali | elenco account; aggiungi account; schermo sempre acceso; lingua; versione app | nessun account ("aggiungi almeno un account") |
| S07 | Impostazioni account | S06 | collegare un server | nome; URL senza http://; API key (sola lettura); usa SSL; scansiona QR; elimina | nuovo, modifica, conferma eliminazione |
| S08 | Scanner QR | S07 | leggere il QR della pagina "My account" | anteprima camera | permesso negato, nessuna camera, QR non valido, QR letto |
| S09 | My Heating | menu laterale | voce presente | - | nessuna schermata trovata: probabile segnaposto |

### B. Aggiunte (dalla documentazione web, non presenti nell'app ufficiale)

| ID | screen | purpose | note |
| --- | --- | --- | --- |
| S10 | Elenco feed | feed per tag con ultimo valore e ora | API feed/list con meta |
| S11 | Visualizzatore grafici | confronto multi-feed, assi sx/dx, intervalli | documentato nei docs grafici |
| S12 | Elenco dashboard e vista | consultare le dashboard del server | nessuna API documentata: vista web con read key |
| S13 | Elenco input e device | verificare che i dati arrivino | API input/list e device/list |
| S14 | My Electric Flow / My Heatpump | app del server in sola lettura | configurazione sul server, accesso via API da verificare |

## Flows

```
F01 Collegare un server con QR
    S05 -> S06 -> S07 -> S08 -> S01
    happy path clicks: 5
    edge: permesso camera negato, QR non valido, server solo in rete locale (HTTP), URL senza http://

F02 Vedere consumo di adesso e di oggi
    S01
    happy path clicks: 0
    edge: feed senza dati recenti, connessione assente, server non configurato

F03 Configurare una pagina
    S01 -> S02 -> salva -> S01
    happy path clicks: 5
    edge: feed sbagliato (watt al posto di kWh), campo costo vuoto, rotazione schermo durante la modifica

F04 Aggiungere una pagina
    S05 -> "Add Page" -> S02 -> S01
    happy path clicks: 4

F05 Cambiare account
    S05 -> account -> S01
    happy path clicks: 2

F06 Cambiare periodo del grafico
    S01 -> 3H / 6H / D / W / M
    happy path clicks: 1

F07 Passare a costi
    S01 -> interruttore kWh/Cost
    happy path clicks: 1

(aggiunte)
F08 Esplorare la storia di un feed: S10 -> S11, 3 click
F09 Consultare una dashboard: S12, 2 click
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Pager con indicatore a punti | pagine My Electric | una pagina, molte | S01, S03 |
| Grafico potenza (linea con area) | pinch-zoom, schermo intero | caricamento, dati, vuoto | S01, S03 |
| Grafico barre giornaliero | kWh, costo | idem | S01, S03 |
| Pulsanti periodo | 3H, 6H, D, W, M | selezionato, non selezionato | S01 |
| Interruttore kWh/costo | - | on, off | S01 |
| Menu laterale | account, pagine, sezioni | aperto, chiuso | tutte |
| Righe di impostazione | testo, interruttore, scelta, valore | normale, errore | S02, S04, S06, S07 |
| Anteprima camera | - | in attesa, letto, permesso negato | S08 |
| Dialogo di conferma | elimina pagina, elimina account | - | S02, S07 |
| Messaggio d'errore a schermo | non configurato, connessione, feed non valido | - | S01, S03 |

## Inferred data model

Sul telefono:

```
Account   nome, url, apikey (lettura), usessl
          evidence: S07, chiavi di impostazione nelle stringhe
          confidence: high (campi); guess (dove sono salvati)

Page      id, account, page_type (myelectric | mysolar), page_def (testo con le impostazioni)
          evidence: tabella locale "emon_page" con queste colonne; S02 per il contenuto
          confidence: high (colonne); guess (formato di page_def)

Impostazioni app   schermo sempre acceso, lingua
          evidence: S06
          confidence: high
```

Dal server (API pubblica): Feed, Input, Device come nella documentazione (feed/list, input/list, device/list); l'app ufficiale usa i feed per scegliere potenza e kWh.

Relationships: Account 1-n Page; Page 1-2 Feed (potenza, kWh); Server 1-n Device; Device 1-n Input; Input 0-n Feed.

## Feature matrix

See `features.csv`. Must: 12, should: 15, could: 8, skip: 8. Di queste, 19 sono funzioni che l'app ufficiale ha davvero (contano per la parità); le altre sono aggiunte.

## Out of scope (cannot or should not be cloned)

- Il server Emoncms, emoncms.org, hardware e firmware: sono il prodotto di OpenEnergyMonitor.
- Dati di terzi (Octopus Agile, rete UK, HeatpumpMonitor.org).
- Nome, logo, icone e testi dell'app ufficiale: rebrand obbligatorio prima di pubblicare.
- Amministrazione del server dal telefono (input processing, motori feed, moduli).

## Size

Screens 9 ufficiali + 5 aggiunte, flows 7 + 2, entities 3 sul telefono + 3 dal server. Hard parts: (1) QR senza la libreria obsoleta (con CameraX e ML Kit); (2) HTTP in chiaro verso emonPi in rete locale, che l'originale consente e Android moderno blocca per default; (3) calcolo dei costi dai feed kWh; (4) grafici fluidi su molti punti; (5) archivio sicuro delle chiavi. Size: **S** per la parità con l'app ufficiale (S01-S09), **M** con le aggiunte S10-S14.

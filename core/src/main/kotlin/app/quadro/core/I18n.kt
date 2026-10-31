package app.quadro.core

/**
 * Traduzione italiano -> inglese dei testi dell'interfaccia.
 * Il testo di partenza è sempre l'italiano: se non c'è una traduzione, resta com'è.
 */
private val EN: Map<String, String> = mapOf(
    // home e comuni
    "Aggiorna" to "Refresh",
    "Server" to "Servers",
    "+ Scheda" to "+ Card",
    "Energia (potenza e kWh)" to "Energy (power and kWh)",
    "Grafico a linee o barre" to "Line or bar chart",
    "Temperatura e umidità" to "Temperature and humidity",
    "Fotovoltaico / pagina web (es. FusionSolar)" to "Solar / web page (e.g. FusionSolar)",
    "Nessuna scheda" to "No cards",
    " per aggiungere un grafico, i consumi o il clima." to " to add a chart, energy use or climate.",
    "Per cominciare aggiungi un server Emoncms, oppure crea una scheda con un link pubblico o una pagina web." to
        "To start, add an Emoncms server, or create a card with a public link or a web page.",
    "Aggiungi un server" to "Add a server",
    "Modifica" to "Edit",
    "Carico i dati…" to "Loading data…",
    "Nessun dato" to "No data",
    "Indietro" to "Back",
    "Salva" to "Save",
    "Elimina" to "Delete",
    "Rimuovi" to "Remove",
    // energia
    "kWh / costo" to "kWh / cost",
    "Adesso" to "Now",
    "Oggi" to "Today",
    "Potenza" to "Power",
    "Energia" to "Energy",
    "Altro" to "Other",
    // clima
    "Temperatura" to "Temperature",
    "Umidità" to "Humidity",
    "Clima" to "Climate",
    "Consumi" to "Energy use",
    "Fotovoltaico" to "Solar",
    "Freddo" to "Cold",
    "Comfort" to "Comfort",
    "Caldo" to "Warm",
    "Aria secca" to "Dry air",
    "Ideale" to "Ideal",
    "Umido" to "Humid",
    "Ora" to "Now",
    "Fascia comfort" to "Comfort range",
    "in aumento" to "rising",
    "in calo" to "falling",
    "stabile" to "steady",
    "La freccia mostra l'andamento nel periodo scelto" to "The arrow shows the trend over the selected period",
    "Avvisi attivi (controllo ogni 15 minuti circa)" to "Alerts on (checked about every 15 minutes)",
    "Avvisi temperatura e umidità" to "Temperature and humidity alerts",
    "media" to "avg",
    "sopra" to "above",
    "sotto" to "below",
    // pagina web / kiosk
    "Potenza in tempo reale" to "Real-time power",
    "Resa di oggi" to "Yield today",
    "Resa del mese" to "Yield this month",
    "Resa dell'anno" to "Yield this year",
    "Resa totale" to "Total yield",
    "Link FusionSolar riconosciuto: l'app legge dalla pagina potenza in tempo reale e rese (oggi, mese, anno, totale) e le mostra a riquadri. La pagina originale resta consultabile." to
        "FusionSolar link recognised: the app reads real-time power and yields (today, month, year, total) from the page and shows them as tiles. The original page stays available.",
    "Per questo indirizzo l'app mostra solo la pagina web." to "For this address the app shows only the web page.",
    "Apri nel browser" to "Open in browser",
    "Leggo i dati dalla pagina…" to "Reading data from the page…",
    "Non riconosco valori in questa pagina. Apri la pagina originale e guarda il testo letto: se serve, mandami una schermata." to
        "No values recognised on this page. Open the original page and check the text read; if needed, send a screenshot.",
    "Mostra la pagina originale" to "Show the original page",
    "Nascondi la pagina originale" to "Hide the original page",
    "Testo letto" to "Text read",
    "Nascondi il testo letto" to "Hide the text read",
    "Nessun testo letto finora." to "No text read yet.",
    // editor comuni
    "Titolo" to "Title",
    "Nome" to "Name",
    "Unità (es. °C, %, W)" to "Unit (e.g. °C, %, W)",
    "Origine" to "Source",
    "Server Emoncms" to "Emoncms server",
    "Link pubblico (JSON o CSV)" to "Public link (JSON or CSV)",
    "Formato" to "Format",
    "Scegli un feed" to "Choose a feed",
    "ID del feed" to "Feed ID",
    "Carico…" to "Loading…",
    "Carica elenco feed" to "Load feed list",
    "Nessun feed trovato" to "No feeds found",
    "Scegli il server" to "Choose the server",
    "Aggiungi prima un server dalla schermata Server." to "First add a server from the Servers screen.",
    "Indirizzo del link (https://…)" to "Link address (https://…)",
    "Percorso dell'elenco (es. data.points, vuoto se è la radice)" to "List path (e.g. data.points, empty if it is the root)",
    "Chiave del tempo (o posizione 0, 1…)" to "Time key (or position 0, 1…)",
    "Chiave del valore (o posizione 0, 1…)" to "Value key (or position 0, 1…)",
    "Colonna del tempo (nome o posizione)" to "Time column (name or position)",
    "Colonna del valore (nome o posizione)" to "Value column (name or position)",
    "l'indirizzo deve iniziare con https://" to "the address must start with https://",
    "scegli un server e un feed, oppure un link" to "choose a server and a feed, or a link",
    "Scrivi un titolo" to "Enter a title",
    // grafico
    "Nuovo grafico" to "New chart",
    "Modifica grafico" to "Edit chart",
    "Tipo" to "Type",
    "Linee" to "Lines",
    "Barre" to "Bars",
    "Aggiungi una serie" to "Add a series",
    "Periodo iniziale" to "Initial period",
    "Raggruppa i dati" to "Group the data",
    "Nessun raggruppamento" to "No grouping",
    "Per ora" to "By hour",
    "Per giorno" to "By day",
    "Come combinare i valori" to "How to combine values",
    "Media" to "Average",
    "Somma" to "Sum",
    "Minimo" to "Minimum",
    "Massimo" to "Maximum",
    "Ultimo" to "Last",
    "Minimo dell'asse (vuoto = automatico)" to "Axis minimum (empty = automatic)",
    "Massimo dell'asse (vuoto = automatico)" to "Axis maximum (empty = automatic)",
    "Colore della linea" to "Line colour",
    // editor clima
    "Nuova scheda clima" to "New climate card",
    "Modifica scheda clima" to "Edit climate card",
    "Fasce comfort (zona verde nei grafici)" to "Comfort ranges (green area in charts)",
    "Temperatura: minimo comfort" to "Temperature: comfort minimum",
    "Temperatura: massimo comfort" to "Temperature: comfort maximum",
    "Umidità: minimo comfort" to "Humidity: comfort minimum",
    "Umidità: massimo comfort" to "Humidity: comfort maximum",
    "Avvisi" to "Alerts",
    "Il controllo avviene circa ogni 15 minuti, quando il telefono è in rete. Si riceve una notifica solo quando il valore esce dai limiti." to
        "The check runs about every 15 minutes while the phone is online. You get a notification only when the value leaves the limits.",
    "Avviso temperatura" to "Temperature alert",
    "Avviso umidità" to "Humidity alert",
    "Avvisa sotto (°C)" to "Alert below (°C)",
    "Avvisa sopra (°C)" to "Alert above (°C)",
    "Avvisa sotto (%)" to "Alert below (%)",
    "Avvisa sopra (%)" to "Alert above (%)",
    "Le fasce comfort devono avere minimo e massimo, con il minimo più basso" to
        "Comfort ranges need a minimum and a maximum, with the minimum lower",
    "Avviso temperatura: indica almeno un limite" to "Temperature alert: set at least one limit",
    "Avviso umidità: indica almeno un limite" to "Humidity alert: set at least one limit",
    // editor energia
    "Nuova scheda energia" to "New energy card",
    "Modifica scheda energia" to "Edit energy card",
    "Scegli il server e i due feed" to "Choose the server and both feeds",
    "Feed potenza (watt)" to "Power feed (watts)",
    "Feed energia cumulativa (per le barre giornaliere)" to "Cumulative energy feed (for the daily bars)",
    "Il feed è in Wh (non in kWh)" to "The feed is in Wh (not kWh)",
    "Costo per kWh" to "Cost per kWh",
    "Simbolo della valuta" to "Currency symbol",
    // editor web
    "Nuova scheda fotovoltaico / web" to "New solar / web card",
    "Modifica scheda fotovoltaico / web" to "Edit solar / web card",
    "Usa un indirizzo che inizia con https://" to "Use an address starting with https://",
    "Indirizzo della pagina (es. il link pubblico di FusionSolar)" to "Page address (e.g. the public FusionSolar link)",
    "Leggi i valori dalla pagina e mostrali a riquadri" to "Read the values from the page and show them as tiles",
    "Mostra la pagina così com'è dentro l'app." to "Show the page as it is inside the app.",
    // server e impostazioni
    "Server e impostazioni" to "Servers and settings",
    "Nessun server. Puoi usare anche solo link pubblici e pagine web." to "No servers. You can also use just public links and web pages.",
    "Aspetto" to "Appearance",
    "Sfondo" to "Background",
    "Automatico (come il telefono)" to "Automatic (as the phone)",
    "Sfondo chiaro" to "Light background",
    "Sfondo scuro" to "Dark background",
    "Dimensione del testo" to "Text size",
    "Piccolo" to "Small",
    "Normale" to "Normal",
    "Grande" to "Large",
    "Molto grande" to "Very large",
    "Lingua" to "Language",
    "Automatica (come il telefono)" to "Automatic (as the phone)",
    "Schermo sempre acceso" to "Keep screen on",
    "Cosa mostra il salvaschermo" to "What the screensaver shows",
    "Mostra: orologio su fondo nero" to "Shows: clock on black",
    "Mostra: le schede a rotazione" to "Shows: the cards in rotation",
    "Cambio scheda" to "Card change",
    "Cambio scheda ogni minuto" to "Card change every minute",
    "Salvaschermo a schermo intero (nasconde le barre del telefono e i pulsanti)" to "Full-screen screensaver (hides the phone bars and buttons)",
    "Salvaschermo: disattivato" to "Screensaver: off",
    "Salvaschermo dopo 1 minuto" to "Screensaver after 1 minute",
    "Salvaschermo dopo 5 minuti" to "Screensaver after 5 minutes",
    "Salvaschermo dopo 15 minuti" to "Screensaver after 15 minutes",
    "Salvaschermo dopo 30 minuti" to "Screensaver after 30 minutes",
    "Nuovo server" to "New server",
    "Modifica server" to "Edit server",
    "Servono nome, indirizzo valido e chiave API" to "Name, valid address and API key are required",
    "Impossibile salvare la chiave in modo sicuro su questo telefono" to "Cannot store the key securely on this phone",
    "Nome (es. Casa)" to "Name (e.g. Home)",
    "Indirizzo (es. emonpi.local oppure emoncms.org)" to "Address (e.g. emonpi.local or emoncms.org)",
    "Chiave API di sola lettura" to "Read-only API key",
    "Connessione non cifrata (HTTP): la chiave viaggia in chiaro. Usala solo in rete locale." to
        "Unencrypted connection (HTTP): the key travels in clear text. Use it only on a local network.",
    "QR letto" to "QR read",
    "QR non riconosciuto: inserisci i dati a mano" to "QR not recognised: enter the details by hand",
    "Scansione non riuscita" to "Scan failed",
    "Scansiona il QR della pagina Account di Emoncms" to "Scan the QR on the Emoncms Account page",
    "Provo…" to "Trying…",
    "Connessione riuscita" to "Connection successful",
    "Prova la connessione" to "Test the connection",
    // errori
    "Dati non validi" to "Invalid data",
    "Server non raggiungibile" to "Server unreachable",
    "Tempo scaduto" to "Timed out",
    "Errore di rete" to "Network error",
    "Errore" to "Error",
    "Accesso negato: controlla la chiave API" to "Access denied: check the API key",
    "Risposta troppo grande" to "Response too large",
    "Server non trovato: controlla la scheda" to "Server not found: check the card",
    "Nessun dato nel periodo scelto" to "No data in the selected period",
    "Serie non configurata" to "Series not configured",
    "Indirizzo non valido" to "Invalid address",
    "La risposta non è nel formato atteso" to "The response is not in the expected format",
    "Risposta inattesa dal server" to "Unexpected response from the server",
    "Valore non valido dal server" to "Invalid value from the server",
    "Percorso non trovato" to "Path not found",
    "Il percorso indicato non porta a un elenco" to "The given path does not lead to a list",
    "Indica la chiave del tempo e quella del valore" to "Give both the time key and the value key",
    "Nessun dato valido nel link" to "No valid data in the link",
    "Il file CSV è vuoto" to "The CSV file is empty",
    "Nessun dato valido nel CSV" to "No valid data in the CSV",
    "Colonna non trovata" to "Column not found",
)

private val EN_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("^Serie (\\d+)$") to "Series $1",
    Regex("^Cambio scheda ogni (\\d+) secondi$") to "Card change every $1 seconds",
    Regex("^Cambio scheda ogni (\\d+) minuti$") to "Card change every $1 minutes",
    Regex("^Luminosità del salvaschermo: (.+)$") to "Screensaver brightness: $1",
    Regex("^(\\d+) feed trovati$") to "$1 feeds found",
    Regex("^Errore del server \\((\\d+)\\)$") to "Server error ($1)",
    Regex("^Letto alle (.+) · si aggiorna ogni 5 minuti$") to "Read at $1 · updates every 5 minutes",
    Regex("^Fascia comfort (.+)$") to "Comfort range $1",
    Regex("^zona verde = fascia comfort (.+)$") to "green area = comfort range $1",
    Regex("^Temperatura \\((.+)\\)$") to "Temperature ($1)",
    Regex("^Umidità \\((.+)\\)$") to "Humidity ($1)",
)

/** Traduce un testo dell'interfaccia dall'italiano all'inglese; se non lo conosce lo lascia com'è. */
fun toEnglish(text: String): String {
    if (text.isBlank()) return text
    val lead = text.takeWhile { it == ' ' }
    val body = text.substring(lead.length)
    return lead + translateBody(body)
}

private fun translateBody(body: String): String {
    EN[body]?.let { return it }
    for ((re, rep) in EN_PATTERNS) {
        if (re.matches(body)) return re.replace(body, rep)
    }
    if (body.length > 2 && body[1] == ' ' && body[0] in "↑↓→") return body.substring(0, 2) + translateBody(body.substring(2))
    val i = body.indexOf(": ")
    if (i > 0) {
        val head = body.substring(0, i)
        val tail = body.substring(i + 2)
        val h = EN[head] ?: Regex("^Serie (\\d+)$").replace(head, "Series $1").takeIf { it != head }
        if (h != null) return h + ": " + translateBody(tail)
    }
    return body
}

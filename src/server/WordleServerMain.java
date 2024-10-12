package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import com.google.gson.stream.JsonReader;

public class WordleServerMain {

	// file di configurazione Server
	private static final String configFile = "./server/resources/server.properties";

	// file JSON salvataggio stato
	private static final String jsonFile = "./server/resources/stato.json";

	// file vocabolario gioco
	private static final String wordsFile = "./server/resources/words.txt";

	// numero porta di ascolto del Server (da file configurazione)
	private static int porta;

	// indirizzo e porta gruppo multicast (da file configurazione)
	private static String indirizzoMulticast;
	private static int portaMulticast;

	// tempo refresh parola (minuti) (da file configurazione)
	private static int tempoRefreshParola;

	// attesa massima chiusura thread pool server e scheduler cambio parola
	// (secondi) (da file configurazione)
	private static int attesaMassima;

	// pool di thread per gestione client
	private static final ExecutorService pool = Executors.newCachedThreadPool();

	// parola da indovinare inizializzata con una parola fittizia poi modificata con
	// i metodi set dell'oggetto
	private static final Parola parola = new Parola("", 0, 0);

	// struttura dati parole usate in passato
	private static final Set<String> parolePassate = new HashSet<String>();

	// struttura dati gestione utenti
	private static final Map<String, DatiUtente> utenti = new HashMap<String, DatiUtente>();

	public static void main(String[] args) {
		try {

			// lettura file di configurazione Server
			readConfig();

			// ripristino stato salvato nel JSON
			ripristinoStato(parola, utenti);

			// creazione ServerSocket
			try (ServerSocket serverSocket = new ServerSocket(porta)) {

				// calcolo tempo rimasto scadenza parola
				long tempoRimastoParola = parola.getScadenza() - System.currentTimeMillis();

				// se la parola e' scaduta tempo rimasto e' 0
				if (tempoRimastoParola < 0)
					tempoRimastoParola = 0;

				// creazione ScheduledExecutorService per cambio parola ogni scadenza
				ScheduledExecutorService schedulerParola = Executors.newScheduledThreadPool(1);

				// avvio thread cambio parola
				schedulerParola.scheduleAtFixedRate(new Runnable() {
					@Override
					public void run() {
						try {
							synchronized (parola) {
								nuovaParola(parola);
							}
						} catch (IOException e) {
							System.err.println("Errore I/O: " + e.getMessage());
							System.exit(1);
						}
					}
				}, tempoRimastoParola, tempoRefreshParola * 60 * 1000, TimeUnit.MILLISECONDS);

				// Configurazione handler di terminazione, si occupa di salvare lo stato e
				// di chiudure le risorse
				Runtime.getRuntime().addShutdownHook(new TerminationHandler(attesaMassima, pool, serverSocket, parola,
						utenti, jsonFile, schedulerParola));

				System.out.println("\nCTRL+C per chiudere il server\n");

				// inizializzazione InetAddress multicast
				InetAddress indirizzoMS = null;
				try {
					indirizzoMS = InetAddress.getByName(indirizzoMulticast);
				} catch (UnknownHostException e) {
					System.err.println("Errore indirizzo multicast");
					System.exit(1);
				}

				// Ciclo accettazione richieste di connesione client
				while (true) {
					try {
						Socket socket = serverSocket.accept();
						pool.execute(
								new WordleServerThread(socket, indirizzoMS, portaMulticast, utenti, parola, wordsFile));
					} catch (SocketException e) {
						// eccezione sollevata quando viene eseguito il TerminationHandler
						break;
					}
				}
			}

		} catch (FileNotFoundException e) {
			System.err.println("\nFile non trovato: " + e.getMessage());
			System.exit(1);
		} catch (IOException e) {
			System.err.println("\nErrore I/O: " + e.getMessage());
			System.exit(1);
		}

	}

	/**
	 * Metodo per leggere il file di configurazione del server.
	 * 
	 * @throws FileNotFoundException se il file non viene trovato
	 * @throws IOException           se si verifica un errore durante la lettura
	 */
	private static void readConfig() throws FileNotFoundException, IOException {
		try (FileInputStream input = new FileInputStream(configFile)) {
			Properties prop = new Properties();
			prop.load(input);
			porta = Integer.parseInt(prop.getProperty("porta"));
			indirizzoMulticast = prop.getProperty("indirizzoMulticast");
			portaMulticast = Integer.parseInt(prop.getProperty("portaMulticast"));
			tempoRefreshParola = Integer.parseInt(prop.getProperty("tempoRefreshParola"));
			attesaMassima = Integer.parseInt(prop.getProperty("attesaMassima"));
		}
	}

	/**
	 * Metodo per ripristinare lo stato salvato dal file JSON. Se non esiste il file
	 * viene creato vuoto e viene scelta una parola casuale.
	 * 
	 * @param parola riferimento condiviso alla parola
	 * @param utenti struttura dati condivisa per la gestione degli utenti
	 * 
	 * 
	 * @throws IOException se si verifica un errore durante la lettura
	 */
	private static void ripristinoStato(Parola parola, Map<String, DatiUtente> utenti) throws IOException {

		try (JsonReader jsonReader = new JsonReader(new BufferedReader(new FileReader(jsonFile)))) {

			// inizio JSON
			jsonReader.beginObject();

			while (jsonReader.hasNext()) {
				// lettura etichetta
				String name = jsonReader.nextName();

				if ("parola".equalsIgnoreCase(name)) {
					parola.setParola(jsonReader.nextString());
				} else if ("id".equalsIgnoreCase(name)) {
					parola.setId(jsonReader.nextInt());
				} else if ("scadenza".equalsIgnoreCase(name)) {
					parola.setScadenza(jsonReader.nextLong());
				} else if ("utenti".equalsIgnoreCase(name)) {
					// inizio array utenti
					jsonReader.beginArray();

					while (jsonReader.hasNext()) {
						String username = "";
						String password = "";
						int partiteGiocate = 0;
						int partiteVinte = 0;
						int miglioreStreakVittorie = 0;
						int streakVittorieInCorso = 0;
						int[] distribuzioneTentativiImpiegati = new int[12];
						long scadenzaParolaGiocata = 0;

						// apertura oggetto utente
						jsonReader.beginObject();

						while (jsonReader.hasNext()) {
							name = jsonReader.nextName();

							if ("username".equalsIgnoreCase(name)) {
								username = jsonReader.nextString();
							} else if ("password".equalsIgnoreCase(name)) {
								password = jsonReader.nextString();
							} else if ("partiteGiocate".equalsIgnoreCase(name)) {
								partiteGiocate = jsonReader.nextInt();
							} else if ("partiteVinte".equalsIgnoreCase(name)) {
								partiteVinte = jsonReader.nextInt();
							} else if ("miglioreStreakVittorie".equalsIgnoreCase(name)) {
								miglioreStreakVittorie = jsonReader.nextInt();
							} else if ("streakVittorieAttuale".equalsIgnoreCase(name)) {
								streakVittorieInCorso = jsonReader.nextInt();
							} else if ("distribuzioneTentativi".equalsIgnoreCase(name)) {
								// apertura array distribuzione
								jsonReader.beginArray();

								for (int i = 0; i < 12; i++) {
									distribuzioneTentativiImpiegati[i] = jsonReader.nextInt();
								}
								// chiusura array distribuzione
								jsonReader.endArray();
							} else if ("scadenzaParolaGiocata".equalsIgnoreCase(name)) {
								scadenzaParolaGiocata = jsonReader.nextLong();
							} else {
								System.out.println("Errore JSON ripristino stato");
								System.exit(1);

							}
						}

						// chiusura oggetto utente
						jsonReader.endObject();

						// creazione utente presente nel JSON
						DatiUtente utente = new DatiUtente(username, password, partiteGiocate, partiteVinte,
								miglioreStreakVittorie, streakVittorieInCorso, distribuzioneTentativiImpiegati,
								scadenzaParolaGiocata);

						// aggiunta alla struttura dati che rappresenta gli utenti
						utenti.put(username, utente);
					}

					jsonReader.endArray();
				} else {
					System.out.println("Errore JSON ripristino stato");
					System.exit(1);
				}
			}

			// fine JSON
			jsonReader.endObject();

		} catch (FileNotFoundException e) {
			// file JSON non presente, creato uno nuovo vuoto
			File fileJson = new File(jsonFile);
			fileJson.createNewFile();
			System.out.println("File JSON creato");

			// non esiste parola attuale, scelta una nuova
			nuovaParola(parola);
		}

	}

	/**
	 * Metodo che estrae dal vocabolario una parola casuale e imposta la sua
	 * scadenza
	 * 
	 * @param parola riferimento condiviso alla parola
	 * 
	 * @throws IOException se il file vocabolario non viene trovato o si verificano
	 *                     errori di lettura
	 */
	private static void nuovaParola(Parola parola) throws IOException {
		// apertura vocabolario
		try (RandomAccessFile vocabolario = new RandomAccessFile(wordsFile, "r")) {

			int byteParola = 11;

			String parolaRandom;
			int rand;
			while (true) {
				// numero casuale scelta parola
				rand = ThreadLocalRandom.current().nextInt(0, (int) (vocabolario.length() / byteParola));

				// spostamento al byte random del file
				vocabolario.seek(rand * byteParola);

				// lettura parola
				parolaRandom = vocabolario.readLine();

				// si continua se la parola scelta non e' gia' stata usata
				if (!parolePassate.contains(parolaRandom))
					break;

				System.out.println("Altra parola");
			}

			parolePassate.add(parolaRandom);
			parola.setParola(parolaRandom);
			parola.setId(rand + 1);
			parola.setScadenza(System.currentTimeMillis() + tempoRefreshParola * 60 * 1000);

			System.out.println("Nuova parola: " + parola.getParola() + "\tID: " + parola.getId() + "\tScadenza: "
					+ parola.getScadenza() + "\n");
		}
	}

}

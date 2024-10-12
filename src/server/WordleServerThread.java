package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public class WordleServerThread implements Runnable {

	// codici richiesta
	private static final int codiceRegistrazione = 10;
	private static final int codiceLogin = 20;
	private static final int codiceGioca = 30;
	private static final int codiceStatistiche = 40;
	private static final int codiceEsci = 50;
	private static final int codiceCondividi = 60;

	// codici risposta
	private static final int codiceOK = 200;
	private static final int finePartita = 201;
	private static final int codiceErrore = 300;

	// socket
	private final Socket socket;

	// indirizzo e porta multicast per condivisione
	private final InetAddress indirizzoMulticast;
	private final int portaMulticast;

	// struttura dati gestione utenti
	private final Map<String, DatiUtente> utenti;

	// dati utente della sessione
	private DatiUtente utente;

	// parola da indovinare
	private final Parola parola;

	// file parole gioco
	private final String wordsFile;

	public WordleServerThread(Socket socket, InetAddress indirizzoMulticast, int portaMulticast,
			Map<String, DatiUtente> utenti, Parola parola, String wordsFile) {
		this.socket = socket;
		this.indirizzoMulticast = indirizzoMulticast;
		this.portaMulticast = portaMulticast;
		this.utenti = utenti;
		this.parola = parola;
		this.wordsFile = wordsFile;
		// utente inizializzato alla registrazione/login
	}

	@Override
	public void run() {

		// inizializzazione stream da socket
		try (DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

			// gestione registrazione o login e sessione
			boolean continua = true;
			while (continua) {
				// lettura richiesta utente
				String[] parteRichiesta = in.readUTF().split(";");

				switch (Integer.parseInt(parteRichiesta[0])) {

				// registrazione o login
				case codiceRegistrazione:
				case codiceLogin:
					gestisciRegistrazioneLogin(parteRichiesta, out);
					break;

				// fase di gioco
				case codiceGioca:
					gestisciIndovinaParola(in, out);
					break;

				// mostra statistiche
				case codiceStatistiche:
					String risposta = codiceOK + ";" + utente.getStatistiche();
					out.writeUTF(risposta);
					break;

				case codiceEsci:
					continua = false;
					break;
				}
			}

		} catch (IOException e) {
			System.err.println("\nErrore I/O: " + e.getMessage() + "\n");
		} finally {
			// se l'utente aveva fatto login si esegue logout
			if (utente != null) {
				utente.setLoggato(false);
			}

			// socket passata per parametro, no try-with-resources
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	/**
	 * Metodo che gestisce la registrazione o il login. Se il procedimento va a buon
	 * fine la variabile utente viene inizializzata con l'oggetto DatiUtente
	 * dell'utente gestito
	 * 
	 * @param parteRichiesta (codice;username;password)
	 * @param out            stream di scrittura su socket
	 * 
	 * @throws IOException errore di scrittura su socket
	 */
	private void gestisciRegistrazioneLogin(String[] parteRichiesta, DataOutputStream out) throws IOException {

		String risposta;

		// recupero codice, username e password dalla richiesta
		int codiceRichiesta = Integer.parseInt(parteRichiesta[0]);
		String username = parteRichiesta[1];
		String password = parteRichiesta[2];

		// piu' thread potrebbero gestire la registrazione/login dello stesso username
		synchronized (utenti) {

			// fase di registrazione/login
			utente = utenti.get(username);

			// controllo se utente gia' loggato
			if (utente != null && utente.isLoggato()) {
				risposta = codiceErrore
						+ (codiceRichiesta == codiceRegistrazione ? ";Username gia' usato\n" : ";Utente gia loggato\n");
				out.writeUTF(risposta);
				return;
			}

			if (codiceRichiesta == codiceRegistrazione) {
				// registrazione

				if (utente != null) {
					// username già esistente
					risposta = codiceErrore + ";Username gia' usato\n";
					out.writeUTF(risposta);
					return;
				} else {
					// hashing password
					password = hashPassword(password);

					// creazione nuovo utente
					utente = new DatiUtente(username, password);
					utenti.put(username, utente);

					// risposta di successo
					risposta = codiceOK + ";Registrato con successo\n";
					out.writeUTF(risposta);
				}
			} else {
				// login

				if (utente == null) {
					// username non presente
					risposta = codiceErrore + ";Utente non presente\n";
					out.writeUTF(risposta);
					return;
				} else {
					// hashing password
					password = hashPassword(password);

					if (!password.equals(utente.getHashPassword())) {
						// password errata
						risposta = codiceErrore + ";Password errata\n";
						out.writeUTF(risposta);
						return;
					} else {
						// dati corretti
						risposta = codiceOK + ";Login effettuato con successo\n";
						out.writeUTF(risposta);
					}
				}
			}

			// utente loggato
			utente.setLoggato(true);
		}
	}

	/**
	 * Metodo che calcola la funzione hash (SHA-256) della password
	 * 
	 * @param password stringa su cui effettuare l'hash
	 * 
	 * @return stringa risultante dall'hash della password
	 */
	private String hashPassword(String password) {

		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			byte[] hash = messageDigest.digest(password.getBytes(StandardCharsets.UTF_8));

			// conversione byte array nella rappresentazione numerica
			BigInteger numberoHash = new BigInteger(1, hash);

			// conversione in valore esadecimale
			StringBuilder hashedPassword = new StringBuilder(numberoHash.toString(16));

			// pad con 0 iniziali
			while (hashedPassword.length() < 64) {
				hashedPassword.insert(0, '0');
			}

			password = hashedPassword.toString();

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return password;
	}

	/**
	 * Metodo che gestisce la partita di un utente
	 * 
	 * @param in  stream lettura dalla socket
	 * @param out stream lettura sulla socket
	 * 
	 * @throws IOException errore lettura/scrittura su socket
	 */
	private void gestisciIndovinaParola(DataInputStream in, DataOutputStream out) throws IOException {

		// copia locale dell'oggetto parola da indovinare
		Parola parolaLocale;
		synchronized (parola) {
			parolaLocale = new Parola(parola.getParola(), parola.getId(), parola.getScadenza());
		}

		String risposta;

		// controllo se l'utente ha gia' giocato la parola in corso
		long scadenzaParolaGiocata = utente.getScadenzaParolaGiocata();
		if (scadenzaParolaGiocata == parolaLocale.getScadenza()) {
			// utente ha gia' giocato
			risposta = codiceErrore + ";\nHai gia' giocato, aspetta la prossima parola\n";
			out.writeUTF(risposta);
			return;
		}

		// utente non ha gia' giocato
		utente.setPartiteGiocate(utente.getPartiteGiocate() + 1);
		utente.setScadenzaParolaGiocata(parolaLocale.getScadenza());

		// se l'utente esce forzatamente la partita è considerata persa
		int streakVittorie = utente.getStreakVittorieInCorso();
		utente.setStreakVittorieInCorso(0);

		risposta = codiceOK + ";\nParola " + parolaLocale.getId() + ":";
		out.writeUTF(risposta);

		String tentativiPerCondivisione = "Condiviso da " + utente.getUsername() + " - Parola " + parolaLocale.getId()
				+ ":\n";
		int round = 1;
		String tentativo;

		// apertura file vocabolario per controllo tentativi
		try (RandomAccessFile vocabolario = new RandomAccessFile(wordsFile, "r")) {

			while (round <= 12) {
				// get campo parola nella classe Parola
				StringBuilder parolaStringa = new StringBuilder(parolaLocale.getParola());
				// stringa che rappresenta il risultato del tentativo
				StringBuilder risultato = new StringBuilder("");

				// tentativo letto attraverso la socket
				tentativo = in.readUTF();

				// controllo se utente esce
				if ("exit".equals(tentativo))
					return;

				// controllo parola nel vocabolario
				if (!binarySearch(vocabolario, tentativo)) {
					// tentativo non valido
					risposta = codiceErrore + ";Parola non valida\n";
					out.writeUTF(risposta);
					continue;
				}

				for (int i = 0; i < parolaStringa.length(); i++) {
					if (tentativo.charAt(i) == parolaStringa.charAt(i)) {
						// carattere nella posizione corretta
						risultato.append('+');

						// il carattere nella posizione esatta non deve piu essere considerao
						parolaStringa.setCharAt(i, '-');
					} else {
						// carattere non presente o nella posizione non corretta, x placeholder
						risultato.append('x');
					}
				}

				if (risultato.indexOf("x") == -1) {
					// parola indovinata
					streakVittorie++;

					// aggiornamento statistiche utente
					utente.setPartiteVinte(utente.getPartiteVinte() + 1);

					utente.setStreakVittorieInCorso(streakVittorie);

					if (streakVittorie > utente.getMiglioreStreakVittorie())
						utente.setMiglioreStreakVittorie(streakVittorie);

					int[] distribuzioneTentativi = utente.getDistribuzioneTentativiImpiegati();
					distribuzioneTentativi[round - 1]++;

					// memorizzazione risultato round per condivisione
					tentativiPerCondivisione += "- Tentativo nr. " + round + ": " + risultato.toString() + "\n";

					// invio risultato all'utente
					risposta = finePartita + ";" + "Parola corretta! Indovinata al tentativo nr. " + round + "\n";
					out.writeUTF(risposta);

					// si esce dal ciclo while
					break;
				}

				// almeno un carattere non corretto

				int i = 0; // offset per caratteri gia' controllati
				int k; // posizione carattere non corretto
				while ((k = risultato.indexOf("x", i)) != -1) {
					// fino a quando ci sono lettere non controllate

					int j;
					String c = tentativo.charAt(k) + ""; // lettera non controllata
					if ((j = parolaStringa.indexOf(c)) != -1) {
						// carattere 'c' in posizione 'k' sbagliata ma presente in posizione 'j'
						risultato.setCharAt(k, '?');
						// carattere non deve essere piu' considerato per altri posizioni
						parolaStringa.setCharAt(j, '-');
					}
					// se non entrato nell'if il carattere non e' presente nella parola

					// prossima lettera da controllare
					i = k + 1;
				}

				if (round == 12) {
					// ultimo tentativo errato, l'utente non e' riuscito ad indovinare,
					// aggiornamento statistiche e invio risultato
					risposta = finePartita + ";Tentativo nr. " + round + ": " + risultato.toString()
							+ "\n\nTentativi possibili terminati, riprova alla prossima parola\n";
					out.writeUTF(risposta);
				} else {
					// invio risultato all'utente
					risposta = codiceOK + ";Tentativo nr. " + round + ": " + risultato.toString() + "\n";
					out.writeUTF(risposta);
				}

				// memorizzazione risultato round per condivisione
				tentativiPerCondivisione += "- Tentativo nr. " + round + ": " + risultato.toString() + "\n";

				// prossimo round
				round++;
			}
		}

		// scelta utente sulla condivisione del risultato
		String[] parteRichiesta = in.readUTF().split(";");

		if (Integer.parseInt(parteRichiesta[0]) != codiceCondividi) {
			// utente non vuole condividere
			return;
		}

		// utente vuole condividere
		try (DatagramSocket socket = new DatagramSocket()) {

			// creazione pacchetto da inviare in multicast
			DatagramPacket pacchetto = new DatagramPacket(tentativiPerCondivisione.getBytes(),
					tentativiPerCondivisione.getBytes().length, indirizzoMulticast, portaMulticast);

			// invio risultato in multicast
			socket.send(pacchetto);

			// conferma all'utente
			risposta = codiceOK + ";\nRisultato condiviso\n";
			out.writeUTF(risposta);
		}
	}

	/**
	 * Esegue una ricerca binaria della parola specificata nel vocabolario.
	 * 
	 * @param vocabolario riferimento al file delle parole
	 * @param parola      parola cercata nel vocabolario
	 * 
	 * @return se la parola viene trovata, restituisce TRUE. Altrimenti restituisce
	 *         FALSE.
	 * 
	 * @throws IOException errore lettura dal file
	 */
	public static boolean binarySearch(RandomAccessFile vocabolario, String parola) throws IOException {
		final int byteParola = 11;

		final int numParole = ((int) vocabolario.length()) / byteParola;
		int lower = 0;
		int upper = numParole - 1;
		int mid;

		while (lower <= upper) {
			mid = (lower + upper) / 2;
			vocabolario.seek(mid * byteParola);
			String value = vocabolario.readLine();

			int confronto = parola.compareTo(value);
			
			if (confronto == 0)
				return true;
			
			if (confronto < 0)
				upper = mid - 1;
			else
				lower = mid + 1;
		}
		
		return false;
	}

}

package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class WordleClientMain {

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

	// file di configurazione Client
	private static final String configFile = "./client/resources/client.properties";

	// indirizzo e porta gruppo multicast (da file configurazione)
	private static String indirizzoMulticast;
	private static int portaMulticast;

	// indirizzo e numero di porta del Server (da file configurazione)
	private static String indirizzoServer;
	private static int porta;

	public static void main(String[] args) {

		// lettura file di configurazione Client
		try {
			readConfig();
		} catch (FileNotFoundException e) {
			System.out.println("File non trovato: " + e.getMessage());
			System.exit(1);
		} catch (IOException e) {
			System.out.println("Errore lettura file di configurazione: " + e.getMessage());
			System.exit(1);
		}

		// connessione con il server, recupero stream associati a socket e creazione
		// scanner per input da command line
		try (Socket socket = new Socket(indirizzoServer, porta);
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				Scanner scan = new Scanner(System.in)) {

			System.out.println("Benvenuto in Wordle!");

			// gestione login o registrazione
			boolean utenteLoggato = false;
			int scelta = 0;

			while (!utenteLoggato) {
				System.out.println("\n\nImmetti:\n1 - Registrazione\n2 - Login\n3 - Esci");

				try {
					// utente immette numero da tastiera
					scelta = Integer.parseInt(scan.nextLine().trim());

					switch (scelta) {
					case 1:
					case 2:
						utenteLoggato = registrazioneLogin(scelta, in, out, scan);
						break;

					case 3:
						esci(out, null);
						return;

					default:
						System.out.println("\nScelta non valida");
					}

				} catch (NumberFormatException e) {
					// non è stato inserito un numero
					continue;
				}
			}

			// creazione struttura dati temporanea per risultati condivisi
			List<String> condivisioni = Collections.synchronizedList(new ArrayList<String>());

			// avvio thread gestione risultati condivisi
			Thread threadCondivisioni = new ThreadCondivisioni(indirizzoMulticast, portaMulticast, condivisioni);
			threadCondivisioni.start();

			// ciclo gestione sessione
			boolean continua = true;
			while (continua) {

				System.out.println(
						"\nImmetti:\n1 - Indovina parola\n2 - Statistiche profilo\n3 - Mostra bacheca risultati condivisi\n4 - Logout");

				try {
					// utente immette numero da tastiera
					scelta = Integer.parseInt(scan.nextLine().trim());

					switch (scelta) {
					case 1:
						indovinaParola(in, out, scan);
						break;

					case 2:
						statisticheProfilo(in, out);
						System.out.println("\nInvio per continuare");
						scan.nextLine();
						break;

					case 3:
						mostraCondivisioni(condivisioni);
						System.out.println("Invio per continuare");
						scan.nextLine();
						break;

					case 4:
						esci(out, threadCondivisioni);
						continua = false;
						break;

					default:
						System.out.println("\nScelta non valida\n");

					}

				} catch (NumberFormatException e) {
					// non è stato inserito un numero
					continue;
				}
			}

		} catch (ConnectException e) {
			System.out.println("\nErrore connessione server: " + e.getMessage());
			System.exit(1);
		} catch (IOException e) {
			System.out.println("\nErrore I/O: " + e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Metodo per leggere il file di configurazione del client.
	 * 
	 * @throws FileNotFoundException se il file non e' presente
	 * @throws IOException           errore in fase di lettura
	 */
	private static void readConfig() throws FileNotFoundException, IOException {
		try (FileInputStream input = new FileInputStream(configFile)) {
			Properties prop = new Properties();
			prop.load(input);
			indirizzoServer = prop.getProperty("hostname");
			porta = Integer.parseInt(prop.getProperty("porta"));
			portaMulticast = Integer.parseInt(prop.getProperty("portaMulticast"));
			indirizzoMulticast = prop.getProperty("indirizzoMulticast");
		}
	}

	/**
	 * Metodo che gestisce la registrazione o il login dell'utente. Una
	 * registrazione andata a buon fine implica il login automatico.
	 * 
	 * @param scelta 1 = registrazione / 2 = login
	 * @param in     stream lettura da socket
	 * @param out    stream scrittura su socket
	 * @param scan   scanner per input utente da command line
	 * 
	 * @throws IOException errore lettura o scrittura sulla socket
	 * 
	 * @return TRUE se l'utente al termine del metodo ha eseguito il login con
	 *         successo, FALSE altrimenti
	 */
	private static boolean registrazioneLogin(int scelta, DataInputStream in, DataOutputStream out, Scanner scan)
			throws IOException {

		System.out.println("\nFase di " + (scelta == 1 ? "registrazione" : "login")
				+ " (immettere EXIT come Username per tornare indietro)");

		System.out.println("Utente e password devono contenere da 4 a 20 caratteri alfanumerici (A-Z, a-z, 0-9)");

		// ciclo gestione registrazine/login
		boolean continua = true;
		while (continua) {

			// utente immette username da tastiera
			System.out.println("\nUsername: ");
			String username = scan.nextLine().trim();

			// verifica se utente vuole uscire
			if ("exit".equalsIgnoreCase(username))
				return false;

			// utente immette password da tastiera
			System.out.println("Password: ");
			String password = scan.nextLine().trim();

			if (!username.matches("^[A-Za-z0-9]{4,20}$") || !password.matches("^[A-Za-z0-9]{4,20}$")) {
				System.out.println("\nUsername / Password non conformi\n");
				continue;
			}

			// invio richiesta
			String richiesta = (scelta == 1 ? codiceRegistrazione : codiceLogin) + ";" + username + ";" + password;
			out.writeUTF(richiesta);

			// ricezione risposta
			String parteRisposta[] = in.readUTF().split(";");

			// stampa risposta
			System.out.println("\n" + parteRisposta[1]);

			// verifica se username e password OK
			if (Integer.parseInt(parteRisposta[0]) == codiceOK)
				continua = false;

		}

		// registrazione/login a buon fine
		return true;
	}

	/**
	 * Metodo che fa uscire il client inviando un messaggio al server e
	 * interrompendo il thread che gestisce i risultati condivisi
	 * 
	 * @param out                stream scrittura su socket
	 * @param threadCondivisioni thread che gestisce i risultati condivisi da
	 *                           interrompere
	 * 
	 * @throws IOException errore scrittura sulla socket
	 */
	private static void esci(DataOutputStream out, Thread threadCondivisioni) throws IOException {
		// invio richiesta di uscire
		String richiesta = codiceEsci + ";";
		out.writeUTF(richiesta);

		// solo se chiamato dopo il login
		if (threadCondivisioni != null) {
			// stop thread gestione condivisioni
			threadCondivisioni.interrupt();
			try {
				threadCondivisioni.join();
			} catch (InterruptedException e) {
				System.out.println("Errore chiusura thread: " + e.getMessage());
				System.exit(1);
			}
		}
	}

	/**
	 * Metodo che gestisce la partita dell'utente
	 * 
	 * @param in   stream lettura su socket
	 * @param out  stream scrittura su socket
	 * @param scan scanner per input da tastiera
	 * 
	 * @throws IOException errore lettura/scrittura su socket
	 */
	private static void indovinaParola(DataInputStream in, DataOutputStream out, Scanner scan) throws IOException {

		// richiesta di giocare al server
		String richiesta = codiceGioca + ";";
		out.writeUTF(richiesta);

		// risposta server
		String[] parteRisposta = in.readUTF().split(";");

		System.out.println(parteRisposta[1]);

		if (Integer.parseInt(parteRisposta[0]) == codiceErrore) {
			// l'utente ha gia' giocato
			return;
		}

		// ciclo gestione tentativi
		while (true) {
			// utente immette stringa da tastiera
			System.out.println("\nInserisci parola (EXIT arrenderti): ");
			String tentativo = scan.nextLine().trim().toLowerCase(); // tentativo normalizzato

			// controllo se utente si arrende ed esce
			if ("exit".equals(tentativo)) {
				out.writeUTF(tentativo);
				System.out.println(); // spazio CLI
				return;
			}

			// controllo validita' stringa inserita
			if (!tentativo.matches("^[a-z]{10}$")) {
				System.out.println("\nParola non valida\n");
				continue;
			}

			// invio tentativo
			out.writeUTF(tentativo);

			// ricezione risposta
			parteRisposta = in.readUTF().split(";");

			System.out.println("\n" + parteRisposta[1]);

			// se parola indovinata o tentativi esauriti
			if (Integer.parseInt(parteRisposta[0]) == finePartita)
				break;
		}

		// fine partita, l'utente sceglie se condividere il risultato
		System.out.println("\nImmetti:\n1 - Condividi risultato\n2 - Torna al menu");

		// ciclo gestione scelta utente sulla condivisione del risultato
		while (true) {
			try {
				// utente immette scelta da tastiera
				int scelta = Integer.parseInt(scan.nextLine());

				if (scelta == 1) {
					// condivisione
					richiesta = codiceCondividi + ";";
					out.writeUTF(richiesta);
					parteRisposta = in.readUTF().split(";");
					System.out.println(parteRisposta[1]);

					System.out.println("Invio per continuare");
					scan.nextLine();
					break;
				}

				if (scelta == 2) {
					// no condivisione
					richiesta = codiceOK + ";";
					out.writeUTF(richiesta);
					break;
				}

				// utente ha inserito numero diverso da 1 o 2
				System.out.println("Scelta non valida");

			} catch (NumberFormatException e) {
				// utente non ha inserito un numero
				System.out.println("Scelta non valida");
			}
		}

	}

	/**
	 * Metodo che richiede le statistiche del profilo del giocatore al server.
	 * Ricevuta la risposta le stampa.
	 * 
	 * @param in  stream lettura su socket
	 * @param out stream scrittura su socket
	 * 
	 * @throws IOException errore lettura o scrittura sulla socket
	 */
	private static void statisticheProfilo(DataInputStream in, DataOutputStream out) throws IOException {

		// invio richiesta statistiche
		String richiesta = codiceStatistiche + ";";
		out.writeUTF(richiesta);

		// ricezione statistiche e stampa
		String[] parteRisposta = in.readUTF().split(";");
		System.out.println("\n" + parteRisposta[1]);
	}

	/**
	 * Metodo che mostra tutti i risultati condivisi dagli utenti durante la
	 * sessione, dal meno recente
	 * 
	 * @param condivisioni lista che contiene i risultati condivisi
	 */
	private static void mostraCondivisioni(List<String> condivisioni) {
		System.out.println(); // spazio CLI

		synchronized (condivisioni) {
			if (condivisioni.isEmpty()) {
				System.out.println("Nessuna condivisione\n");
				return;
			}

			for (String risultato : condivisioni)
				System.out.println(risultato);
		}
	}

}

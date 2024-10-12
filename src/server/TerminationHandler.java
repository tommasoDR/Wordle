package server;

import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.stream.JsonWriter;

public class TerminationHandler extends Thread {

	// attesa massima chiusura pool di thread e ScheduledExecutorService
	private final int attesaMassima;

	// pool di thread del server da terminare
	private final ExecutorService pool;

	// serverSocket da chiudure
	private final ServerSocket serverSocket;

	// parola attuale da salvare
	private final Parola parola;

	// dati sugli utenti da salvare
	private final Map<String, DatiUtente> utenti;

	// percorso file JSON da salvare
	private final String jsonFile;

	// ScheduledExecutorService cambio parola da terminare
	private final ScheduledExecutorService schedulerParola;

	public TerminationHandler(int attesaMassima, ExecutorService pool, ServerSocket serverSocket, Parola parola,
			Map<String, DatiUtente> utenti, String jsonFile, ScheduledExecutorService schedulerParola) {
		this.attesaMassima = attesaMassima;
		this.pool = pool;
		this.serverSocket = serverSocket;
		this.parola = parola;
		this.utenti = utenti;
		this.jsonFile = jsonFile;
		this.schedulerParola = schedulerParola;
	}

	public void run() {
		// inizio procedura di terminazione del server.
		System.out.println("Avvio terminazione Server\n");

		// chiusura ServerSocket per impedire accettazione di nuove richieste
		try {
			serverSocket.close();
		} catch (IOException e) {
			System.err.printf("Errore chiusura: %s\n", e.getMessage());
		}

		// terminazione pool di thread gestione richieste utenti
		pool.shutdown();
		try {
			if (!pool.awaitTermination(attesaMassima, TimeUnit.SECONDS))
				pool.shutdownNow();
		} catch (InterruptedException e) {
			pool.shutdownNow();
		}

		// terminazione ScheduledExecutorService cambio parola
		schedulerParola.shutdown();
		try {
			if (!schedulerParola.awaitTermination(attesaMassima, TimeUnit.SECONDS))
				schedulerParola.shutdownNow();
		} catch (InterruptedException e) {
			schedulerParola.shutdownNow();
		}

		// salvataggio stato, solo questo thread lavora sui dati condivisi
		try (JsonWriter jsonWriter = new JsonWriter(new FileWriter(jsonFile))) {
			// inizio oggetto json
			jsonWriter.beginObject();

			// dati parola
			jsonWriter.name("parola").value(parola.getParola());
			jsonWriter.name("id").value(parola.getId());
			jsonWriter.name("scadenza").value(parola.getScadenza());

			// inizio array utenti
			jsonWriter.name("utenti");
			jsonWriter.beginArray();

			for (Entry<String, DatiUtente> entry : utenti.entrySet()) {
				// recupero dati utente da entry hash map
				DatiUtente utente = entry.getValue();

				// inizio oggetto utente
				jsonWriter.beginObject();

				// dati profilo
				jsonWriter.name("username").value(entry.getKey());
				jsonWriter.name("password").value(utente.getHashPassword());
				jsonWriter.name("partiteGiocate").value(utente.getPartiteGiocate());
				jsonWriter.name("partiteVinte").value(utente.getPartiteVinte());
				jsonWriter.name("miglioreStreakVittorie").value(utente.getMiglioreStreakVittorie());
				jsonWriter.name("streakVittorieAttuale").value(utente.getStreakVittorieInCorso());
				jsonWriter.name("distribuzioneTentativi");

				// inizio array distribuzione
				jsonWriter.beginArray();

				int distribuzione[] = utente.getDistribuzioneTentativiImpiegati();
				for (int i = 0; i < 12; i++) {
					jsonWriter.value(distribuzione[i]);
				}

				// fine array distribuzione
				jsonWriter.endArray();

				// ultima partita giocata
				jsonWriter.name("scadenzaParolaGiocata").value(utente.getScadenzaParolaGiocata());

				// fine oggetto utente
				jsonWriter.endObject();
			}

			// fine array utenti
			jsonWriter.endArray();

			// fine json
			jsonWriter.endObject();

		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Server terminato");
	}

}

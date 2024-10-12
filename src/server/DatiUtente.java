package server;

public class DatiUtente {

	// dati generali profilo
	private final String username;
	private final String hashPassword;
	private int partiteGiocate;
	private int partiteVinte;
	private int miglioreStreakVittorie;
	private int streakVittorieInCorso;
	private int[] distribuzioneTentativiImpiegati;

	// dati ultima partita
	private long scadenzaParolaGiocata;

	// stato
	private boolean loggato;

	// costruttore nuovi utenti
	public DatiUtente(String username, String password) {
		this.username = username;
		this.hashPassword = password;
		this.partiteGiocate = 0;
		this.partiteVinte = 0;
		this.miglioreStreakVittorie = 0;
		this.streakVittorieInCorso = 0;
		this.distribuzioneTentativiImpiegati = new int[12];
		this.scadenzaParolaGiocata = 0;
		this.loggato = false;
	}

	// costruttore utenti da JSON
	public DatiUtente(String username, String password, int partiteGiocate, int partiteVinte,
			int miglioreStreakVittorie, int streakVittorieInCorso, int[] distribuzioneTentativiImpiegati,
			long scadenzaParolaGiocata) {
		this.username = username;
		this.hashPassword = password;
		this.partiteGiocate = partiteGiocate;
		this.partiteVinte = partiteVinte;
		this.miglioreStreakVittorie = miglioreStreakVittorie;
		this.streakVittorieInCorso = streakVittorieInCorso;
		this.distribuzioneTentativiImpiegati = distribuzioneTentativiImpiegati;
		this.scadenzaParolaGiocata = scadenzaParolaGiocata;
	}

	// getters

	public String getUsername() {
		return username;
	}

	public String getHashPassword() {
		return hashPassword;
	}

	public int getPartiteGiocate() {
		return partiteGiocate;
	}

	public int getPartiteVinte() {
		return partiteVinte;
	}

	public int getMiglioreStreakVittorie() {
		return miglioreStreakVittorie;
	}

	public int getStreakVittorieInCorso() {
		return streakVittorieInCorso;
	}

	public int[] getDistribuzioneTentativiImpiegati() {
		return distribuzioneTentativiImpiegati;
	}

	public long getScadenzaParolaGiocata() {
		return scadenzaParolaGiocata;
	}

	public synchronized boolean isLoggato() {
		return loggato;
	}

	// statistiche da stampare su richiesta utente

	public String getStatistiche() {
		String statistiche = "Utente " + username + ":\n- Partite giocate: " + partiteGiocate + "\n- Partite vinte: "
				+ partiteVinte + "\n- Streak di vittorie in corso: " + streakVittorieInCorso
				+ "\n- Migliore streak di vittorie: " + miglioreStreakVittorie;

		statistiche += "\n- Distribuzione vittorie: ";

		for (int i = 0; i < 12; i++) {
			statistiche += "\n\tVittorie al " + (i + 1) + " tentativo: " + distribuzioneTentativiImpiegati[i];
		}

		return statistiche;
	}

	// setters

	public void setPartiteGiocate(int partiteGiocate) {
		this.partiteGiocate = partiteGiocate;
	}

	public void setPartiteVinte(int partiteVinte) {
		this.partiteVinte = partiteVinte;
	}

	public void setMiglioreStreakVittorie(int miglioreStreakVittorie) {
		this.miglioreStreakVittorie = miglioreStreakVittorie;
	}

	public void setStreakVittorieInCorso(int streakVittorieInCorso) {
		this.streakVittorieInCorso = streakVittorieInCorso;
	}

	public void setDistribuzioneTentativiImpiegati(int[] distribuzioneTentativiImpiegati) {
		this.distribuzioneTentativiImpiegati = distribuzioneTentativiImpiegati;
	}

	public void setScadenzaParolaGiocata(long scadenzaParolaGiocata) {
		this.scadenzaParolaGiocata = scadenzaParolaGiocata;
	}

	public synchronized void setLoggato(boolean loggato) {
		this.loggato = loggato;
	}

}

package server;

public class Parola {

	private String parola;
	private long scadenza;
	private int id;

	public Parola(String parola, int id, long scadenza) {
		this.parola = parola;
		this.id = id;
		this.scadenza = scadenza;
	}

	// getters

	public String getParola() {
		return parola;
	}

	public int getId() {
		return id;
	}

	public long getScadenza() {
		return scadenza;
	}

	// setters

	public void setParola(String parola) {
		this.parola = parola;
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setScadenza(long scadenza) {
		this.scadenza = scadenza;
	}

}

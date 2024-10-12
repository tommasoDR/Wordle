package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

public class ThreadCondivisioni extends Thread {

	// Indirizzo (stringa) e porta gruppo multicast
	private final String indirizzoMulticast;
	private final int portaMulticast;

	// InetAddress e interfaccia di rete per join group
	private InetSocketAddress indirizzoGruppo;
	private NetworkInterface interfacciaRete;

	// Multicast socket
	private MulticastSocket ms;

	// lista contenente i risultati condivisi come stringa
	private final List<String> condivisioni;

	public ThreadCondivisioni(String stringaIndirizzo, int portaMulticast, List<String> condivisioni) {
		this.indirizzoMulticast = stringaIndirizzo;
		this.portaMulticast = portaMulticast;
		this.condivisioni = condivisioni;
	}

	@Override
	public void run() {
		InetAddress indirizzo = null;

		try {
			// da indirizzo sotto forma di stringa a InetAddress
			indirizzo = InetAddress.getByName(indirizzoMulticast);
		} catch (UnknownHostException e) {
			System.out.println("\nErrore connessione gruppo multicast");
			return;
		}

		try {
			// InetSocketAddress per entrata nel gruppo
			indirizzoGruppo = new InetSocketAddress(indirizzo, portaMulticast);

			// interfaccia di rete
			interfacciaRete = NetworkInterface.getByInetAddress(indirizzo);

			// entrata nel gruppo multicast
			ms = new MulticastSocket(portaMulticast);
			ms.joinGroup(indirizzoGruppo, interfacciaRete);

			// timeout per receive
			ms.setSoTimeout(4000);

			// pacchetto di ricezione dati
			DatagramPacket pacchetto = new DatagramPacket(new byte[512], 512);

			boolean continua = true;
			while (continua) {

				try {
					// ricezione risultato condiviso
					ms.receive(pacchetto);

					// add alla lista, sincronizzato
					condivisioni.add(new String(pacchetto.getData(), 0, pacchetto.getLength()));

				} catch (SocketTimeoutException e) {
					// se è stato ricevuto un interrupt bisogna terminare
					if (isInterrupted())
						continua = false;
				}
			}

		} catch (IOException e) {
			System.out.println("\nErrore condivisioni: " + e.getMessage());
			return;
		} finally {
			// se entrato nel gruppo multicast
			if (ms != null) {
				try {
					ms.leaveGroup(indirizzoGruppo, interfacciaRete);
				} catch (IOException e) {
					e.printStackTrace();
				}
				ms.close();
			}
		}

	}

}

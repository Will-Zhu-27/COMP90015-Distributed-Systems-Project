package unimelb.bitbox;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
//import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;

public class PeerControlServer extends Thread {
	//private static Logger log = Logger.getLogger(PeerControlServer.class.getName());
	private ServerSocket serverSocket;
	protected ServerMain serverMain;
	private int port;
	public PeerControlServer(ServerMain serverMain) {
		this.serverMain = serverMain;
		port = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
		try {
			serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		start();
	}

	public void run() {
		while (true) {
			Socket clientSocket;
			try {
				// wait for receive connection
				clientSocket = serverSocket.accept();
				new PeerControlConnection(this, clientSocket);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
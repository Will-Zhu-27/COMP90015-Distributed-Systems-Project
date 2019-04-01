package unimelb.bitbox;

import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;

public class ClientMain {
	private static Logger log = Logger.getLogger(Peer.class.getName());
	private String host;
	private int port;
	private ArrayList<String> peerList;
	private ArrayList<Socket> socketList;
	public ClientMain() {
		host = Configuration.getConfigurationValue("advertisedName");
		port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		peerList = new ArrayList<String>();
		socketList = new ArrayList<Socket>();
		for(String peer: Configuration.getConfigurationValue("peers").split(",")) {
			peerList.add(peer);
			String serverHost = (peer.split(":"))[0];
			int serverPort = Integer.parseInt((peer.split(":"))[1]);
			try {
				Socket clientSocket = new Socket(serverHost, serverPort);
				log.info("connect to " + peer + " successfully.");
				Connection connection = new Connection(clientSocket, serverHost, serverPort);
				connection.handshakeRequest();
				socketList.add(clientSocket);
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				log.warning("while connecting to " + peer + " refused.");
			}
		}
	}
	
}
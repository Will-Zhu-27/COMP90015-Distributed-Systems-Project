package unimelb.bitbox;

import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;

public class ClientMain extends Thread {
	private static Logger log = Logger.getLogger(Peer.class.getName());
	private ArrayList<String> peerList;
	private ArrayList<Socket> socketList;
	
	public ClientMain() {
		peerList = new ArrayList<String>();
		socketList = new ArrayList<Socket>();
		for(String peer: Configuration.getConfigurationValue("peers").split(",")) {
			peerList.add(peer);
			String host = (peer.split(":"))[0];
			int port = Integer.parseInt((peer.split(":"))[1]);
			try {
				Socket clientSocket = new Socket(host, port);
				log.info("connect to " + peer + " successfully.");
				new Connection(clientSocket);
				socketList.add(clientSocket);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				log.warning("while connecting to " + peer + " refused.");
			}
		}
	}
	
}
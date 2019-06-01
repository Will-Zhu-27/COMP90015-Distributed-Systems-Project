package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;

public class PeerControlConnection extends Connection {
	protected Logger log = Logger.getLogger(PeerControlConnection.class.getName());
	protected PeerControlServer controlServer;
	protected String secretKey;
	public PeerControlConnection(PeerControlServer controlServer, Socket socket) throws IOException {
		super(socket);
		this.controlServer = controlServer;
		start();
	}
	
	@Override
	public void sendMessage(Document doc) {
		log.info("sending " + doc.toJson() + " to client");
		try {
			writer.write(doc.toJson() + "\n");
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void checkCommand(Document doc) throws IOException {
		// TODO Auto-generated method stub
		log.info("receive "+ doc.toJson() + " from client");
		String command = doc.getString("command");
		if (command == null) {
			/* receive payload */
			if (doc.getString("payload") != null) {
				Command.payloadHandler(this, doc);
			}
		} else {
			/* receive AUTH_REQUEST from client */
			if (command.equals("AUTH_REQUEST")) {
				Command.authRequestHandler(this, doc);
			}
		}
	}
	
	/**
	 * get connected peer info
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	public ArrayList<Document> getConnectedPeerDocumentArrayList() {
		ArrayList<Document> peerDocList = new ArrayList<Document>();
		HashMap<String, PeerConnection> connectedPeerList = 
			controlServer.serverMain.getConnectedPeerList();
		for (String peer : connectedPeerList.keySet()) {
			Document peerDoc = new Document();
			String host = (peer.split(":"))[0];
			int port = Integer.parseInt((peer.split(":"))[1]);
			peerDoc.append("host", host);
			peerDoc.append("port", port);
			peerDocList.add(peerDoc);
		}
		return peerDocList;
	}
	
	/**
	 * get the public key of  the requestedIdentity in configuration.properties.
	 * @param requestedIdentity
	 * @return null: the requestedIdentity is not recorded.
	 * 				the corresponding public key string without the prefix "ssh-rsa"
	 */
	public String getPublicKey(String requestedIdentity) {
		try {
			String[] authorizedKeysList = Configuration.getConfigurationValue("authorized_keys").split(",");
			String authorizedKey = null;
			boolean flag = false;
			for (int i = 0; i < authorizedKeysList.length; i++) {
				authorizedKey = authorizedKeysList[i];
				if (requestedIdentity.equals(authorizedKey.substring(authorizedKey.lastIndexOf(" ") + 1))) {
					flag = true;
					break;
				}
			}
			if (flag == false) {
				return null;
			}
			
			authorizedKey = authorizedKey.trim();
			int startIndex = authorizedKey.indexOf(" ") + 1;
			int endIndex = authorizedKey.lastIndexOf(" ");
			
			String publicKeyString = authorizedKey.substring(startIndex, endIndex);
			log.info(requestedIdentity + " public key is :" + publicKeyString);
			return publicKeyString;
		} catch (Exception e) {
			return null;
		}
	}
	
}
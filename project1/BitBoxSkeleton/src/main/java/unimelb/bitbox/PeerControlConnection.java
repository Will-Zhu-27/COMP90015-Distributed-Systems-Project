package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;

import unimelb.bitbox.PeerConnection.CONNECTION_STATUS;
import unimelb.bitbox.util.AES;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.SshWithRSA;

public class PeerControlConnection extends Connection {
	private PeerControlServer controlServer;
	private String secretKey;
	public PeerControlConnection(PeerControlServer controlServer, Socket socket) throws IOException {
		super(socket);
		this.controlServer = controlServer;
		start();
	}
	
	@Override
	public void checkCommand(Document doc) throws IOException {
		// TODO Auto-generated method stub
		String command = doc.getString("command");
		if (command == null) {
			/* receive payload */
			if (doc.getString("payload") != null) {
				payloadHandler(doc);
				//log.info("received payload from " + connectedSocket.get + ":" + connectedPort);
			}
		} else {
			/* receive AUTH_REQUEST from client */
			if (command.equals("AUTH_REQUEST")) {
				String requestedIdentity = doc.getString("identity");
				String publicKeyString = getPublicKey(requestedIdentity);
				if (publicKeyString == null) {
					authResponseFalse();
				} else {
					try {
						authResponseTrue(publicKeyString);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	private void payloadHandler(Document doc) {
		String encodedContentJsonString = doc.getString("payload");
		if (encodedContentJsonString == null) {
			log.info("Error!!!"); // need more!!!!!
			return;
		}
		String decodedContentJsonString = new String(Base64.getDecoder().decode(encodedContentJsonString.getBytes()));
		String decryptedContentJsonString = AES.decryptHex(decodedContentJsonString, secretKey);
		log.info("Received content from client:" + decryptedContentJsonString);
		// check client command and respond correspondingly
		Document decryptedDoc = Document.parse(decryptedContentJsonString);
		switch(decryptedDoc.getString("command")) {
			case "LIST_PEERS_REQUEST":{
					listPeersResponse();
					break;
				}
			case "CONNECT_PEER_REQUEST":{
				connectPeerResponse(decryptedDoc);
				break;
			}
			case "DISCONNECT_PEER_REQUEST":{
				disconnectPeerRequest(decryptedDoc);
				break;
			}
		}
	}
	
	private void listPeersResponse() {
		ArrayList<Document> peerDocList = getConnectedPeerDocumentArrayList();
		Document doc = new Document();
		doc.append("command", "LIST_PEERS_RESPONSE");
		doc.append("peers", peerDocList);
		payload(doc.toJson());
	}
	
	private void connectPeerResponse(Document doc) {
		Document unencryptedDoc = new Document();
		unencryptedDoc.append("command", "CONNECT_PEER_RESPONSE");
		String givenHost = doc.getString("host");
		String temp = "" + doc.get("port");
		int givenPort = Integer.parseInt(temp);
		unencryptedDoc.append("host", givenHost);
		unencryptedDoc.append("port", givenPort);
		// already connect to given peer
		if(controlServer.serverMain.connectedPeerListContains(givenHost + ":" + givenPort) == true) {
			unencryptedDoc.append("status", true);
			unencryptedDoc.append("message", "already connected to peer");
		} 
		// try to connect
		else {
			PeerConnection givenPeerconnection = controlServer.serverMain.connectGivenPeer(givenHost, givenPort);
			while(givenPeerconnection.getConnectionStatus() == CONNECTION_STATUS.WAITING);
			if (givenPeerconnection.getConnectionStatus() == CONNECTION_STATUS.ONLINE) {
				unencryptedDoc.append("status", true);
				unencryptedDoc.append("message", "connected to peer");
			} else {
				unencryptedDoc.append("status", false);
				unencryptedDoc.append("message", "connection failed");
			}
		}
		payload(unencryptedDoc.toJson());
	}
	
	private void disconnectPeerRequest(Document doc) {
		Document unencryptedDoc = new Document();
		unencryptedDoc.append("command", "DISCONNECT_PEER_REQUEST");
		String givenHost = doc.getString("host");
		String temp = "" + doc.get("port");
		int givenPort = Integer.parseInt(temp);
		unencryptedDoc.append("host", givenHost);
		unencryptedDoc.append("port", givenPort);
		if (controlServer.serverMain.connectedPeerListContains(givenHost + ":" + givenPort) == false) {
			unencryptedDoc.append("status", false);
			unencryptedDoc.append("message", "connection not active");
		} else {
			if (controlServer.serverMain.disconnectPeer(givenHost, givenPort) == true) {
				unencryptedDoc.append("status", true);
				unencryptedDoc.append("message", "disconnected from peer");
			} else {
				unencryptedDoc.append("status", false);
				unencryptedDoc.append("message", "fail to disconnect");
			}
		}
		payload(unencryptedDoc.toJson());
	}
	
	/**
	 * get connected peer info
	 * @author yuqiangz@student.unimelb.edu.au
	 */
	private ArrayList<Document> getConnectedPeerDocumentArrayList() {
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
	 * send encrypted message to client
	 * @param message unencrypted String you want to send to client
	 */
	private void payload(String message) {
		log.info("The content before encrypted:" + message);
		Document doc = new Document();
		String encryptedContent =  AES.encryptHex(message, secretKey);
		String encodedContent = Base64.getEncoder().encodeToString(encryptedContent.getBytes());
		doc.append("payload", encodedContent);
		sendMessage(doc);
		log.info("sending " + doc.toJson());
	}
	
	private void authResponseFalse() {
		Document doc = new Document();
		doc.append("command", "AUTH_RESPONSE");
		doc.append("status", false);
		doc.append("message", "public key not found");
		sendMessage(doc);
		log.info("sending " + doc.toJson());
	}
	
	private void authResponseTrue(String publicKeyString) throws Exception {
		Document doc = new Document();
		doc.append("command", "AUTH_RESPONSE");
		// generate a secret key
		secretKey = AES.generateAESKey(128);
		// encrypt the secret key using public key
		RSAPublicKey publicKey = SshWithRSA.decodePublicKey(Base64.getDecoder().decode(publicKeyString));
		byte[] encryptedContent = SshWithRSA.encrypt(secretKey.getBytes(), publicKey);
		String encryptedContentString = Base64.getEncoder().encodeToString(encryptedContent);
		//log.info("encodedContentString is:" + encodedContentString);
		doc.append("AES128", encryptedContentString);
		doc.append("status", true);
		doc.append("message", "public key found");
		sendMessage(doc);
		log.info("sending " + doc.toJson());
	}
	
	/**
	 * get the public key of  the requestedIdentity in configuration.properties.
	 * @param requestedIdentity
	 * @return null: the requestedIdentity is not recorded.
	 */
	private String getPublicKey(String requestedIdentity) {
		try {
			String[] authorizedKeysList = Configuration.getConfigurationValue("authorized_keys").split(",");
			String authorizedKey = null;
			for (int i = 0; i < authorizedKeysList.length; i++) {
				authorizedKey = authorizedKeysList[i];
				if (requestedIdentity.equals(authorizedKey.substring(authorizedKey.lastIndexOf(" ")))) {
					break;
				}
			}
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
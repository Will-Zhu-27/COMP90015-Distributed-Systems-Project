package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import unimelb.bitbox.util.AES;
import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.SshWithRSA;

public class ClientConnection extends Connection {
	private Client client;
	private String secretKey;
	
	public ClientConnection(Client client, Socket socket) throws IOException {
		super(socket);
		this.client = client;
		start();
		authRequest();
	}
	
	@Override
	public void checkCommand(Document doc) throws IOException {
		// TODO Auto-generated method stub
		String command = doc.getString("command");
		if (command == null) {
			/* receive payload */
			if (doc.getString("payload") != null) {
				log.info("received payload from " + client.getServerHost() + ":" + client.getServerPort());
				payloadHandler(doc);
				// disconnect
				connectedSocket.close();
				System.exit(0);
			}
		} else {
			/* receive AUTH_REQUEST from client */
			if (command.equals("AUTH_RESPONSE")) {
				String encodedContentString = doc.getString("AES128");
				log.info("encodedContentString is:" + encodedContentString);
				byte[] encodedContent = Base64.getDecoder().decode(encodedContentString);
				// use private key to decrypt
				try {
					RSAPrivateKey privateKey = SshWithRSA.parseString2PrivateKey();
					// get secret key
					secretKey = new String(SshWithRSA.decrypt(encodedContent, privateKey), "utf-8");
					sendClientRequest();
					// log.info("Get the secret key:" + secretKey);
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidKeySpecException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			log.info("received " + command + " from " + client.getServerHost() + ":" + client.getServerPort());
		}

	}
	
	public void run() {
		String data;
		try {
			while ((data = reader.readLine()) != null) {
				// convert message from string to JSON
				Document doc = Document.parse(data);
				checkCommand(doc);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			try {
				connectedSocket.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
	
	private void authRequest() {
		Document doc = new Document();
		doc.append("command", "AUTH_REQUEST");
		doc.append("identity", client.getIdentity());
		sendMessage(doc);
		log.info("sending to " + client.getServerHost() + ":" + client.getServerPort() + 
				doc.toJson());
	}
	
	/**
	 * Send the client command from the inputed parameter when launch the Client 
	 */
	private void sendClientRequest() {
		// the unecrypted document
		Document commandDoc = new Document();
		String command = client.getClientCommand().toUpperCase() + "_REQUEST";
		commandDoc.append("command", command);
		if(client.getGivenPeerHost() != null) {
			commandDoc.append("host", client.getGivenPeerHost());
			commandDoc.append("port", client.getGivenPeerPort());
		}
		String encryptedClientCommand =  AES.encryptHex(commandDoc.toJson(), secretKey);
		String encodedContent = Base64.getEncoder().encodeToString(encryptedClientCommand.getBytes());
		
		// the encrypted payload document
		Document doc = new Document();
		doc.append("payload", encodedContent);
		sendMessage(doc);
		log.info("sending to " + client.getServerHost() + ":" + client.getServerPort() + 
				doc.toJson());
	}
	
	
	private void payloadHandler(Document doc) {
		String encodedContentJsonString = doc.getString("payload");
		if (encodedContentJsonString == null) {
			log.info("Error!!!"); // need more!!!!!
			return;
		}
		String decodedContentJsonString = new String(Base64.getDecoder().decode(encodedContentJsonString.getBytes()));
		String decryptedContentJsonString = AES.decryptHex(decodedContentJsonString, secretKey);
		log.info(decryptedContentJsonString);
	}
}
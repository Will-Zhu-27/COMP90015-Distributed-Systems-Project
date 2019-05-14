package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import unimelb.bitbox.util.AES;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.SshWithRSA;

public class ClientConnection extends Connection {
	private Client client;
	private byte[] secretKey;
	
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
		/* receive AUTH_REQUEST from client */
		if (command.equals("AUTH_RESPONSE")) {
			String encodedContentString = doc.getString("AES128");
			log.info("encodedContentString is:" + encodedContentString);
			byte[] encodedContent = Base64.getDecoder().decode(encodedContentString);
			// use private key to decrypt
			try {
				RSAPrivateKey privateKey = SshWithRSA.parseString2PrivateKey();
				secretKey = SshWithRSA.decrypt(encodedContent, privateKey);
				log.info("Get the secret key:" + new String(secretKey, "utf-8"));
				String encodedTestContentString = doc.getString("testMessage");
				byte[] decodedText = AES.AESDecode(Base64.getDecoder().decode(encodedTestContentString), secretKey);
				log.info("The decoded test content:" + new String(decodedText, "utf-8"));
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
		log.info("received " + command + " from " + client.getServerHost() + ":" 
				+ client.getServerPort());
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
}
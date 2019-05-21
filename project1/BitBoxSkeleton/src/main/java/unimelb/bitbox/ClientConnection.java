package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;
import java.util.Base64;
import java.util.logging.Logger;

import unimelb.bitbox.util.AES;
import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;

public class ClientConnection extends Connection {
	protected Logger log = Logger.getLogger(ClientConnection.class.getName());
	protected Client client;
	protected String secretKey;
	
	public ClientConnection(Client client, Socket socket) throws IOException {
		super(socket);
		this.client = client;
		start();
		Command.authRequest(this);
	}
	
	@Override
	public void sendMessage(Document doc) {
		log.info("sending " + doc.toJson() + " to " + client.getServerHost() + ":" + client.getServerPort());
		try {
			writer.write(doc.toJson() + "\n");
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void checkCommand(Document doc) {
		// TODO Auto-generated method stub
		String command = doc.getString("command");
		if (command == null) {
			/* receive payload */
			if (doc.getString("payload") != null) {
				log.info("received payload from " + client.getServerHost() + ":" + client.getServerPort());
				Command.payloadHandler(this, doc);
				// disconnect
				try {
					connectedSocket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.exit(0);
			}
		} else {
			log.info("received " + doc.toJson() + " from " + client.getServerHost() + ":" + client.getServerPort());
			/* receive AUTH_RESPONSE from peer */
			if (command.equals("AUTH_RESPONSE")) {
				try {
					Command.authResponseHandler(this, doc);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
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
	
	/**
	 * Send the client command from the inputed parameter when launch the Client 
	 */
	public void sendClientRequest() {
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
}
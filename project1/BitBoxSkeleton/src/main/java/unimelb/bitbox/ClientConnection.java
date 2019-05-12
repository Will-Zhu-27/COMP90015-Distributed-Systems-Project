package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;

import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;

public class ClientConnection extends Connection {
	private Client client;
	
	public ClientConnection(Client client, Socket socket) throws IOException {
		super(socket);
		this.client = client;
		start();
		authRequest();
	}
	
	@Override
	public void checkCommand(Document doc) throws IOException {
		// TODO Auto-generated method stub
		
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
package unimelb.bitbox.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.logging.Logger;

import unimelb.bitbox.Peer;
import unimelb.bitbox.util.Document;

/**
 * Deal with things about socket including sending and receiving message.
 */
public abstract class Connection extends Thread {
	protected static Logger log = Logger.getLogger(Peer.class.getName());
	protected Socket connectedSocket;
	protected BufferedReader reader;
	protected BufferedWriter writer;
	
	public Connection() {// need more comment
	}
	
	public Connection(Socket socket) throws IOException {
		connectedSocket = socket;
		reader = new BufferedReader(
			new InputStreamReader(socket.getInputStream(), "UTF-8"));
		writer = new BufferedWriter(
			new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
	}

	public void run() {
		String data;
		try {
			while ((data = reader.readLine()) != null) {
				// System.out.println(data);
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
	 * check command information and response.
	 * 
	 * @param doc received message.
	 * 
	 */
	public abstract void checkCommand(Document doc) throws IOException;
	
	/**
	 * broadcast message to the clients.
	 * 
	 * @param doc the message you want to broadcast.
	 */
	public void sendMessage(Document doc) {
		try {
			writer.write(doc.toJson() + "\n");
			writer.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.info("Fail to send message to connected peer.");
		}

	}

	public Socket getConnectedSocket() {
		return connectedSocket;
	}

}
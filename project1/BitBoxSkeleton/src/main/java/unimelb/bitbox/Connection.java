package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.json.simple.JSONObject;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

public class Connection extends Thread {
		private static Logger log = Logger.getLogger(Peer.class.getName());
		private DataInputStream in;
		private DataOutputStream out;
		private Socket socket;
		private BufferedReader inReader;
		private PrintWriter outWriter;
		private String host;
		private int port;
		private String serverHost;
		private int serverPort;
		
		public Connection(Socket socket, String serverHost, int serverPort) throws IOException {
			host = Configuration.getConfigurationValue("advertisedName");
			port = Integer.parseInt(Configuration.getConfigurationValue("port"));
			this.socket = socket;
			in = new DataInputStream(this.socket.getInputStream());
			out = new DataOutputStream(this.socket.getOutputStream());
			inReader = new BufferedReader(new InputStreamReader(in));
			outWriter = new PrintWriter(out, true);
			this.serverHost = serverHost;
			this.serverPort = serverPort;
			start();
		}
		
		public Connection(Socket socket) throws IOException {
			host = Configuration.getConfigurationValue("advertisedName");
			port = Integer.parseInt(Configuration.getConfigurationValue("port"));
			this.socket = socket;
			in = new DataInputStream(this.socket.getInputStream());
			out = new DataOutputStream(this.socket.getOutputStream());
			inReader = new BufferedReader(new InputStreamReader(in));
			outWriter = new PrintWriter(out, true);
			start();
		}
		
		public void run() {
			System.out.println("*******");
			String data;
			try {
				while((data = inReader.readLine()) != null) {
					System.out.println(data);
					Document doc = Document.parse(data);
					checkCommand(doc);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void sendMessage(Document doc) {
			outWriter.write(doc.toJson()+"\n");
			outWriter.flush();
		}
		public void checkCommand(Document doc) {
			String command = doc.getString("command");
			if(command.equals("HANDSHAKE_REQUEST") ) {
				Document hostPort = (Document)doc.get("hostPort");
				System.out.println(hostPort.toJson());
				serverHost = hostPort.getString("host");
				String temp = "" + hostPort.get("port");
				serverPort = Integer.parseInt(temp);
				log.info("received " + command + " from " + serverHost + ":" + serverPort);
			}
			
		}
		public void handshakeRequest() {
			Document doc = new Document();
			doc.append("command", "HANDSHAKE_REQUEST");
			Document doc2 = new Document();
			doc2.append("host", host);
			doc2.append("port", port);
			doc.append("hostPort", doc2);
			sendMessage(doc);
			log.info("sending to " + serverHost + ":" + serverPort + doc.toJson());
		}
}
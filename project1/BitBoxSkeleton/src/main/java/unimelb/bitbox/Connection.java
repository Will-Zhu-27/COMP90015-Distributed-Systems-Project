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

/**
 * Deal with things about socket including sending and receiving message.
 */
public class Connection extends Thread {
		private static Logger log = Logger.getLogger(Peer.class.getName());
		private ClientMain client = null;
		private ServerMain server = null;
		private DataInputStream in;
		private DataOutputStream out;
		private Socket connectedSocket;
		private BufferedReader inReader;
		private PrintWriter outWriter;
		private String host;
		private int port;
		private String connectedHost;
		private int connectedPort;
		
		/**
		 * when client gets a socket from server, use this constructor to create an object 
		 * of Class Connection to monitor.
		 */
		public Connection(ClientMain client, Socket socket, String serverHost, int serverPort) throws IOException {
			this.client = client;
			setCommonAttributesValue(socket);
			connectedHost = serverHost;
			connectedPort = serverPort;
			start();
		}
		
		/**
		 * when server receives a connection from client, use this constructor to create
		 *  an object of Class Connection to monitor.
		 */
		public Connection(ServerMain server, Socket socket) throws IOException {
			this.server = server;
			setCommonAttributesValue(socket);
			start();
		}
		
		/**
		 * Set common attributes value for constructor.
		 */
		private void setCommonAttributesValue(Socket socket) throws IOException {
			host = Configuration.getConfigurationValue("advertisedName");
			port = Integer.parseInt(Configuration.getConfigurationValue("port"));
			connectedSocket = socket;
			in = new DataInputStream(connectedSocket.getInputStream());
			out = new DataOutputStream(connectedSocket.getOutputStream());
			inReader = new BufferedReader(new InputStreamReader(in));
			outWriter = new PrintWriter(out, true);
		}
		
		public void run() {
			String data;
			try {
				while((data = inReader.readLine()) != null) {
					//System.out.println(data);
					// convert message from string to JSON
					Document doc = Document.parse(data);
					checkCommand(doc);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				try {
					connectedSocket.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
		
		/**
		 * broadcast message to the clients.
		 * 
		 * @param doc the message you want to broadcast.
		 */
		public void sendMessage(Document doc) {
			outWriter.write(doc.toJson() + "\n");
			outWriter.flush();
		}
		
		/**
		 * check command information and response.
		 * 
		 * @param doc received message.
		 * 
		 */
		public void checkCommand(Document doc) throws IOException {
			String command = doc.getString("command");
			
			/* receive HANDSHAKE_REQUEST */
			if(command.equals("HANDSHAKE_REQUEST") ) {
				Document hostPort = (Document)doc.get("hostPort");
				//System.out.println(hostPort.toJson());
				connectedHost = hostPort.getString("host");
				String temp = "" + hostPort.get("port");
				connectedPort = Integer.parseInt(temp);
				log.info("received " + command + " from " + connectedHost + ":" + connectedPort);
				if (!server.connectedPeerListContains(connectedHost + ":" + connectedPort)) {
					invalidProtocol();
				} else if(ServerMain.connectionNum >= ServerMain.maximunIncommingConnections) {
					connectionRefused();
				} else {
					handshakeResponse();
				}	
			}
			
			/* receive HANDSHAKE_RESPONSE */
			if(command.equals("HANDSHAKE_RESPONSE")) {
				Document hostPort = (Document)doc.get("hostPort");
				System.out.println(hostPort.toJson());
				connectedHost = hostPort.getString("host");
				String temp = "" + hostPort.get("port");
				connectedPort = Integer.parseInt(temp);
				log.info("received " + command + " from " + connectedHost + ":" + connectedPort);
			}
			
			/* receive CONNECTION_REFUSED */
			if(command.equals("CONNECTION_REFUSED")) {
				log.info("received " + command + " from " + connectedHost + ":" + connectedPort);
				connectedSocket.close();
			}
			
			/* receive INVALID_PROTOCOL */
			if(command.equals("INVALID_PROTOCOL")) {
				log.info("received " + command + " from " + connectedHost + ":" + connectedPort);
				connectedSocket.close();
			}
		}
		
		/**
		 * @author yuqiangz@student.unimelb.edu.au
		 */
		public void handshakeResponse() {
			Document doc = new Document();
			doc.append("command", "HANDSHAKE_RESPONSE");
			doc.append("hostPort", new HostPort(host, port).toDoc());
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + doc.toJson());
			// update the num of connection
			ServerMain.connectionNum++;
			//System.out.println("Now connection is " + ServerMain.connectionNum);
			//System.out.println("The max connection num is " + ServerMain.maximunIncommingConnections);
		}
		
		/**
		 * @author yuqiangz@student.unimelb.edu.au
		 */
		public void handshakeRequest() {
			Document doc = new Document();
			doc.append("command", "HANDSHAKE_REQUEST");
			doc.append("hostPort", new HostPort(host, port).toDoc());
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + doc.toJson());
		}
		
		/**
		 * @author yuqiangz@student.unimelb.edu.au
		 */
		public void connectionRefused() throws IOException {
			Document doc = new Document();
			doc.append("command", "CONNECTION_REFUSED");
			doc.append("message", "connection limit reached");
			ArrayList<Document> peerDocList = new ArrayList<Document>();
			ArrayList<String> connectedPeerList = server.getConnectedPeerList();
			for(String peer:connectedPeerList) {
				Document peerDoc = new Document();
				String host = (peer.split(":"))[0];
				int port = Integer.parseInt((peer.split(":"))[1]);
				peerDoc.append("host", host);
				peerDoc.append("port", port);
				peerDocList.add(peerDoc);
			}
			doc.append("peers", peerDocList);
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + doc.toJson());
			connectedSocket.close();
		}
		
		/**
		 * @author yuqiangz@student.unimelb.edu.au
		 */
		public void invalidProtocol() {
			Document doc = new Document();
			doc.append("command", "INVALID_PROTOCOL");
			doc.append("message", "message must a command field as string");
			sendMessage(doc);
			log.info("sending to " + connectedHost + ":" + connectedPort + doc.toJson());
			try {
				connectedSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
}
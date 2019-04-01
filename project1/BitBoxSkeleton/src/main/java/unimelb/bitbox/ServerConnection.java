package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;

public class ServerConnection extends Thread {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	private ServerSocket serverSocket;
	private String serverHost;
	private int serverPort;
	private ArrayList<String> peerList;
	private ArrayList<Socket> socketList;
	
	public ServerConnection() {
		peerList = new ArrayList<String>();
		socketList = new ArrayList<Socket>();
		for(String peer: Configuration.getConfigurationValue("peers").split(",")) {
			peerList.add(peer);
			String host = (peer.split(":"))[0];
			int port = Integer.parseInt((peer.split(":"))[1]);
			try {
				Socket clientSocket = new Socket(host, port);
				log.info("connect to " + peer + " successfully.");
				socketList.add(clientSocket);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				log.warning("while connecting to " + peer + " refused.");
			}
		}
		serverHost = Configuration.getConfigurationValue("advertisedName");
		serverPort = Integer.parseInt(Configuration.getConfigurationValue("port"));
		try {
			serverSocket = new ServerSocket(serverPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		start();
	}
	
	public void run() {
		while(true) {
			Socket clientSocket;
			try {
				clientSocket = serverSocket.accept();
				Connection c = new Connection(clientSocket);
				log.info("get connect request from " + clientSocket.getInetAddress().getHostName() 
						+ clientSocket.getPort());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public class Connection extends Thread {
		private DataInputStream in;
		private DataOutputStream out;
		private Socket clientSocket;
		private BufferedReader inReader;
		private PrintWriter outWriter;
			
		public Connection(Socket aClientSocket) throws IOException {
			clientSocket = aClientSocket;
			in = new DataInputStream(clientSocket.getInputStream());
			out = new DataOutputStream(clientSocket.getOutputStream());
			inReader = new BufferedReader(new InputStreamReader(in));
			outWriter = new PrintWriter(out, true);
			this.start();
		}
		
		public void run() {
			String data;
			try {
				while((data = inReader.readLine()) != null) {
					
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
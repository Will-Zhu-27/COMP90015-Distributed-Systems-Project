package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

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
package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;

import unimelb.bitbox.util.Connection;
import unimelb.bitbox.util.Document;

public class ClientConnection extends Connection {
	public ClientConnection(Socket socket) throws IOException {
		super(socket);
	}
	
	@Override
	public void checkCommand(Document doc) throws IOException {
		// TODO Auto-generated method stub
		
	}
	
}
package unimelb.bitbox;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import unimelb.bitbox.util.Document;

public class UDPHandlingErrors {
	private static Logger log = Logger.getLogger(UDPHandlingErrors.class.getName());
	protected Document storedDoc;
	private PeerConnection connection;
	private int udpRetryTimes = 0;
	protected Timer UDPTimer = null;
	
	
	public UDPHandlingErrors(Document doc, PeerConnection connection) {
		this.storedDoc = doc;
		this.connection = connection;
		UDPTimer = UDPPacketLossProtectionTimer();
	}
	
	public Timer UDPPacketLossProtectionTimer() {
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			public void run() {
				if (udpRetryTimes < ServerMain.udpRetries) {
					connection.log.info("*** UDP_HANDLING_ERRORS: not receive corresponding message in limit time, resend the message ***");
					connection.sendMessage(storedDoc);
					udpRetryTimes++;
				} else {
					log.info("*** UDP_HANDLING_ERRORS: have try enough times, disconnect " + connection.connectedHost + ":" + connection.connectedPort + " ***");
					timer.cancel();
					storedDoc = null;
					udpRetryTimes = 0;
					Command.invalidProtocol(connection);
					UDPTimer = null;
				}
			}
		}, ServerMain.udpTimeout, ServerMain.udpTimeout);
		return timer;
	}
}
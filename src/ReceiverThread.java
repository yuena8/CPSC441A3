import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import cpsc441.a3.shared.Segment;

public class ReceiverThread extends Thread {
	DatagramSocket uConnection;
	FastFtp master;
	
	public ReceiverThread(FastFtp owner,DatagramSocket udp) {
		uConnection=udp;
		master=owner;
	}
	
	
	public void run() {
			byte[] buffer=new byte[1000];
			DatagramPacket packet=new DatagramPacket(buffer, buffer.length);
			try {
				while (!Thread.interrupted()) {
					
					uConnection.receive(packet);
					Segment ack=new Segment(packet);
					System.out.println( "ACK received: " +ack.getSeqNum());
					
					master.processACK(ack);
					
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
}

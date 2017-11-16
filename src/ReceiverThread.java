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
					master.processACK(ack);
				}
				System.out.println("Receiver ended");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	public void interrupt() {
		System.out.println("Interrupted Receiver");
		super.interrupt();
		uConnection.close();
		
	}
	
}

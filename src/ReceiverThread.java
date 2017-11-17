import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import cpsc441.a3.shared.Segment;

public class ReceiverThread extends Thread {
	DatagramSocket uConnection;
	FastFtp master;
	volatile boolean stop;
	int lastSeg;
	public ReceiverThread(FastFtp owner,DatagramSocket udp) {
		uConnection=udp;
		master=owner;
		stop=false;
	}
	
	
	public void run() {
			try {
				while (!this.isInterrupted() && !stop) {
					byte[] buffer=new byte[1000];
					DatagramPacket packet=new DatagramPacket(buffer, buffer.length);
					uConnection.receive(packet);
					master.processACK(new Segment(packet));
				}
				System.out.println("Receiver ended");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
	}
	
	public void interrupt() {
		System.out.println("Interrupted Receiver");
		stop=true;
	}
	
}

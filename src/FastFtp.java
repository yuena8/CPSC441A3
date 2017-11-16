import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Timer;

import cpsc441.a3.shared.Segment;
import cpsc441.a3.shared.TxQueue;

/**
 * FastFtp Class
 *
 */

//import cpsc441.a3.shared.*;

public class FastFtp {
	int winSize;
	int timeout;
	TxQueue window;
	DatagramSocket udpSoc;
	Timer ti;
	int nextSeg;
	boolean timerSet=false;
	/**
     * Constructor to initialize the program 
     * 
     * @param windowSize	Size of the window for Go-Back_N in terms of segments
     * @param rtoTimer		The time-out interval for the retransmission timer
     */
	public FastFtp(int windowSize, int rtoTimer) {
		winSize=windowSize;
		timeout=rtoTimer;
		window=new TxQueue(winSize);
	}
	

    /**
     * Sends the specified file to the specified destination host:
     * 1. send file/connection info over TCP
     * 2. start receiving thread to process coming ACKs
     * 3. send file segment by segment
     * 4. wait until transmit queue is empty, i.e., all segments are ACKed
     * 5. clean up (cancel timer, interrupt receiving thread, close sockets/files)
     * 
     * @param serverName	Name of the remote server
     * @param serverPort	Port number of the remote server
     * @param fileName		Name of the file to be transferred to the remote server
     */
	public void send(String serverName, int serverPort, String fileName) {
		try {
			//Open random udpSocket
			udpSoc=new DatagramSocket(8888);
			
			//Establish TCP connection
			Socket soc=new Socket(serverName, serverPort);
			File sFile=new File(fileName);
	
			//TCP input and output streams
			DataInputStream in=new DataInputStream(soc.getInputStream());
			DataOutputStream out=new DataOutputStream(soc.getOutputStream());
			
			long fileSize=sFile.length();
			out.writeUTF(fileName);
			out.writeLong(fileSize);	//Length of file sent
			
			//Arbitrary?
			out.writeInt(udpSoc.getLocalPort());				//Local UDP port number
			out.flush();
			
			int serverUdpPort=in.readInt();
			System.out.println(serverUdpPort);

			udpSoc.connect(soc.getInetAddress(), serverUdpPort);
			
			
			//System.out.println("udp local prot = " + udpSoc.getLocalPort());
			
			//long cTime=System.currentTimeMillis();
			//while (cTime+5000 > System.currentTimeMillis()) {}
			
			ReceiverThread listener=new ReceiverThread(this, udpSoc);
			listener.start();
			FileInputStream fInStream=new FileInputStream(sFile);
			ti=new Timer();
			
			nextSeg=0;
			long bytesLeft=fileSize;
			
			while (bytesLeft != 0) {
				byte[] bArray;
				if (bytesLeft < 1000) {
					bArray=new byte[(int) bytesLeft];
				} else {
					bArray=new byte[1000];
				}
				
				bytesLeft-=fInStream.read(bArray);
				
				Segment sPacket=new Segment(nextSeg, bArray);
				nextSeg++;
				while (window.isFull()){
					Thread.yield();
				}
				processSend(sPacket);
			}
			
			fInStream.close();
			while (!window.isEmpty()) {
				//System.out.println("Window is not emptied");
				Thread.yield();
			}
			if (timerSet) {
				
				ti.cancel();
				ti.purge();
				timerSet=false;
			}
			udpSoc.close();
			listener.interrupt();
			soc.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public synchronized void processSend(Segment seg) {
		try {
			System.out.println("Sending Seg:\t" + seg.getSeqNum() + "\t(processSend)");
			
			udpSoc.send(new DatagramPacket(seg.getBytes(), seg.getLength()+4));
			window.add(seg);
			if (seg.getSeqNum()==window.element().getSeqNum()){
				//Cancel Previous Timer and Start new timer
				startNewTimer();
			}
		} catch (InterruptedException | IOException e) {
			System.out.println("Error occured in sending");
		}
	}
	
	public synchronized void processACK(Segment ack) {
		int ackNum=ack.getSeqNum();
		System.out.println("Received ACK:\t" + ackNum + "\t(processACK)");
		if (ackNum>window.element().getSeqNum()+winSize || ackNum<window.element().getSeqNum()) {
			//ignored cases
		} else {
			if (timerSet) {
				ti.cancel();
				ti.purge();
				timerSet=false;
			}
			boolean received=true;
			while (received) {
				try {
					Segment rSeg=window.element();
					if (rSeg==null) {
						//Do nothing
					} else if (rSeg.getSeqNum() < ackNum) {
						window.remove();
					} else if (rSeg.getSeqNum() == ackNum) {
						received=false;
					}
				} catch (InterruptedException e) {
					System.out.println("Thread interrupted");
				}
			}
			
			if (!window.isEmpty()) {
				startNewTimer();
			}
		}
	}
	
	public synchronized void processTimeout() {
		System.out.println("TIME OUT - Resending all window segments.");
		for (Segment cSeg:window.toArray()) {
			try {
				udpSoc.send(new DatagramPacket(cSeg.getBytes(), cSeg.getLength()+4));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if (!window.isEmpty()) {
			startNewTimer();
		}
	}
	
	private void startNewTimer() {
		if (timerSet) {
			ti.cancel();
			ti.purge();
		}
		ti=new Timer();
		ti.schedule(new TimeOuter(this), timeout);
		timerSet=true;		
	}
	
	
    /**
     * A simple test driver
     * 
     */
	public static void main(String[] args) {
		// all arguments should be provided
		// as described in the assignment description 
		if (args.length != 5) {
			System.out.println("incorrect usage, try again.");
			System.out.println("usage: FastFtp server port file window timeout");
			System.exit(1);
		}
		
		// parse the command line arguments
		// assume no errors
		String serverName = args[0];
		int serverPort = Integer.parseInt(args[1]);
		String fileName = args[2];
		int windowSize = Integer.parseInt(args[3]);
		int timeout = Integer.parseInt(args[4]);

		// send the file to server
		FastFtp ftp = new FastFtp(windowSize, timeout);
		System.out.printf("sending file \'%s\' to server...\n", fileName);
		ftp.send(serverName, serverPort, fileName);
		System.out.println("file transfer completed.");
	}
}

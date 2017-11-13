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
			out.writeInt(9564);				//Local UDP port number
			out.flush();
			
			int udpPort=in.readInt();
			
			udpSoc=new DatagramSocket(udpPort);
			
			ReceiverThread listener=new ReceiverThread(this, udpSoc);
			
			FileInputStream fInStream=new FileInputStream(sFile);
			ti=new Timer();

			int nextSeg=0;
			long bytesLeft=fileSize;
			
			while (bytesLeft != 0) {
				int length;
				byte[] bArray;
				if (bytesLeft < 1000) {
					bArray=new byte[(int) bytesLeft];
					length=(int) bytesLeft;
				} else {
					bArray=new byte[1000];
					length=1000;
				}
				
				bytesLeft-=fInStream.read(bArray);
				
				Segment sPacket=new Segment(nextSeg, bArray);
				while (window.isFull()){
					//Do a yield here later on
				}
				processSend(sPacket);
				
			}
			
			
		} catch (IOException e) {
			
			e.printStackTrace();
		} 
		
	}
	
	public synchronized void processSend(Segment seg) {
		try {
			udpSoc.send(new DatagramPacket(seg.getBytes(), seg.getLength()));
			window.add(seg);
			if (seg.getSeqNum()==window.element().getSeqNum()){
				//Cancel Previous Timer and Start new timer
				ti.cancel();
				ti.schedule(new TimeOuter(), timeout);				
			}
		} catch (InterruptedException | IOException e) {
			System.out.println("Error occured in sending");
		}
	}
	
	public synchronized void processACK(Segment ack) {
		int ackNum=ack.getSeqNum();
		if (ackNum>window.element().getSeqNum()+winSize || ackNum<window.element().getSeqNum()) {
			//ignored cases
		} else {
			ti.cancel();
			boolean received=true;
			while (received) {
				try {
					if (ackNum==window.remove().getSeqNum()) {
						received=false;
					}
					
				} catch (InterruptedException e) {
					System.out.println("Thread interrupted");
				}
			}
		}
	}
	
	public synchronized void processTimeout() {
		
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

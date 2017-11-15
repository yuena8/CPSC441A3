import java.util.TimerTask;

public class TimeOuter extends TimerTask {
	FastFtp master;
	
	public TimeOuter(FastFtp owner) {
		master=owner;
	}
	
	public void run() {
		
		master.processTimeout();
	
	}

}

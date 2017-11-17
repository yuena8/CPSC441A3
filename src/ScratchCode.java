import java.io.File;
import java.util.Timer;

public class ScratchCode {
	public static void main(String[] args) {
		File file=new File("test.pdf");
		
		System.out.println(file.getAbsolutePath());
		Out o=new Out();
		Timer ti=new Timer();
		ti.schedule(o, 1000);
		o.cancel();
		ti.purge();
		o=new Out();
		System.out.println(Thread.activeCount());
		
		ti.schedule(o, 1000);
		long time=System.currentTimeMillis();
		
		while(time+10000 > System.currentTimeMillis()) {}
		ti.cancel();
		o.cancel();
		System.out.println(Thread.activeCount());
	}
}

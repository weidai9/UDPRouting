import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Iterator;


public class UpdateSender extends Thread{
	public BFclient host;
	public boolean running = true;
	public UpdateSender(BFclient client){
		host = client;
	}

	@Override
	public void run(){
		while(running){
			try {
				Thread.sleep(host.TIME_OUT);
				for (String key : host.live_neighbor_cost.keySet()){
					if (isNotDeadNeighbor(key)){
						host.sendDistanceVector(key);
					} else if(!host.down_neighbor.contains(key)){
						System.out.println("Neighbor timeout, shut down: "+key);//test
						host.shutDownNeighbor(key);
					}
				}

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public boolean isNotDeadNeighbor(String neib){
		if(!host.last_update_time.containsKey(neib)){
			return true;
		}
		if(System.currentTimeMillis() - host.last_update_time.get(neib) > host.TIME_OUT*3){
			return false;
		} else
			return true;
	}

	public void stopRunning(){
		running = false;
	}

}

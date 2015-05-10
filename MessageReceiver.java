import java.io.IOException;
import java.net.DatagramPacket;


public class MessageReceiver extends Thread {
	public BFclient host;
	public boolean listening = true;
	public MessageReceiver(BFclient client){
		host = client;
	}
	@Override
	public void run(){
		while(listening){
			byte[] receiveData = new byte[100000];
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {
				host.hostSocket.receive(receivePacket);
				String nei = ""+receivePacket.getAddress()+":"+receivePacket.getPort();
				nei = nei.substring(1);
				MessageReceiverWorker mrw = new MessageReceiverWorker(host, receivePacket.getData(), nei);
				mrw.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void stopRunning(){
		listening = false;
	}
}

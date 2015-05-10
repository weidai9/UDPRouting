import java.util.concurrent.ConcurrentHashMap;


public class MessageReceiverWorker extends Thread {
	public BFclient host;
	public byte [] msg_byte;
	public String msg_type;
	public String msg_data;
	public String nei;
	public MessageReceiverWorker(BFclient host, byte [] msgbyte, String nei){
		this.host = host;
		this.nei = nei;
		String [] parse_res = Message.parseByteData(msgbyte);
		msg_byte = msgbyte;
		msg_type = parse_res[0];
		msg_data = parse_res[1];
		msg_data = msg_data.trim();
	}
	@Override
	public void run(){
		if (msg_type.equals("00")){
			handleUpdateVector();
			host.updateDistanceVector();
		} else if (msg_type.equals("01")){
			host.linkDownNeighbor(msg_data);
		} else if (msg_type.equals("02")){
			String [] dest_cost = msg_data.split("\\|");
			host.original_neighbor_cost.put(dest_cost[0], Double.parseDouble(dest_cost[1]));
			host.changeLinkCost(dest_cost[0], Double.parseDouble(dest_cost[1]));
		} else if (msg_type.equals("03")){
			double newcost = host.original_neighbor_cost.get(msg_data);
			host.last_update_time.put(msg_data, System.currentTimeMillis());
			host.live_neighbor_cost.put(msg_data, newcost);
			host.down_neighbor.remove(msg_data);
			host.changeLinkCost(msg_data, newcost);
		} else if (msg_type.equals("04")) {
			System.out.println("Packet received.");
			host.transferPacket(msg_byte);
		}
	}
	
	public void handleUpdateVector(){
		host.last_update_time.put(nei, System.currentTimeMillis());
		//host.neighbor_cost.put(nei, host.original_neighbor_cost.get(nei));
		host.live_neighbor_cost.put(nei, host.original_neighbor_cost.get(nei));
		host.reachable_dest.add(nei);
		String [] vectors = msg_data.split("\\|");
		ConcurrentHashMap<String, Double> newmap = new ConcurrentHashMap<String, Double>();
		ConcurrentHashMap<String, String> newmap2 = new ConcurrentHashMap<String, String>();
		for (String vector : vectors){
			if(vector.trim().length()>0){
				String [] addr_weight = vector.split(",");
				double weight = Double.parseDouble(addr_weight[1]);
				newmap.put(addr_weight[0], weight);
				newmap2.put(addr_weight[0], addr_weight[2]);
				host.reachable_dest.add(addr_weight[0]);
			}
		}
		host.neighbor_DV.put(nei, newmap);
		host.neighbor_next.put(nei, newmap2);
	}
}

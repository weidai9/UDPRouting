import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class BFclient {
	public ConcurrentHashMap<String, Double> host_DV;
	public ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> neighbor_DV;
	public ConcurrentHashMap<String, ConcurrentHashMap<String, String>> neighbor_next;
	public ConcurrentHashMap<String, Double> neighbor_cost;
	public ConcurrentHashMap<String, Double> live_neighbor_cost;
	public ConcurrentHashMap<String, Double> original_neighbor_cost;
	public ConcurrentHashMap<String, Long> last_update_time;
	public ConcurrentHashMap<String, String> dest_next;
	public Set<String> down_neighbor;
	public Set<String> reachable_dest;
	public DatagramSocket hostSocket;
	public int port_num;
	public long TIME_OUT;
	public String host_addr;
	public UpdateSender update_sender;
	public MessageReceiver message_receiver;
	
	public BFclient(String conf_filename){
		initClient(conf_filename);
		try {
			hostSocket = new DatagramSocket(port_num);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	public void initClient(String conf_filename){
		host_DV = new ConcurrentHashMap<String, Double>();
		neighbor_DV = new ConcurrentHashMap<String, ConcurrentHashMap<String, Double>>();
		neighbor_next = new ConcurrentHashMap<String, ConcurrentHashMap<String, String>>();
		neighbor_cost = new ConcurrentHashMap<String, Double>();
		original_neighbor_cost = new ConcurrentHashMap<String, Double>();
		live_neighbor_cost = new ConcurrentHashMap<String, Double>();
		last_update_time = new ConcurrentHashMap<String, Long>();
		dest_next = new ConcurrentHashMap<String, String>();
		reachable_dest = new HashSet<String>();
		down_neighbor = new HashSet<String>();
		try {
			/* load client configurations */
			FileInputStream conf_file = new FileInputStream(new File(conf_filename));
			BufferedReader br = new BufferedReader(new InputStreamReader(conf_file));
			String line = null;
			line = br.readLine();
			line = line.trim();
			String [] init_pairs = line.split(" ");
			if (init_pairs.length>2){
				System.out.println("Error: config file wrong format");
				System.exit(1);
			}
			port_num = Integer.parseInt(init_pairs[0]);
			TIME_OUT = Math.round(Double.parseDouble(init_pairs[1])*1000);
			while ((line = br.readLine()) != null) {
				line = line.trim();
				String [] words = line.split(" ");
				if (words.length>2){
					System.out.println("Error: config file wrong format");
					System.exit(1);
				}
				original_neighbor_cost.put(words[0], Double.parseDouble(words[1]));
				live_neighbor_cost.put(words[0], Double.parseDouble(words[1]));
				neighbor_cost.put(words[0], Double.parseDouble(words[1]));
			}
			host_addr = InetAddress.getLocalHost().getHostAddress()+":"+port_num;
			host_DV.put(host_addr, 0.0);
			reachable_dest.add(host_addr);
			br.close();
			updateDistanceVector();
		} catch(FileNotFoundException e){
			System.out.println("Error: File name not found! Exit...");
		} catch (IOException e) {
			System.out.println("Error: Fail to close file...");
		}
	}
	
	public static void main(String [] args) throws IOException {
		if (args.length != 1){
			System.out.println("Invalid number of arguements!");
			return;
		}
		BFclient client = new BFclient(args[0]);
		System.out.println("Address of this node: "+InetAddress.getLocalHost().getHostAddress()+":"+client.port_num);
		client.message_receiver = new MessageReceiver(client);
		client.message_receiver.start();
		client.update_sender = new UpdateSender(client);
		client.update_sender.start();
		client.runCommands();
	}
	
	public void runCommands() throws IOException{
		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
		while(true){
			System.out.print(" > ");
			String command = stdin.readLine();
			if (!checkCommand(command)){
				System.out.println("Invalid command syntax");
				continue;
			}
			String [] inputs = command.split(" ");
			if(inputs[0].equals("SHOWRT")){
				/* Print the routing table */
				DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
				Calendar cal = Calendar.getInstance();
				System.out.println("Distance Vector at "+dateFormat.format(cal.getTime()));
				System.out.println("Destination\t\tCost\t\tNextHop");
				System.out.println("-------------------------------------------------");
				for(String dest : host_DV.keySet()){
					if(dest.equals(host_addr))
						continue;
					String show = dest + "\t" + host_DV.get(dest) + "\t"+ dest_next.get(dest);
					System.out.println(show);
				}
				
			} else if (inputs[0].equals("CLOSE")){
				update_sender.stopRunning();
				message_receiver.stopRunning();
				break;
			} else if (inputs[0].equals("LINKDOWN")){
				String dest = inputs[1]+":"+inputs[2];
				linkDownNeighbor(dest);
				String data = host_addr;
				byte [] type = {0, 1};
				sendMessage(dest, type, data);
			} else if (inputs[0].equals("LINKUP")){
				String dest = inputs[1]+":"+inputs[2];
				if (!original_neighbor_cost.containsKey(dest)){
					System.out.println("Error: Invalid address...");
					continue;
				} else if (!down_neighbor.contains(dest)){
					System.out.println("Error: Cannot link up offline host...");
					continue;
				} else if (neighbor_cost.contains(dest)){
					System.out.println("Error: The link is not down...");
					continue;
				}
				down_neighbor.remove(dest);
				double newcost = original_neighbor_cost.get(dest);
				last_update_time.put(dest, System.currentTimeMillis());
				live_neighbor_cost.put(dest, newcost);
				changeLinkCost(dest, newcost);
				String data = host_addr;
				byte [] type = {0, 3};
				sendMessage(dest, type, data);
				
			} else if (inputs[0].equals("CHANGECOST")){
				String dest = inputs[1]+":"+inputs[2];
				if (!live_neighbor_cost.containsKey(dest)){
					System.out.println("Ignored: host current down...");
					continue;
				}
				double newcost = Double.parseDouble(inputs[3]);
				if (newcost == Double.POSITIVE_INFINITY|| newcost<0){
					System.out.println("Error: cannot change to negative or infinity...");
					continue;
				}
				original_neighbor_cost.put(dest, newcost);
				changeLinkCost(dest, newcost);
				String data = host_addr+"|"+newcost;
				byte [] type = {0, 2};
				sendMessage(dest, type, data);
			} else if(inputs[0].equals("TRANSFER")){
				String filename = inputs[1];
				String dest = inputs[2]+":"+inputs[3];
				if(!dest_next.contains(dest)){
					System.out.println("Error: unknown destination.");
					continue;
				}
				System.out.println(filename);
				byte [] filedata = Files.readAllBytes(Paths.get(filename));
				String [] filenamesplit = filename.split("\\.");
				String newfilename = filenamesplit[0]+"_transfered."+filenamesplit[1];
				
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				
				byte [] type = {0, 4};
				outputStream.write(type);
				byte [] lengthofdest = ByteBuffer.allocate(4).putInt(dest.getBytes().length).array();
				outputStream.write(lengthofdest);
				outputStream.write(dest.getBytes());
				byte [] lengthofnewfilename = ByteBuffer.allocate(4).putInt(newfilename.getBytes().length).array();
				outputStream.write(lengthofnewfilename);
				outputStream.write(newfilename.getBytes());
				byte [] lengthoffiledata = ByteBuffer.allocate(4).putInt(filedata.length).array();
				outputStream.write(lengthoffiledata);
				outputStream.write(filedata);
				
				byte [] transdata = outputStream.toByteArray();
				transferPacket(transdata);
				
			}
		}
	}
	
	public boolean checkCommand(String command){
		command = command.trim();
		String [] inputs = command.split(" ");
		int len = inputs.length;
		if (len==1 && (inputs[0].equals("SHOWRT")||inputs[0].equals("CLOSE")||inputs[0].equals("SHOWALL"))){
			return true;
		} else if (len==3 && (inputs[0].equals("LINKDOWN")||inputs[0].equals("LINKUP"))){
			return true;
		} else if (len==4 && (inputs[0].equals("CHANGECOST") ||inputs[0].equals("TRANSFER"))) {
			return true;
		}
		
		return false;
	}
	
	public void changeLinkCost(String dest, double newcost){
		neighbor_cost.put(dest, newcost);
		
		/* Poison reverse*/
		for (String neinei: neighbor_DV.keySet()){
			for (String neinei2: neighbor_DV.get(neinei).keySet()){
				if(neighbor_next.get(neinei).get(neinei2).equals(host_addr)){
					neighbor_DV.get(neinei).put(neinei2, Double.POSITIVE_INFINITY);
				}
			}
		}
		updateDistanceVector();
	}
	
	public void linkDownNeighbor(String dest){
		live_neighbor_cost.remove(dest);
		down_neighbor.add(dest);
		//neighbor_cost.remove(dest);
		neighbor_cost.put(dest, Double.POSITIVE_INFINITY);
		/* Poison reverse*/
		for (String neinei: neighbor_DV.keySet()){
			for (String neinei2: neighbor_DV.get(neinei).keySet()){
				if(neighbor_next.get(neinei).get(neinei2).equals(host_addr)){
					neighbor_DV.get(neinei).put(neinei2, Double.POSITIVE_INFINITY);
				}
			}
		}
		updateDistanceVector();
	}
	
	public void updateDistanceVector(){
		/* The part of Bellman-Ford Algorithm*/
		for (String dest : reachable_dest){
			if (dest.equals(host_addr)){
				continue;
			} else {
				// If the neighbor's DVs are not initialized
				if(neighbor_DV.keySet().isEmpty()){
					host_DV.put(dest, neighbor_cost.get(dest));
					dest_next.put(dest, dest);
				} else{
					String next_hop = dest_next.get(dest);
					double min_cost = Double.POSITIVE_INFINITY;
					for (String neib : neighbor_DV.keySet()){
						if(neighbor_DV.get(neib).containsKey(dest)){
							double current_cost = neighbor_DV.get(neib).get(dest) + neighbor_cost.get(neib);
							if(current_cost < min_cost){
								min_cost = current_cost;
								next_hop = neib;
							}
						}
					}
					host_DV.put(dest, min_cost);
					dest_next.put(dest, next_hop);
				}
				
			}
		}
	}
	
	public void shutDownNeighbor(String nei){
		/* poison reverse */
		live_neighbor_cost.remove(nei);
		neighbor_cost.put(nei, Double.POSITIVE_INFINITY);

		for (String neinei: neighbor_DV.keySet()){
			for (String neinei2: neighbor_DV.get(neinei).keySet()){
				if(neighbor_next.get(neinei).get(neinei2).equals(host_addr)){
					neighbor_DV.get(neinei).put(neinei2, Double.POSITIVE_INFINITY);
				}
			}
		}
		updateDistanceVector();
	}
	
	public void sendMessage(String dest, byte [] type, String data){
		String [] ip_port = dest.split(":");
		InetAddress inaddr;
		try {
			inaddr = InetAddress.getByName(ip_port[0]);
			byte [] sendData = Message.packetData(type, data.getBytes());
			DatagramPacket sendPacket = new DatagramPacket(
					sendData, 
					sendData.length, 
					inaddr,
					Integer.parseInt(ip_port[1]));
			hostSocket.send(sendPacket);
		} catch (UnknownHostException e) {
			System.out.println("Unknown host error...");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void sendDistanceVector(String dest){
		byte [] type = {0, 0};
		String sendMessage = "";
		for (String hostdvkey : host_DV.keySet()){
			sendMessage += hostdvkey + "," +host_DV.get(hostdvkey)+","+dest_next.get(hostdvkey)+"|";
		}
		sendMessage(dest, type, sendMessage);
	}
	
	public void transferPacket(byte [] data){
		/* the data[] includes the type*/
		String dest = Message.getFilePacketDestination(data);
		if(dest.equals(host_addr)){
			try {
				String filename = Message.getNewFileName(data);
				FileOutputStream fos = new FileOutputStream(filename);
				fos.write(Message.getFileData(data));
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Destination = "+ dest);
			System.out.println("File Received successfully");
		} else {
			String nexthop = dest_next.get(dest);
			String [] ip_port = nexthop.split(":");
			InetAddress inaddr;
			try {
				inaddr = InetAddress.getByName(ip_port[0]);
				DatagramPacket sendPacket = new DatagramPacket(
						data, 
						data.length, 
						inaddr,
						Integer.parseInt(ip_port[1]));
				hostSocket.send(sendPacket);
			} catch (UnknownHostException e) {
				System.out.println("Unknown host error...");
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Destination = "+ dest);
			System.out.println("Next hop = "+ nexthop);
		}
	}

}

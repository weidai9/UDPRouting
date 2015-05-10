import java.nio.ByteBuffer;
import java.util.Arrays;


public class Message {
	/*
	 * Data transfer protocol:
	 * The first two bytes are the type of data,
	 * and the rest is the data.
	 * type:
	 * 00 : update distance vector
	 * 01 : linkdown a path
	 * 02 : change cost
	 * 03 : linkup a path
	 * 04 : [4:length of address] [address] [4:length of filename] [filename] [4:data length] [data]  
	 * 
	 */
	
	public static byte [] packetData(byte [] type, byte [] data){
		byte [] result = Arrays.copyOf(type, type.length + data.length);
		System.arraycopy(data, 0, result, type.length, data.length);
		return result;
	}
	
	public static String [] parseByteData(byte [] data){
		String [] res = new String[2];
		byte [] databytes = Arrays.copyOfRange(data, 2, data.length);
		byte [] typebytes = {(byte) (data[0]+48), (byte) (data[1]+48)};
		res[0] = new String(typebytes);
		res[1] = new String(databytes);
		return res;
	}
	public static int getFilePacketDestLength(byte [] data){
		byte [] destlengthbytes = Arrays.copyOfRange(data, 2, 6);
		int x = ByteBuffer.wrap(destlengthbytes).getInt();
		return x;
	}
	
	public static String getFilePacketDestination(byte [] data){
		int destlength = getFilePacketDestLength(data);
		byte [] destbytes = Arrays.copyOfRange(data, 6, 6+destlength);
		return new String(destbytes);
	}
	
	public static int getFilePacketNewNameLength(byte [] data){
		int destlength = getFilePacketDestLength(data);
		byte [] namelengthbytes = Arrays.copyOfRange(data, 6+destlength, 10+destlength);
		return ByteBuffer.wrap(namelengthbytes).getInt();
	}
	
	public static String getNewFileName(byte [] data){
		int destlength = getFilePacketDestLength(data);
		int filenamelength = getFilePacketNewNameLength(data);
		byte [] filenamebyte = Arrays.copyOfRange(data, 10+destlength, 10+destlength+filenamelength);
		return new String(filenamebyte);
	}
	
	public static int getDatalength(byte [] data) {
		int destlength = getFilePacketDestLength(data);
		int filenamelength = getFilePacketNewNameLength(data);
		byte [] filelengthbytes = Arrays.copyOfRange(data, 10+destlength+filenamelength, 14+destlength+filenamelength);
		return ByteBuffer.wrap(filelengthbytes).getInt();
	}
	
	public static byte [] getFileData(byte [] data){
		int destlength = getFilePacketDestLength(data);
		int filenamelength = getFilePacketNewNameLength(data);
		int filelength = getDatalength(data);
		byte [] filedata = Arrays.copyOfRange(data, 14+destlength+filenamelength, 14+destlength+filelength);
		return filedata;
	}
	

}

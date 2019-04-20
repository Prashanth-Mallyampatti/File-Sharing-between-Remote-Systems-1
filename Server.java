import java.io.*;
import java.net.*;
import java.util.*;

public class Server
{
	public static int port, seqNum, checksum;
	public static float probability;
	public static String file, packetType;
	public static void main(String[] args)
	{
		if(args.length > 2)
		{
			port = Integer.parseInt(args[0]);
			file = args[1];
			probability = Float.parseFloat(args[2]);
		}
		DatagramSocket serverSocket = null;

		try {
			serverSocket = new DatagramSocket(port);
		} catch (SocketException e1)
		{
			System.out.println("Socket Error.");
			e1.printStackTrace();
		}
		int localPointer = 0;
		System.out.println("Socket created");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		boolean flag = true;
		DatagramPacket clientPacket = null;
		while(flag)
		{
			try {
			byte[] dataPacket = new byte[2048];
			clientPacket = new DatagramPacket(dataPacket, dataPacket.length);
			serverSocket.receive(clientPacket);
			double rand = Math.random();
		//	int localPointer = 0;
			String clientData = new String(clientPacket.getData()).substring(0, clientPacket.getLength());
			seqNum = binToDec(clientData.substring(0, 32));
			checksum = binToDec(clientData.substring(32, 48));
			packetType = clientData.substring(48, 64);
			System.out.println("Received Packet of seq number: "+seqNum);
			if(packetType.equals("0000000000000000"))
			{
				flag = false;
				break;
			}
			String data = clientData.substring(64, clientData.length());
			if(rand <= probability)
			{
				System.out.println("Packet Lost; Seq num: "+seqNum);
				continue;
			}
			else if(validateCheckSum(data, checksum) == 0 && seqNum == localPointer)
			{
				out.write(data.getBytes());
				InetAddress client_IP = clientPacket.getAddress();
				int client_port = clientPacket.getPort();
				byte[] ack = ackServer(seqNum);
				DatagramPacket ackToClient = new DatagramPacket(ack, ack.length, client_IP, client_port);
				serverSocket.send(ackToClient);
				System.out.println("ACK sent for seq num: "+seqNum);
				localPointer++;
			}	
			}catch(Exception e)
			{
				System.out.println("ERROR");
				e.printStackTrace();
			}
		}
		FileOutputStream fp = null;
		try{
			fp = new FileOutputStream(file);
			out.writeTo(fp);
			fp.close();
		} catch(Exception e) {
			System.out.println("File write not successful");
		}
		serverSocket.close();
	}

	public static int binToDec(String s)
	{
		int x=0, val = 0;
		for(int i = s.length()-1; i>=0; i--)
		{
				
			if(s.charAt(i) == '1')  
				val +=  Math.pow(2,x);
			x++;
		}
		return val;
	}

	public static byte[] ackServer(int seqNum)
	{
		String header = Integer.toBinaryString(seqNum);
		for(int i = header.length(); i<32; i++)
			header = "0" + header;
		header = header + "00000000000000001010101010101010";
		return header.getBytes();
	}

	public static int validateCheckSum(String data, int checksum)
	{
		String hexString = new String();
		int i, value, result = 0;
		for(i = 0; i<data.length() - 2; i=i+2)
		{
			value = (int)(data.charAt(i));
			hexString = Integer.toHexString(value);
			value = (int)(data.charAt(i + 1));
			hexString = hexString + Integer.toHexString(value);
			value = Integer.parseInt(hexString, 16);
			result = result + value;
		}
        	if (data.length() % 2 == 0) 
		{
            		value = (int) (data.charAt(i));
            		hexString = Integer.toHexString(value);
            		value = (int) (data.charAt(i + 1));
            		hexString = hexString + Integer.toHexString(value);
            		value = Integer.parseInt(hexString, 16);
        	} 
		else 
		{
            		value = (int) (data.charAt(i));
            		hexString = "00" + Integer.toHexString(value);
            		value = Integer.parseInt(hexString, 16);
        	}
        	result += value;
        	hexString = Integer.toHexString(result);
       		 if (hexString.length() > 4) 
		 {
            		int carry = Integer.parseInt(("" + hexString.charAt(0)), 16);
            		hexString = hexString.substring(1, 5);
            		result = Integer.parseInt(hexString, 16);
            		result += carry;
        	}
        	result = Integer.parseInt("FFFF", 16) - result;
        	result = Integer.parseInt("FFFF", 16) - result;
		int valid = result + checksum;
        	valid = Integer.parseInt("FFFF", 16) - valid;
        	return valid;
    	}
}

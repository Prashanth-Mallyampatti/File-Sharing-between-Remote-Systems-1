import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.nio.file.*;

class Segment
{
	int index; 
	Segment next;
	String data;

	public Segment(int index, String data)
	{
		this.index = index;
		this.next = null;
		this.data = data;
	}
}
public class Client
{
	public static Segment head;
	public static int port, N, mss;
	public static String file, host;
	public Client()
	{
		head = null;
	}

	public static void main(String[] args) throws IOException
	{
		System.out.println("In client");
		if(args.length > 4)
		{
			host = args[0];
			port = Integer.parseInt(args[1]);
			file = args[2];
			N = Integer.parseInt(args[3]);
			mss = Integer.parseInt(args[4]);
		}
		DatagramSocket clientSocket = null;
		try {
			clientSocket = new DatagramSocket();
		} catch(Exception e0)
		{
			System.out.println("Client Socket Error");
			e0.printStackTrace();
		}

		InetAddress serverIp = null;
		try {
			serverIp = InetAddress.getByName(host);
		} catch(UnknownHostException e6)
		{
			System.out.println("Host error");
			e6.printStackTrace();
		}
		Path fp = Paths.get(file);
		byte[] dataPacket = null;
		try {
			dataPacket = Files.readAllBytes(fp);
		} catch(IOException e1)
		{
			System.out.println("client io exception");
			e1.printStackTrace();
		}
	//	numPackets = (int) Math.ceil((double) dataPacket.length / mss);
		dividePacket(dataPacket);
		int localPointer = 0, ptr = 0, ack = -1;
		while((localPointer * mss) < dataPacket.length)
		{
			while(ptr < N && (localPointer * mss) < dataPacket.length)
			{
				Segment temp = head;
				while(temp.index != localPointer)
					temp = temp.next;
				String s = temp.data;
				byte[] header = addHeader(localPointer, s);
				byte[] dataB = s.getBytes();
				byte[] packet = new byte[header.length + dataB.length];
				for(int i=0, j=0; i<packet.length; i++)
				{
					if(i < header.length)
						packet[i] = header[i];
					else
					{
						packet[i] = dataB[j];
						j++;
					}
				}
				DatagramPacket toServer = new DatagramPacket(packet, packet.length, serverIp, port);
				try {
					clientSocket.send(toServer);
					System.out.println("Packet sent: " + localPointer);
					localPointer++;
					ptr++;
				} catch(Exception e2)
				{
					System.out.println("Error sending packet");
					e2.printStackTrace();
				}
			}

			int timeout = 1000;
			byte[] receive = new byte[1536];
			DatagramPacket server = new DatagramPacket(receive, receive.length);
			boolean flag = true;
			int temp = localPointer;
			try {
				clientSocket.setSoTimeout(timeout);
				while(flag)
				{
					clientSocket.receive(server);
					ack = checkAck(server.getData());
					System.out.println("ACK received for: " + ack);
					if(ack == temp -1)
					{
						ptr = 0;
						localPointer = temp;
						flag = false;
					}
					else if(ack != -1)
					{
						ptr = localPointer - ack - 1;
						localPointer = ack + 1;
					}
				}
			} catch(SocketTimeoutException e4)
			{
				System.out.println("Timeout for seq num: " + ack);
				localPointer = ack + 1;
				ptr = 0;
			}
		}
		String eof = "0000000000000000000000000000000000000000000000000000000000000000000000000000";
		byte[] eof_ = eof.getBytes();
		DatagramPacket eofPacket = new DatagramPacket(eof_, eof_.length, serverIp, port);
		clientSocket.send(eofPacket);
	}
	
	public static byte[] addHeader(int num, String data)
	{
		String seq = Integer.toBinaryString(num);        
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
		String pad = Integer.toBinaryString(result);
		for(int j=pad.length(); j<16; j++)
			pad = "0" + pad;


		String str = "0101010101010101";
		for(int k = seq.length(); k<32; k++)
			seq = "0" + seq;
		String total = seq + pad + str;
		return total.getBytes();
	}

	public static void dividePacket(byte[] dataPacket)
	{
		int numPackets = (int) Math.ceil((double) dataPacket.length / mss);
		String data = new String(dataPacket);
		for(int i=0; i<numPackets; i++)
		{
			int j = mss * (i+1);
			if(j > data.length())
				j = data.length();
			Segment seg = new Segment(i, data.substring(mss * i, j));
			if(head == null)
				head = seg;
			
			else
			{
				Segment temp = head; 
				while(temp.next != null)
					temp = temp.next;
				temp.next = seg;
			}
		}
	}
	
	public static int checkAck(byte[] data)
	{
		String ack = "";
		for(int i=0; i<64; i++)
		{
			if(data[i] == 48)
				ack += "0";
			else
				ack += "1";
		}
		String packetType = ack.substring(48, 64);
		if(packetType.equals("1010101010101010"))
		{
			return binToDec(ack.substring(0,32)); 
		}
		return -1;
	}

	public static int binToDec(String s)
	{
		int x=0, val = 0;
		for(int i=s.length()-1; i>=0; i--)
		{
			if(s.charAt(i) == '1')
				val += Math.pow(2, x);
			x++;
		}
		return val;
	}
	
}

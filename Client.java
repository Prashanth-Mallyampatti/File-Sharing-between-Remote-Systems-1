import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.nio.file.*;

class Segment
{ 
	protected int index; 
	protected Segment next;
	protected String data;

	protected Segment(int index, String data)
	{
		this.index = index;
		this.next = null;
		this.data = data;
	}
}
public class Client
{
	private static Segment head;
	private static int port, N, mss, numPackets, localPointer = 0, windowPointer = 0, ack = -1;
	private static String file, host;
	private static List<Integer> sentPackets = new ArrayList<Integer>();
	private static List<Integer> acksReceived = new ArrayList<Integer>();
	protected static DatagramSocket clientSocket = null;

	protected Client()
	{
		head = null;
	}

	public static void main(String[] args) throws IOException
	{
		System.out.println("\nClient started with Go-Back-N ARQ protocol.");
		if(args.length > 4)
		{
			host = args[0];
			port = Integer.parseInt(args[1]);
			file = args[2];
			N = Integer.parseInt(args[3]);
			mss = Integer.parseInt(args[4]);
		}

		int window[]= new int[N];
		try {
			clientSocket = new DatagramSocket();
		} catch(Exception e0)
		{
			System.out.println("\nClient Socket Error");
			e0.printStackTrace();
		}
		
		//get server ip address
		InetAddress serverIp = null;
		try {
			serverIp = InetAddress.getByName(host);
		} catch(UnknownHostException e6)
		{
			System.out.println("\nHost error");
			e6.printStackTrace();
		}

		//read data to send
		Path fp = Paths.get(file);
		byte[] dataToSend = null;
		try {
			dataToSend = Files.readAllBytes(fp);
		} catch(IOException e1)
		{
			System.out.println("\nclient IO exception");
			e1.printStackTrace();
		}

		File f = new File(file);
		System.out.println("Size of file: " + f.length() + "\n");
		
		//calculate the number of packets to be generated based on the file size and generate the packets
		numPackets = (int) Math.ceil((double) f.length() / mss);
		dividePacket(dataToSend);

		//Increment in size of MSS till the last packet
		while((localPointer * mss) < dataToSend.length)
		{	
			//Transmit data to server
			sendPacketToServer(dataToSend, serverIp, window);

			//Receive ACK from server
			receiveACK(window);
		}

		//Send an EOF packet.
		sendEOF(serverIp);

		//Close client
		System.out.println("\n\nAll data sent.\n\nClient closing..");
		clientSocket.close();
	}

	private static void sendPacketToServer(byte[] dataToSend, InetAddress serverIp, int[] window)
	{
		for(windowPointer=0; windowPointer<N; windowPointer++)
		{
			if((localPointer * mss) > dataToSend.length)
				break;
			if(window[windowPointer] == 2)
			{
				localPointer++;
				continue;
			}
			//Get packet parameters from local pointer
			Segment temp = head;
			while(temp.index != localPointer && temp != null)
				temp = temp.next;
			String s = temp.data;

			//Add header to the packet
			byte[] header = addHeader(localPointer, s);
			byte[] dataB = s.getBytes();
			byte[] packet = new byte[header.length + dataB.length];

			for(int i = 0, j = 0; i<packet.length; i++)
			{
				if(i < header.length)
					packet[i] = header[i];
				else
				{
					packet[i] = dataB[j];
					j++;
				}
			}

			//send packet to server
			DatagramPacket toServer = new DatagramPacket(packet, packet.length, serverIp, port);
			try {
				clientSocket.send(toServer);
				System.out.println("Packet Sent: " + localPointer);
				localPointer++;
				window[windowPointer] = 1;
			} catch(Exception e2)
			{
				System.out.println("\nError sending packet");
				e2.printStackTrace();
			}
		}

	}
	
	private static void receiveACK(int[] window) throws IOException
	{
		//standard eth mss is 1540 bytes
		byte[] receive = new byte[1540];

		//to receive ACKs from server
		DatagramPacket server = new DatagramPacket(receive, receive.length);
		boolean flag = true;
		localPointer = localPointer - windowPointer;
		try {
	
			//set timeout of 1000ms
			clientSocket.setSoTimeout(1000);
			while(flag)
			{
				clientSocket.receive(server);
				//loop until you get ACKs for all packets you sent earlier
				ack = checkAck(server.getData());
				System.out.println("ACK received for: " + ack);

				//if you receive any other, other than negative ACK, advance the window pointer to that packet
				if(ack != -1)
				{
					int x = ack - localPointer;
					window[ack - localPointer] = 2;
					if((ack - localPointer) == 0)
					{
						while(window[x] == 2)
						{
							for(int i=1; i<N; i++)
								window[i - 1] = window[i];
						//	window[N - 1] = -1;
							localPointer++;
						}
					}
				}
			}
		} catch(SocketTimeoutException e4)
		{
			//If timed out, set the pointer to next packet of last received ACK
			System.out.println("Timeout, sequence number: " + (ack+1));
		}
	}

	private static void sendEOF(InetAddress serverIp) throws IOException
	{
		String eof = "0000000000000000000000000000000000000000000000000000000000000000000000000000";
		byte[] eof_ = eof.getBytes();
		DatagramPacket eofPacket = new DatagramPacket(eof_, eof_.length, serverIp, port);
		clientSocket.send(eofPacket);
	}

	//add header to the client data
	private static byte[] addHeader(int num, String data)
	{
		String seq = Long.toBinaryString( num & 0xffffffffL | 0x100000000L ).substring(1);

		//compute the checksum
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

		String checksum = Long.toBinaryString( result & 0xfffffL | 0x10000L ).substring(1);

		String str = "0101010101010101";

		//Add padding and checksum to the data
		String total = seq + checksum + str;
		return total.getBytes();
	}

	//make packets and store it is a class list
	private static void dividePacket(byte[] dataPacket)
	{
		String data = new String(dataPacket);
		for(int i=0; i<numPackets; i++)
		{
			Segment seg = new Segment(i, data.substring(mss * i, Math.min(mss * (i+1), data.length())));
			if(head != null)
			{
				Segment temp = head; 
				while(temp.next != null)
					temp = temp.next;
				temp.next = seg;
				//head = temp;
			}
			else
				head = seg;
		}
	}

	//check the ACK packet and determine to which sequence its ack'ed for
	private static int checkAck(byte[] data)
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

	//convert binary to decimal value
	private static int binToDec(String s)
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

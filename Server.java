import java.io.*;
import java.net.*;
import java.util.*;
import java.time.*;

class serverSegment
{
	protected int index;
	protected String data;
	protected serverSegment next;

	protected serverSegment(int index, String data)
	{
		this.index = index;
		this.next = null;
		this.data = data;
	}
}

public class Server
{
	private static serverSegment head;
	public Server()
	{
		head = null;
	}
	private static int port, seqNum = 0, checksum = 0;
	private static float probability;
	private static String file, packetType;
	private static List<Integer> acksSent = new ArrayList<Integer>();
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
			System.exit(-1);
		}
	
		int localPointer = 0;
		System.out.println("Server socket created\nWaiting for packets....\n");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		boolean flag = true;

		while(flag)
		{
			try {
			byte[] dataPacket = new byte[2048];
			DatagramPacket clientPacket = new DatagramPacket(dataPacket, dataPacket.length);
			serverSocket.receive(clientPacket);
			double rand = Math.random();

			String clientData = new String(clientPacket.getData()).substring(0, clientPacket.getLength());
			seqNum = binToDec(clientData.substring(0, 32));
			//System.out.println("Packet Received: "+ seqNum);
			packetType = clientData.substring(48, 64);

			//EOF
			if(packetType.equals("0000000000000000"))
			{
				flag = false; 
				break;
			}
			
			//Get Data from client packet
			String data = clientData.substring(64, clientData.length());
			checksum = binToDec(clientData.substring(32, 48));
			
			//Discarding Packet
			if(rand <= probability)
			{
				System.out.println("Packet loss, Sequence number: " + seqNum);
				continue;
			}
			//validate the data and slide the window accordingly
			if(validateCheckSum(data) == 0 && seqNum == localPointer)
			{
				InetAddress client_IP = clientPacket.getAddress();
				int client_port = clientPacket.getPort();
				byte[] ack = ackServer(seqNum);
				
				//Send ACK for the data received.
				DatagramPacket ackToClient = new DatagramPacket(ack, ack.length, client_IP, client_port);
				serverSocket.send(ackToClient);
				//System.out.println("ACK sent for: "+seqNum);
				
				acksSent.add(seqNum);
				//Mark local pointer assuming ACK will reach successfully
				localPointer++;
				out.write(data.getBytes());
				
				//slide the server window to until which it has sent ACKs for
				while(head != null)
				{
					if(head.index == localPointer)
					{
						//write the data for which the ACKs have already been sent
						out.write(head.data.getBytes());
						head = head.next;
						localPointer++; //maintain the order of receiving
						continue;
					}
					break;
				}
				continue;
			}
			
			//send ACKs for packets received after a packet loss, but dont write it to file
			if(validateCheckSum(data) == 0 && seqNum > localPointer)
			{
				InetAddress client_IP = clientPacket.getAddress();
				int client_port = clientPacket.getPort();
				byte[] ack = ackServer(seqNum);
				
				//Send ACK for the data received.
				DatagramPacket ackToClient = new DatagramPacket(ack, ack.length, client_IP, client_port);
				serverSocket.send(ackToClient);
				//System.out.println("ACK sent for: "+seqNum);
				
				acksSent.add(seqNum);

				//generate a window and make head point to it
				serverSegment seg_ = new serverSegment(seqNum, data);
				if(head == null)
					head = seg_;
				else
				{
					serverSegment temp = head;
					serverSegment temp_prev = head;
					while(temp.next != null && temp.index < seqNum)
					{
						temp_prev = temp;
						temp = temp.next;
					}
					if(temp.index < seqNum)
						temp.next = seg_;
					else
					{
						if(temp_prev != temp)
							temp_prev.next = seg_;
						else
							head = seg_;
						seg_.next = temp;
					}
				}
			}
			
			}catch(Exception e)
			{
				System.out.println("ERROR");
				e.printStackTrace();
				System.exit(-1);
			}
		}

		System.out.println("\nACKs sent to client: \n");
		for(int i = 0; i < acksSent.size(); i++)
			System.out.print(acksSent.get(i) + ", ");

		//Store the client data to 'file'
		System.out.println("\n\nWriting received data to file...");
		FileOutputStream fp = null;
		try{
			fp = new FileOutputStream(file);
			out.writeTo(fp);
			fp.close();
			System.out.println("\nFile write successful");
		} catch(Exception e) {
			System.out.println("File write not successful");
			e.printStackTrace();
			System.exit(-1);
		}
		
		try {
			validateFile();
		} catch (Exception fe)
		{
			System.out.println("File check error");
			fe.printStackTrace();
		}

		System.out.println("\nClosing socket....");	
		serverSocket.close();
	}

	//convert client header to decimal for validation
	private static int binToDec(String s)
	{
		int x=0, y = 0, val = 0;
		for(int i = s.length()-1; i>=0; i--)
		{
			if(s.charAt(i) == '1')  
				val += Math.pow(2, x);
			x++;
		}
		return val;
	}

	//create ACK packet for 'seqNum' to send to client
	private static byte[] ackServer(int seqNum)
	{
		String header = Integer.toBinaryString(seqNum);
		for(int i = header.length(); i<32; i++)
			header = "0" + header;
		header = header + "00000000000000001010101010101010";
		return header.getBytes();
	}

	//checksum for client data
	private static float validateCheckSum(String data)
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
            		value = (int)(data.charAt(i));
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
		
		return Integer.parseInt("FFFF", 16) - result - checksum;
    	}

	//Check if the file is transferred correctly
	private static void validateFile() throws Exception
	{
		System.out.println("\nValidating file write...");
		String first = "", second = "";
		Scanner input1 = new Scanner(new File("test.txt"));
		Scanner input2 = new Scanner(new File(file));
		boolean diff = false; //No differences
		while(input1.hasNextLine() && input2.hasNextLine())
		{
    			first = input1.nextLine();
    			second = input2.nextLine(); 

    			if(!first.equals(second))
			{
				diff = true;
        			System.out.println("Differences found: "+"\n"+first+'\n'+second);
    			}
		}	
		if(diff == false)
			System.out.println("\nFile validated, No differences found.");
	}
}

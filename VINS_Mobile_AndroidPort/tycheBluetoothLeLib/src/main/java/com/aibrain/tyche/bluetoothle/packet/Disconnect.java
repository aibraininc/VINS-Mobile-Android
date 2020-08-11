package com.aibrain.tyche.bluetoothle.packet;

import com.aibrain.tyche.bluetoothle.constants.Command;
import com.aibrain.tyche.bluetoothle.constants.Packet;

public class Disconnect {

	public static final byte LENGTH = 0x00;
	
	public byte[] getCommand() {
		return formatPacket();
	}
		
	public byte[] formatPacket() {
		
		byte[] packet = new byte[4];
		
		packet[0] = Packet.START;
		packet[1] = LENGTH;
		packet[2] = Command.DISCONNECT;
		packet[3] = Packet.END;
				
		return packet;
	}
	
}

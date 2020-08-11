package com.aibrain.tyche.bluetoothle.packet;

import com.aibrain.tyche.bluetoothle.constants.Command;
import com.aibrain.tyche.bluetoothle.constants.Packet;

public class Headlight {
			
	public static final byte LENGTH = 0x01;
		
	public byte[] getCommand(boolean isOn) {
		return formatPacket(isOn);
	}
	
	private byte[] formatPacket(boolean isOn) {
		
		byte[] packet = new byte[5];
		
		packet[0] = Packet.START;
		packet[1] = LENGTH;
		packet[2] = Command.HEADLIGHT;
		packet[3] = (byte)(isOn? 0x01 : 0x00);
		packet[4] = Packet.END;
				
		return packet;		
	}
	
}

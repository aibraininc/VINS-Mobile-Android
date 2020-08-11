package com.aibrain.tyche.bluetoothle.packet;

import com.aibrain.tyche.bluetoothle.constants.Command;
import com.aibrain.tyche.bluetoothle.constants.Packet;

public class Connect {

	public static final byte LENGTH = 0x02;
	public static final byte BATT_THRESHOLD = (byte)0xa0;
	public static final byte ENCODER_THRESHOLD = 0x08;

	public byte[] getCommand() {
		return formatPacket();
	}

	public byte[] formatPacket() {

		byte[] packet = new byte[6];

		packet[0] = Packet.START;
		packet[1] = LENGTH;
		packet[2] = Command.CONNECT;
		packet[3] = ENCODER_THRESHOLD;		// encoder threshold
		packet[4] = BATT_THRESHOLD;			// batt threshold
		packet[5] = Packet.END;

		return packet;
	}

}

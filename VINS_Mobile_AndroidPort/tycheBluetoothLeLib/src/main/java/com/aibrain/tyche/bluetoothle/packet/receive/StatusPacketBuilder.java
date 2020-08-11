package com.aibrain.tyche.bluetoothle.packet.receive;

import com.aibrain.tyche.bluetoothle.constants.Command;
import com.aibrain.tyche.bluetoothle.constants.Packet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class StatusPacketBuilder {

	private static final int STATUS_PACKET_SIZE = 20;

	private static final int DISTANCE_IDX = 3;
	private static final int HEADLIGHT_IDX = 5;
	private static final int LEFT_WHEEL_VELOCIRY_IDX = 6;
	private static final int RIGHT_WHEEL_VELOCIRY_IDX = 7;
	private static final int LEFT_ENCODER_POSION_IDX = 8;
	private static final int RIGHT_ENCODER_POSITION_IDX = 10;
	private static final int BATTERY_PERCENTAGE_IDX = 12;
	private static final int FIRMWARE_VERSION_CODE_IDX = 13;
	private static final int SERIAL_NUMBER_IDX = 15;

	private static final int DISTANCE_DATA_SIZE = 2;
	private static final int SERIAL_NUMBER_SIZE = 4;
	private static final int ENCODER_DATA_SIZE = 2;
	
	
	
	public static StatusData createStatus(byte[] packet) {
		if(packet[0] != Packet.START || packet[STATUS_PACKET_SIZE-1] != Packet.END
				|| packet[1]+4 != STATUS_PACKET_SIZE || packet[2] != Command.STATUS) {
			return null;
		}

		// distance
		ByteBuffer distance = ByteBuffer.wrap(packet, DISTANCE_IDX, DISTANCE_DATA_SIZE);
		distance.order(ByteOrder.LITTLE_ENDIAN);

		// left encoder position
		ByteBuffer leftEncPos = ByteBuffer.wrap(packet, LEFT_ENCODER_POSION_IDX, ENCODER_DATA_SIZE);
		leftEncPos.order(ByteOrder.LITTLE_ENDIAN);

		// right encoder position
		ByteBuffer rightEncPos = ByteBuffer.wrap(packet, RIGHT_ENCODER_POSITION_IDX, ENCODER_DATA_SIZE);
		rightEncPos.order(ByteOrder.LITTLE_ENDIAN);

		// serial number
		ByteBuffer serialNumer = ByteBuffer.wrap(packet, SERIAL_NUMBER_IDX, SERIAL_NUMBER_SIZE);
		serialNumer.order(ByteOrder.LITTLE_ENDIAN);

		StatusData status = new StatusData();
		status.setCommand(Command.STATUS);
		status.setDistance(distance.getShort());
		status.setHeadlight(packet[HEADLIGHT_IDX]!=0);
		status.setLeftWheelVelocity(packet[LEFT_WHEEL_VELOCIRY_IDX]);
		status.setRightWheelVelocity(packet[RIGHT_WHEEL_VELOCIRY_IDX]);
		status.setLeftEncPosition(leftEncPos.getShort());
		status.setRightEncPosition(rightEncPos.getShort());
		status.setBattery(packet[BATTERY_PERCENTAGE_IDX] & 0xff);
		status.setFirmwareVersionCode(packet[FIRMWARE_VERSION_CODE_IDX]);
		status.setSerialNumber(serialNumer.getInt());
		
		return status;
	}
	
	private static byte[] sReceiveBuffer = new byte[STATUS_PACKET_SIZE];
	private static int sBufferIndex = 0;
	
	/*
	 * If status packet fully is built then this method returns StatusPacket.
	 */
	public static byte[] createStatusPacket(byte[] packet) {
		int size = packet.length;
		
		// overflow
		if(sBufferIndex+size > STATUS_PACKET_SIZE) {
			sBufferIndex = 0;
			return null;
		}
		
		if(size>0 && size<STATUS_PACKET_SIZE) {
			System.arraycopy(packet, 0, sReceiveBuffer, sBufferIndex, size);
			sBufferIndex += size;
			// complete status packet
			if(sBufferIndex == STATUS_PACKET_SIZE) {
				sBufferIndex = 0;
				return sReceiveBuffer;
			}
		}
		
		return null;
	}
	
}

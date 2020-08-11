package com.aibrain.tyche.bluetoothle.packet;

import com.aibrain.tyche.bluetoothle.constants.Command;
import com.aibrain.tyche.bluetoothle.constants.Packet;

public class MotorPwm {
	
	public static final byte LENGTH = 0x02;
		
	public byte[] getCommand(int leftVelocity, int rightVelocity) {				
		return formatPacket(leftVelocity, rightVelocity);		
	}
	
	public byte[] formatPacket(int leftSpeed, int rightSpeed) {
		
		byte[] packet = new byte[6];
		
		packet[0] = Packet.START;
		packet[1] = LENGTH;
		packet[2] = Command.MOTOR_PWM;
		packet[3] = formatSpeed(leftSpeed);
		packet[4] = formatSpeed(rightSpeed);
		packet[5] = Packet.END;
				
		return packet;
	}
	
	private byte formatSpeed(int speed) {
		if(speed > 100) {
			speed = 100;
		}
		if(speed < -100) {
			speed = -100;
		}
		
		return (byte)speed;
	}
	
}

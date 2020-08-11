package com.aibrain.tyche.bluetoothle.packet;

import com.aibrain.tyche.bluetoothle.constants.Command;
import com.aibrain.tyche.bluetoothle.constants.Packet;

public class MotorRpm {
		
	public static final byte LENGTH = 0x03;
		
	public byte[] getCommand(int leftVelocity, int rightVelocity) {				
		return formatPacket(leftVelocity, rightVelocity);		
	}
	
	public byte[] formatPacket(int leftSpeed, int rightSpeed){
		
		byte[] packet = new byte[7];
		
		packet[0] = Packet.START;
		packet[1] = LENGTH;
		packet[2] = Command.MOTOR_RPM;
		packet[3] = setWheelDir(leftSpeed, rightSpeed);
		packet[4] = formatSpeed(leftSpeed);
		packet[5] = formatSpeed(rightSpeed);
		packet[6] = Packet.END;
				
		return packet;
		
	}
	private byte setWheelDir(int leftSpeed, int rightSpeed){
		byte dir = 0x00;
		if(leftSpeed>0){
			if(rightSpeed>0){
				dir = 0x11;
			}else if(rightSpeed==0){
				dir = 0x10;
			}else if(rightSpeed<0){
				dir = 0x1F;
			}
		}else if(leftSpeed==0){
			if(rightSpeed>0){
				dir = 0x01;
			}else if(rightSpeed==0){
				dir = 0x00;
			}else if(rightSpeed<0){
				dir = 0x0F;
			}
		}else if(leftSpeed<0){
			if(rightSpeed>0){
				dir = (byte) 0xF1;
			}else if(rightSpeed==0){
				dir = (byte) 0xF0;
			}else if(rightSpeed<0){
				dir = (byte) 0xFF;
			}
		}
		return dir;
	}
	private byte formatSpeed(int speed){
		byte tmpSpeed;
		if(speed>=0){
			if(speed>0xFF){
				tmpSpeed = (byte) 0xFF;
			}else{
				tmpSpeed = (byte) speed;
			}
		}else{
			if(speed<-0xFF){
				tmpSpeed = (byte) -0xFF;
			}else{
				tmpSpeed = (byte) -speed;
			}
		}
		return tmpSpeed;
	}
	
}

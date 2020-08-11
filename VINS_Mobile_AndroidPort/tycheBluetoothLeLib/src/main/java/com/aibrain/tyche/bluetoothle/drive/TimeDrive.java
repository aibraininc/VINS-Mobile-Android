package com.aibrain.tyche.bluetoothle.drive;

import com.aibrain.tyche.bluetoothle.constants.Direction;
import com.aibrain.tyche.bluetoothle.constants.Mode;

public class TimeDrive extends Drive {

	private Direction mDirection;
	private int mDuration = 0;
	private int mVelocity = 0;
	
	public TimeDrive() {
		mMode = Mode.TIME;
	}
	public void setDirection(Direction dir) {
		mDirection = dir;
	}
	
	public Direction getDirection() {
		return mDirection;
	}
	
	public void setDuration(int durationMs) {
		mDuration = durationMs;
	}
	
	public int getDuration() {
		return mDuration;
	}
	
	public void setVelocity(int velocity) {
		mVelocity = velocity;
	}
	
	public int getVelocity() {
		return mVelocity;
	}
	
}

package com.aibrain.tyche.bluetoothle.executor;

import com.aibrain.tyche.bluetoothle.BluetoothLeManager;

public class TimeBaseMoveExecutor extends TimeBaseExecutor {

	private int mLeftVelocity = 0;
	private int mRightVelocity = 0;
	
	public TimeBaseMoveExecutor(int durationMs, int leftVel, int rightVel) {
		super(durationMs);
		
		mLeftVelocity = leftVel;
		mRightVelocity = rightVel;
	}

	@Override
	protected void operate() {
		BluetoothLeManager.getInstance().requestChangeWheelVelocity(mLeftVelocity, mRightVelocity);
	}
	
	@Override
	protected boolean detect() {
		return false;
	}

	@Override
	protected void stop() {
		BluetoothLeManager.getInstance().requestChangeWheelVelocity(0, 0);
	}
	
}

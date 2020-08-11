package com.aibrain.tyche.bluetoothle.executor;

import com.aibrain.tyche.bluetoothle.BluetoothLeManager;

public class ContinuousMoveExecutor extends ContinuousExecutor {

	private int mLeftVelocity = 0;
	private int mRightVelocity = 0;
	
	public ContinuousMoveExecutor(int leftVel, int rightVel) {
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

}

package com.aibrain.tyche.bluetoothle.executor;

import com.aibrain.tyche.bluetoothle.BluetoothLeManager;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

public class ContinuousObstacleDetectingMoveExecutor extends ContinuousMoveExecutor {

	private OnObstacleDetectedListener mOnObstacleDetectedListener;
	
	public ContinuousObstacleDetectingMoveExecutor(int leftVel, int rightVel, OnObstacleDetectedListener listener) {
		super(leftVel, rightVel);
		if(leftVel>0 && rightVel>0) {
			mOnObstacleDetectedListener = listener;
		}
	}
	
	@Override
	protected boolean detect() {
		if(mOnObstacleDetectedListener == null) {
			return false;
		}
		else {
			StatusData status = BluetoothLeManager.getInstance().getCurrentStatusData();
			if(status != null) {
				int leftVel = status.getLeftWheelVelocity();
				int rightVel = status.getRightWheelVelocity();
				if(leftVel>0 && rightVel>0) {
					int distance = status.getDistance();
					if(distance>0 && distance < Math.max(leftVel, rightVel)/3) {
						BluetoothLeManager.getInstance().requestChangeWheelVelocity(0, 0);
						if(mOnObstacleDetectedListener != null) {
							mOnObstacleDetectedListener.onDetected();
						}
						return true;
					}
				}
			}
			
			return false;
		}
	}

}

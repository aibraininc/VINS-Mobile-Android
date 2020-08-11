package com.aibrain.tyche.bluetoothle.drive;

import com.aibrain.tyche.bluetoothle.constants.Mode;

public class RotateDrive extends Drive {

	private int mAngle = 0;
	
	public RotateDrive(Mode mode) {
		mMode = mode;
	}
	
	public void setAngle(int angle) {
		mAngle = angle;
	}
	
	public int getAngle() {
		return mAngle;
	}

}

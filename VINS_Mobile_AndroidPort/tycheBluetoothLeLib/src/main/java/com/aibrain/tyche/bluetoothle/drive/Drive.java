package com.aibrain.tyche.bluetoothle.drive;

import com.aibrain.tyche.bluetoothle.constants.Mode;

public abstract class Drive {
	
	protected Mode mMode;
	private int mRestTime = 1;
		
	public Mode getMode() {
		return mMode;
	}

	public void setRestTime(int duration) {
		mRestTime = duration;
	}

	public int getRestTime() {
		return mRestTime;
	}
	
}

package com.aibrain.tyche.bluetoothle.executor;

import android.os.SystemClock;

public abstract class TimeBaseExecutor extends Executor {

	private int mDuration = 0;
	
	public TimeBaseExecutor(int durationMs) {
		mDuration = durationMs;
	}
	
	protected abstract void operate();
	protected abstract boolean detect();
	protected abstract void stop();
	
	@Override
	protected void doJob() {
		operate();
		long startTime = SystemClock.elapsedRealtime();
		while(SystemClock.elapsedRealtime() - startTime < mDuration) {
			if(isCanceled() || detect()) {
				break;
			}
			try { Thread.sleep(1); }catch(InterruptedException e) {}
		}
		stop();
	}

}

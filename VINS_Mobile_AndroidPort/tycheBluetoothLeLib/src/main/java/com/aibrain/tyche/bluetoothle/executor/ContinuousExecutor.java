package com.aibrain.tyche.bluetoothle.executor;

public abstract class ContinuousExecutor extends Executor {

	protected abstract void operate();
	protected abstract boolean detect();
	
	@Override
	protected void doJob() {
		operate();
		while(!isCanceled() && !detect()) {
			try { Thread.sleep(1); }catch(InterruptedException e) {}
		}
	}

}

package com.aibrain.tyche.bluetoothle.executor;

import java.util.LinkedList;

public class ExecutorManager extends Thread {

	final private LinkedList<Executor> mExecutors = new LinkedList<Executor>();
	
	private boolean mIsRunning = false;
	private Boolean mCanceled = false;

	@Override
	public void run() {
		mIsRunning = true;
		while(mIsRunning) {
			mCanceled = false;
			Executor executor;
			synchronized(mExecutors) {
				executor = mExecutors.poll();	
			}
			if(executor != null) {
				executor.execute();
				while(!executor.isFinished()) {
					if(mCanceled) {
						executor.cancel();
					}
					try { Thread.sleep(1); }catch(InterruptedException e) { e.printStackTrace(); }
				}
				executor.join();
				try { Thread.sleep(executor.getRestTime()); }catch(InterruptedException e) { e.printStackTrace(); }
			}
			try { Thread.sleep(1); }catch(InterruptedException e) { e.printStackTrace(); }
		}
	}
	
	public void setRunning(boolean isRunning) {
		mIsRunning = isRunning;
	}
	
	public void add(Executor executor) {
		synchronized(mExecutors) {
			mExecutors.add(executor);
		}
	}
	
	public void addFirst(Executor executor) {
		synchronized(mExecutors) {
			mExecutors.addFirst(executor);
		}
	}
	
	public void removeAll() {
		synchronized(mExecutors) {
			mExecutors.clear();
		}
	}
	
	public void cancelCurrent() {
		mCanceled = true;
	}
	
	public void cancelAll() {
		removeAll();
		cancelCurrent();
	}

}

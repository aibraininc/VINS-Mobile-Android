package com.aibrain.tyche.bluetoothle.executor;

public abstract class Executor {

	public interface OnFinishListener {
		void onFinish(boolean isCanceled);
	}
	
	private OnFinishListener mOnFinishListener;
	private boolean mCanceled = false;
	private boolean mFinished = false;

	private int mRestTime = 0;

	private ExecuteThread mExecuteThread;
	
	public void setOnFinishListener(OnFinishListener listener) {
		mOnFinishListener = listener;
	}
	
	public void execute() {
		if(mExecuteThread == null) {
			mExecuteThread = new ExecuteThread();
			mExecuteThread.start();
		}
	}
	
	public void join() {
		if(mExecuteThread != null) {
			try {
				mExecuteThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void cancel() {
		mCanceled = true;
	}
	
	protected boolean isCanceled() {
		return mCanceled;
	}
	
	protected void finish() {
		mFinished = true;
	}
	
	public boolean isFinished() {
		return mFinished;
	}

	public void setRestTime(int duration) {
		mRestTime = duration;
	}

	public int getRestTime() {
		return mRestTime;
	}

	protected abstract void doJob();
	
	private class ExecuteThread extends Thread {

		@Override
		public void run() {
			doJob();
			finish();
			if(mOnFinishListener != null) {
				mOnFinishListener.onFinish(isCanceled());
			}
		}
		
	}
}

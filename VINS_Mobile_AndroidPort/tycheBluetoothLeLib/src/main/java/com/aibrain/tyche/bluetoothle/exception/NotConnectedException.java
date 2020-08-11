package com.aibrain.tyche.bluetoothle.exception;

public class NotConnectedException extends Exception {
	
	public NotConnectedException() {
		this("System is not connected to TYCHE.");
	}
	
	public NotConnectedException(String msg) {
		super(msg);
	}
	
}

package net.pp3345.yubidroid.yubikey;

public class YubiKeyException extends Exception {
	public YubiKeyException() {
	}

	public YubiKeyException(final String message) {
		super(message);
	}

	public YubiKeyException(final Throwable cause) {
		super(cause);
	}
}

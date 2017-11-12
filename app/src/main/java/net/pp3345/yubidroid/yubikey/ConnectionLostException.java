package net.pp3345.yubidroid.yubikey;

public class ConnectionLostException extends YubiKeyException {
	public ConnectionLostException(final Throwable cause) {
		super(cause);
	}
}

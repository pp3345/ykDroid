package net.pp3345.yubidroid.yubikey;

/**
 * Thrown when the connection to a YubiKey was lost during a NFC operation.
 */
public class ConnectionLostException extends YubiKeyException {
	public ConnectionLostException(final Throwable cause) {
		super(cause);
	}
}

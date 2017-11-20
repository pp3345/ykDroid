package net.pp3345.yubidroid.apdu;

/**
 * ISO 7816-4 5.3.3
 */
public class ResponseApdu {
	protected final byte[] response;

	public ResponseApdu(final byte[] response) {
		this.response = response;
	}

	public boolean isSuccess() {
		return this.response[this.response.length - 2] == (byte) 0x90 && this.response[this.response.length - 1] == (byte) 0x00;
	}
}

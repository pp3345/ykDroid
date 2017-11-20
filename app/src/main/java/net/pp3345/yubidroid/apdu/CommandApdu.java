package net.pp3345.yubidroid.apdu;

/**
 * ISO 7816-4 5.3.1
 */
public abstract class CommandApdu {
	protected byte[] data;

	public byte[] build() {
		assert this.getParameters().length == 2;

		final byte[] apdu = new byte[6 + this.data.length];
		apdu[0] = this.getCommandClass();
		apdu[1] = this.getInstruction();
		apdu[2] = this.getParameters()[0];
		apdu[3] = this.getParameters()[1];
		apdu[4] = (byte) this.data.length;
		System.arraycopy(this.data, 0, apdu, 5, this.data.length);
		apdu[apdu.length - 1] = this.getExpectedLength();

		return apdu;
	}

	protected abstract byte getCommandClass();

	protected abstract byte getInstruction();

	protected abstract byte[] getParameters();

	protected abstract byte getExpectedLength();

	public ResponseApdu parseResponse(final byte[] response) {
		return new ResponseApdu(response);
	}
}

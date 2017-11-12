package net.pp3345.yubidroid.apdu.command.ykoath;

import net.pp3345.yubidroid.apdu.CommandApdu;
import net.pp3345.yubidroid.apdu.response.ykoath.PutResponseApdu;
import net.pp3345.yubidroid.yubikey.Slot;

/**
 * https://developers.yubico.com/OATH/YKOATH_Protocol.html
 */
public class PutApdu extends CommandApdu {
	private final Slot slot;

	public PutApdu(final Slot slot, final byte[] challenge) {
		this.slot = slot;
		this.data = challenge;
	}

	@Override
	protected byte getCommandClass() {
		return (byte) 0x00;
	}

	@Override
	protected byte getInstruction() {
		return (byte) 0x01;
	}

	@Override
	protected byte[] getParameters() {
		return new byte[]{this.slot.getAddress(), (byte) 0x00};
	}

	@Override
	protected byte getExpectedLength() {
		return (byte) 0x00;
	}

	@Override
	public PutResponseApdu parseResponse(final byte[] response) {
		return new PutResponseApdu(response);
	}
}

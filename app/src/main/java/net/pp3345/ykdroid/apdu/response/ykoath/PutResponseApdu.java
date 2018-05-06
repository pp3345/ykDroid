package net.pp3345.ykdroid.apdu.response.ykoath;

import net.pp3345.ykdroid.YubiKey;
import net.pp3345.ykdroid.apdu.ResponseApdu;

public class PutResponseApdu extends ResponseApdu {
	public PutResponseApdu(final byte[] response) {
		super(response);
	}

	public byte[] getResult() {
		final byte[] result = new byte[YubiKey.CHALLENGE_RESPONSE_LENGTH];

		System.arraycopy(this.response, 0, result, 0, YubiKey.CHALLENGE_RESPONSE_LENGTH);

		return result;
	}
}

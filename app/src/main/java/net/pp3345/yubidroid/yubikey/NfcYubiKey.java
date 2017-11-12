package net.pp3345.yubidroid.yubikey;

import android.nfc.tech.IsoDep;

import net.pp3345.yubidroid.YubiKey;
import net.pp3345.yubidroid.apdu.command.iso.SelectFileApdu;
import net.pp3345.yubidroid.apdu.command.ykoath.PutApdu;
import net.pp3345.yubidroid.apdu.response.ykoath.PutResponseApdu;

import java.io.IOException;

public class NfcYubiKey implements YubiKey {
	private final IsoDep tag;

	public static final  String YUBIKEY_NEO_NDEF_SCHEME = "https";
	public static final  String YUBIKEY_NEO_NDEF_HOST   = "my.yubico.com";
	private static final byte[] CHALLENGE_AID           = new byte[]{(byte) 0xa0, 0x00, 0x00, 0x05, 0x27, 0x20, 0x01};

	public NfcYubiKey(final IsoDep tag) {
		this.tag = tag;
	}

	@Override
	public byte[] challengeResponse(final Slot slot, final byte[] challenge) throws YubiKeyException {
		slot.ensureChallengeResponseSlot();

		try {
			if (!this.tag.isConnected())
				this.tag.connect();

			final SelectFileApdu selectFileApdu = new SelectFileApdu(SelectFileApdu.SelectionControl.DF_NAME_DIRECT, SelectFileApdu.RecordOffset.FIRST_RECORD, CHALLENGE_AID);
			if (!selectFileApdu.parseResponse(this.tag.transceive(selectFileApdu.build())).isSuccess()) {
				throw new FailedOperationException();
			}

			final PutApdu         putApdu         = new PutApdu(slot, challenge);
			final PutResponseApdu putResponseApdu = putApdu.parseResponse(this.tag.transceive(putApdu.build()));

			if (!putResponseApdu.isSuccess()) {
				throw new FailedOperationException();
			}

			return putResponseApdu.getResult();
		} catch (final IOException e) {
			throw new ConnectionLostException(e);
		}
	}
}

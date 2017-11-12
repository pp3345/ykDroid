package net.pp3345.yubidroid;

import net.pp3345.yubidroid.yubikey.Slot;
import net.pp3345.yubidroid.yubikey.YubiKeyException;

public interface YubiKey {
	int CHALLENGE_RESPONSE_LENGTH = 20;

	byte[] challengeResponse(final Slot slot, final byte[] challenge) throws YubiKeyException;
}

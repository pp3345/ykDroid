package net.pp3345.ykdroid;

import net.pp3345.ykdroid.yubikey.InvalidSlotException;
import net.pp3345.ykdroid.yubikey.Slot;
import net.pp3345.ykdroid.yubikey.YubiKeyException;

/**
 * Interface that defines the YubiKey features that must be supported by the different driver
 * implementations
 */
public interface YubiKey {
	/**
	 * Length of a response to a challenge-response request (in bytes)
	 */
	int CHALLENGE_RESPONSE_LENGTH = 20;

	/**
	 * Sends a challenge to the YubiKey and returns the response received. May wait for the user to
	 * press the button on the YubiKey, depending on its configuration. Thus, this method should
	 * not be called on the UI thread to ensure good user experience.
	 *
	 * @param slot      The YubiKey feature slot to use. Must be either
	 *                  {@link Slot#CHALLENGE_HMAC_1} or {@link Slot#CHALLENGE_HMAC_2}.
	 * @param challenge Challenge bytes to send to the YubiKey.
	 * @return The response from the YubiKey.
	 * @throws InvalidSlotException When a slot was selected that can't be used for
	 *                              challenge-response.
	 * @throws YubiKeyException     Depending on the driver implementation, additional exceptions may
	 *                              be thrown.
	 */
	byte[] challengeResponse(final Slot slot, final byte[] challenge) throws YubiKeyException;
}

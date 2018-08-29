package net.pp3345.ykdroid;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import net.pp3345.ykdroid.yubikey.Slot;

/**
 * Manages the user's preferences for YubiKey slots based upon the called activity and a string that
 * uniquely identifies the purpose of the requested action set by the calling activity.
 */
class SlotPreferenceManager {
	private final SharedPreferences slotPreferences;

	private static final String SLOT_PREFERENCES_FILE_NAME = "slot_preferences";
	private static final String DEFAULT_IDENTIFIER         = "default";

	/**
	 * Should be instantiated exactly once by an activity that wants to store a slot preference.
	 *
	 * @param activity Required to store slot preferences are stored for each distinct activity.
	 */
	SlotPreferenceManager(final Activity activity) {
		this.slotPreferences = activity.getSharedPreferences(activity.getLocalClassName() + "_" + SLOT_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
	}

	/**
	 * Gets the preferred slot for a given unique purpose identifier.
	 *
	 * @param identifier  Uniquely identifies the purpose of the ykDroid request. Should be set by
	 *                    the invoking activity. If null or "" is passed, the default identifier will
	 *                    be used.
	 * @param defaultSlot The default slot to return in case no previous preferred slot is saved for
	 *                    the given purpose.
	 * @return Returns the slot that should be pre-selected for the given purpose.
	 */
	public Slot getPreferredSlot(final String identifier, final Slot defaultSlot) {
		if (this.isEmptyIdentifier(identifier)) {
			return this.getPreferredSlot(DEFAULT_IDENTIFIER, defaultSlot);
		}

		final int preference = this.slotPreferences.getInt(identifier, -1);
		if (preference == -1) {
			return defaultSlot;
		}

		// We could do a binary search here, but since we only have a very small amount of slots...
		for (final Slot slot : Slot.values()) {
			if (slot.getAddress() == preference)
				return slot;
		}

		throw new IllegalStateException();
	}

	/**
	 * Updates the preferred slot for a given unique purpose identifier. Should be called after and
	 * only if the requested YubiKey transaction was completed successfully. This method updates the
	 * preference asynchronously and can thus be safely called from the UI thread.
	 *
	 * @param identifier Uniquely identifies the purpose of the ykDroid request. Should be set by
	 *                   the invoking activity. If null or "" is passed, the default identifier will
	 *                   be used.
	 * @param slot       The slot to set as new preference for the given purpose.
	 */
	public void setPreferredSlot(final String identifier, final Slot slot) {
		if (this.isEmptyIdentifier(identifier)) {
			this.setPreferredSlot(DEFAULT_IDENTIFIER, slot);
			return;
		}

		final SharedPreferences.Editor editor = this.slotPreferences.edit();
		editor.putInt(identifier, slot.getAddress());
		editor.apply();
	}

	private boolean isEmptyIdentifier(final String identifier) {
		return identifier == null || identifier.equals("");
	}
}

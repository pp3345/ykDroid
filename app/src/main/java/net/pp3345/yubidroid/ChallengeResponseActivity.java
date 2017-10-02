package net.pp3345.yubidroid;

import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.util.Log;

public class ChallengeResponseActivity extends Activity implements UsbPermissionHandler.YubiKeyUsbReceiver {
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		(new UsbPermissionHandler(this)).waitForYubiKey(this);
	}

	@Override
	public void onYubiKeyConnected(final UsbDevice device, final UsbDeviceConnection connection) {
		final YubiKey yubiKey = new YubiKey(device, connection);

		try {
			final Intent result = new Intent();
			result.putExtra("response", yubiKey.challengeResponse(YubiKey.Slot.CHALLENGE_HMAC_2, this.getIntent().getByteArrayExtra("challenge")));

			this.setResult(RESULT_OK, result);
			this.finish();
		} catch (final UsbException e) {
			Log.e("YubiDroid", "Exception during challenge-response", e);
		}
	}
}

package net.pp3345.yubidroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

public class ChallengeResponseActivity extends Activity implements UsbConnectionManager.YubiKeyUsbConnectReceiver, UsbConnectionManager.YubiKeyUsbUnplugReceiver, AdapterView.OnItemSelectedListener {
	private final UsbConnectionManager usbConnectionManager = new UsbConnectionManager(this);
	private YubiKey.Slot selectedSlot = YubiKey.Slot.CHALLENGE_HMAC_2;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(R.layout.activity_challenge_response);

		final Spinner slotSelection = this.findViewById(R.id.slotSelection);
		slotSelection.setSelection(1);
		slotSelection.setOnItemSelectedListener(this);

		this.usbConnectionManager.waitForYubiKey(this);
	}

	@Override
	public void onYubiKeyConnected(final UsbDevice device, final UsbDeviceConnection connection) {
		final YubiKey yubiKey = new YubiKey(device, connection);

		((TextView) this.findViewById(R.id.info)).setText(R.string.press_button);
		this.findViewById(R.id.slotSelection).setVisibility(View.GONE);

		@SuppressLint("StaticFieldLeak") // Leaks can't occur as the task will eventually timeout
		final AsyncTask<Void, Void, byte[]> challengeResponseTask = new AsyncTask<Void, Void, byte[]>() {
			private Exception executionException;

			@Override
			protected byte[] doInBackground(final Void... nothing) {
				try {
					return yubiKey.challengeResponse(ChallengeResponseActivity.this.selectedSlot, ChallengeResponseActivity.this.getIntent().getByteArrayExtra("challenge"));
				} catch (final UsbException e) {
					this.executionException = e;
					return null;
				}
			}

			@Override
			protected void onPostExecute(final byte[] bytes) {
				super.onPostExecute(bytes);

				if (this.executionException == null) {
					final Intent result = new Intent();
					result.putExtra("response", bytes);
					ChallengeResponseActivity.this.setResult(RESULT_OK, result);
					ChallengeResponseActivity.this.finish();
				} else {
					Log.e("YubiDroid", "Error during challenge-response request", this.executionException);

					ChallengeResponseActivity.this.usbConnectionManager.waitForYubiKeyUnplug(ChallengeResponseActivity.this);

					ChallengeResponseActivity.this.findViewById(R.id.waiting).setVisibility(View.GONE);
					ChallengeResponseActivity.this.findViewById(R.id.failure).setVisibility(View.VISIBLE);
					((TextView) ChallengeResponseActivity.this.findViewById(R.id.info)).setText(R.string.unplug_yubikey);
				}
			}
		};

		challengeResponseTask.execute();
	}

	@Override
	public void onYubiKeyUnplugged() {
		this.recreate();
	}

	@Override
	protected void onStop() {
		super.onStop();

		this.usbConnectionManager.stop();
	}

	@Override
	public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
		switch (position) {
			case 0:
				this.selectedSlot = YubiKey.Slot.CHALLENGE_HMAC_1;
				break;
			case 1:
				this.selectedSlot = YubiKey.Slot.CHALLENGE_HMAC_2;
				break;
			default:
				throw new IllegalStateException();
		}
	}

	@Override
	public void onNothingSelected(final AdapterView<?> parent) {
		throw new IllegalStateException();
	}
}

package net.pp3345.ykdroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import net.pp3345.ykdroid.yubikey.Slot;
import net.pp3345.ykdroid.yubikey.UsbYubiKey;

/**
 * May be invoked by Android apps using the
 * <code>"android.yubikey.intent.action.CHALLENGE_RESPONSE"</code> intent to send a challenge
 * to a YubiKey and receive the response.
 * <p>
 * The challenge must be passed in an extra <code>byte[] challenge</code>. Upon successful completion,
 * the activity returns an extra <code>byte[] response</code> in the result intent. Optionally,
 * an extra <code>String purpose</code> may be passed in the intent to identify the purpose of the
 * challenge. ykDroid will use this identifier to remember and pre-select the slot used for each
 * purpose.
 * </p>
 */
public class ChallengeResponseActivity extends Activity implements ConnectionManager.YubiKeyConnectReceiver, ConnectionManager.YubiKeyUsbUnplugReceiver, AdapterView.OnItemSelectedListener {
	private ConnectionManager connectionManager;
	private SlotPreferenceManager slotPreferenceManager;
	private Slot selectedSlot;
	private String purpose;
	private byte[] challenge;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.connectionManager = new ConnectionManager(this);
		this.slotPreferenceManager = new SlotPreferenceManager(this);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(R.layout.activity_challenge_response);

		this.challenge = this.getIntent().getByteArrayExtra("challenge");
		if (this.challenge == null || this.challenge.length == 0) {
			this.showError();
			((TextView) ChallengeResponseActivity.this.findViewById(R.id.info)).setText(R.string.invalid_challenge);
			return;
		}

		switch (this.connectionManager.getSupportedConnectionMethods()) {
			case ConnectionManager.CONNECTION_METHOD_USB | ConnectionManager.CONNECTION_METHOD_NFC:
				((TextView) this.findViewById(R.id.info)).setText(R.string.attach_or_swipe_yubikey);
				break;
			case ConnectionManager.CONNECTION_METHOD_USB:
				((TextView) this.findViewById(R.id.info)).setText(R.string.attach_yubikey);
				break;
			case ConnectionManager.CONNECTION_METHOD_NFC:
				((TextView) this.findViewById(R.id.info)).setText(R.string.swipe_yubikey);
				break;
			default:
				((TextView) this.findViewById(R.id.info)).setText(R.string.no_supported_connection_method);
				this.showError();
				return;
		}

		this.purpose = this.getIntent().getStringExtra("purpose");
		this.selectedSlot = this.slotPreferenceManager.getPreferredSlot(this.purpose, Slot.CHALLENGE_HMAC_1);

		final Spinner slotSelection = this.findViewById(R.id.slotSelection);

		switch (this.selectedSlot) {
			case CHALLENGE_HMAC_1:
				slotSelection.setSelection(0);
				break;
			case CHALLENGE_HMAC_2:
				slotSelection.setSelection(1);
				break;
		}

		slotSelection.setOnItemSelectedListener(this);

		this.connectionManager.waitForYubiKey(this);
	}

	@Override
	public void onYubiKeyConnected(final YubiKey yubiKey) {
		if (yubiKey instanceof UsbYubiKey)
			((TextView) this.findViewById(R.id.info)).setText(R.string.press_button);
		this.findViewById(R.id.slotSelection).setVisibility(View.GONE);

		@SuppressLint("StaticFieldLeak") // Leaks can't occur as the task will eventually timeout
		final AsyncTask<Void, Void, byte[]> challengeResponseTask = new AsyncTask<Void, Void, byte[]>() {
			private Exception executionException;

			@Override
			protected byte[] doInBackground(final Void... nothing) {
				try {
					return yubiKey.challengeResponse(ChallengeResponseActivity.this.selectedSlot, ChallengeResponseActivity.this.challenge);
				} catch (final Exception e) {
					this.executionException = e;
					return null;
				}
			}

			@Override
			protected void onPostExecute(final byte[] bytes) {
				super.onPostExecute(bytes);

				if (this.executionException == null) {
					ChallengeResponseActivity.this.slotPreferenceManager.setPreferredSlot(ChallengeResponseActivity.this.purpose, ChallengeResponseActivity.this.selectedSlot);

					final Intent result = new Intent();
					result.putExtra("response", bytes);
					ChallengeResponseActivity.this.setResult(RESULT_OK, result);
					ChallengeResponseActivity.this.finish();
				} else {
					Log.e("ykDroid", "Error during challenge-response request", this.executionException);

					ChallengeResponseActivity.this.connectionManager.waitForYubiKeyUnplug(ChallengeResponseActivity.this);
					ChallengeResponseActivity.this.showError();

					((TextView) ChallengeResponseActivity.this.findViewById(R.id.info)).setText(R.string.unplug_yubikey);
				}
			}
		};
		this.findViewById(R.id.slotSelection).setVisibility(View.GONE);

		challengeResponseTask.execute();
	}

	@Override
	public void onYubiKeyUnplugged() {
		this.recreate();
	}

	@Override
	public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
		switch (position) {
			case 0:
				this.selectedSlot = Slot.CHALLENGE_HMAC_1;
				break;
			case 1:
				this.selectedSlot = Slot.CHALLENGE_HMAC_2;
				break;
			default:
				throw new IllegalStateException();
		}
	}

	@Override
	public void onNothingSelected(final AdapterView<?> parent) {
		throw new IllegalStateException();
	}

	private void showError() {
		this.findViewById(R.id.waiting).setVisibility(View.GONE);
		this.findViewById(R.id.failure).setVisibility(View.VISIBLE);
		this.findViewById(R.id.slotSelection).setVisibility(View.GONE);
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		// This is kind of ugly but Android doesn't leave us any other choice
		this.connectionManager.onReceive(this, intent);
	}
}

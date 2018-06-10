package net.pp3345.ykdroid;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;

import net.pp3345.ykdroid.yubikey.NfcYubiKey;
import net.pp3345.ykdroid.yubikey.UsbYubiKey;

/**
 * Manages the lifecycle of a YubiKey connection via USB or NFC.
 */
class ConnectionManager extends BroadcastReceiver implements Application.ActivityLifecycleCallbacks {
	private final Activity activity;
	private       boolean  isActivityResumed;

	private static final String ACTION_USB_PERMISSION_REQUEST = "net.pp3345.ykdroid.intent.action.USB_PERMISSION_REQUEST";

	/**
	 * Flag used to indicate that support for USB host mode is present on the Android device.
	 */
	public static final byte CONNECTION_METHOD_USB = 0b1;
	/**
	 * Flag used to indicate that support for NFC is present on the Android device.
	 */
	public static final byte CONNECTION_METHOD_NFC = 0b10;

	private YubiKeyConnectReceiver   connectReceiver;
	private YubiKeyUsbUnplugReceiver unplugReceiver;

	/**
	 * Receiver interface that is called when a YubiKey was connected.
	 */
	interface YubiKeyConnectReceiver {
		/**
		 * Called when a YubiKey was connected via USB or NFC.
		 *
		 * @param yubiKey The YubiKey driver implementation, instantiated with a connection to the
		 *                YubiKey.
		 */
		void onYubiKeyConnected(YubiKey yubiKey);
	}

	/**
	 * Receiver interface that is called when a YubiKey connected via USB was unplugged.
	 */
	interface YubiKeyUsbUnplugReceiver {
		/**
		 * Called when a YubiKey connected via USB was unplugged.
		 */
		void onYubiKeyUnplugged();
	}

	/**
	 * May only be instantiated as soon as the basic initialization of a new activity is complete
	 * (usually in {@link Activity#onStart()}).
	 *
	 * @param activity As the connection lifecycle depends on the activity lifecycle, an active
	 *                 {@link Activity} must be passed
	 */
	ConnectionManager(final Activity activity) {
		this.activity = activity;
		this.activity.getApplication().registerActivityLifecycleCallbacks(this);
	}

	@Override
	public void onActivityCreated(final Activity activity, final Bundle savedInstanceState) {
	}

	/**
	 * Waits for a YubiKey to be connected. Should be called in {@link Activity#onStart()}.
	 *
	 * @param receiver The receiver implementation to be called as soon as a YubiKey was connected.
	 */
	public void waitForYubiKey(final YubiKeyConnectReceiver receiver) {
		this.connectReceiver = receiver;
	}

	@Override
	public void onActivityStarted(final Activity activity) {
		if (this.connectReceiver == null || (this.getSupportedConnectionMethods() & CONNECTION_METHOD_USB) == 0)
			return;

		final UsbManager usbManager = (UsbManager) this.activity.getSystemService(Context.USB_SERVICE);

		this.activity.registerReceiver(this, new IntentFilter(ACTION_USB_PERMISSION_REQUEST));
		this.activity.registerReceiver(this, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));

		assert usbManager != null;
		for (final UsbDevice device : usbManager.getDeviceList().values())
			this.requestPermission(device);
	}

	@Override
	public void onActivityResumed(final Activity activity) {
		if (this.connectReceiver == null || (this.getSupportedConnectionMethods() & CONNECTION_METHOD_NFC) == 0)
			return;

		final IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		filter.addDataScheme(NfcYubiKey.YUBIKEY_NEO_NDEF_SCHEME);
		filter.addDataAuthority(NfcYubiKey.YUBIKEY_NEO_NDEF_HOST, null);

		NfcAdapter.getDefaultAdapter(this.activity).enableForegroundDispatch(this.activity,
		                                                                     PendingIntent.getActivity(this.activity, -1, new Intent(this.activity, this.activity.getClass()), 0),
		                                                                     new IntentFilter[]{filter},
		                                                                     null);
		this.isActivityResumed = true;
	}

	/**
	 * Waits until no YubiKey is connected.
	 *
	 * @param receiver The receiver implementation to be called as soon as no YubiKey is connected
	 *                 anymore.
	 */
	public void waitForYubiKeyUnplug(final YubiKeyUsbUnplugReceiver receiver) {
		if ((this.getSupportedConnectionMethods() & CONNECTION_METHOD_USB) == 0) {
			receiver.onYubiKeyUnplugged();
			return;
		}

		if (!this.isYubiKeyPlugged()) {
			receiver.onYubiKeyUnplugged();
			return;
		}

		this.unplugReceiver = receiver;
		this.activity.registerReceiver(this, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
	}

	private boolean isYubiKeyPlugged() {
		final UsbManager usbManager = (UsbManager) this.activity.getSystemService(Context.USB_SERVICE);

		assert usbManager != null;

		for (final UsbDevice device : usbManager.getDeviceList().values()) {
			if (device.getVendorId() == UsbYubiKey.YUBICO_USB_VENDOR_ID)
				return true;
		}

		return false;
	}

	@Override
	public void onReceive(final Context context, final Intent intent) {
		assert intent.getAction() != null;

		switch (intent.getAction()) {
			case ACTION_USB_PERMISSION_REQUEST:
				if(!this.isYubiKeyPlugged()) // Do not keep asking for permission to access a YubiKey that was unplugged already
					break;
			case UsbManager.ACTION_USB_DEVICE_ATTACHED:
				this.requestPermission((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
				break;
			case UsbManager.ACTION_USB_DEVICE_DETACHED:
				if (((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)).getVendorId() == UsbYubiKey.YUBICO_USB_VENDOR_ID) {
					this.activity.unregisterReceiver(this);
					this.unplugReceiver.onYubiKeyUnplugged();
					this.unplugReceiver = null;
				}
				break;
			case NfcAdapter.ACTION_NDEF_DISCOVERED:
				final IsoDep isoDep = IsoDep.get((Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG));

				if (isoDep == null) {
					// Not a YubiKey
					return;
				}

				if ((this.getSupportedConnectionMethods() & CONNECTION_METHOD_USB) != 0)
					this.activity.unregisterReceiver(this);

				this.connectReceiver.onYubiKeyConnected(new NfcYubiKey(isoDep));
				this.connectReceiver = null;
				break;
		}
	}

	@Override
	public void onActivityPaused(final Activity activity) {
		if (this.connectReceiver != null && (this.getSupportedConnectionMethods() & CONNECTION_METHOD_NFC) != 0)
			NfcAdapter.getDefaultAdapter(this.activity).disableForegroundDispatch(this.activity);

		this.isActivityResumed = false;
	}

	@Override
	public void onActivityStopped(final Activity activity) {
		if (this.connectReceiver != null || this.unplugReceiver != null)
			this.activity.unregisterReceiver(this);
	}

	private void requestPermission(final UsbDevice device) {
		final UsbManager usbManager = (UsbManager) this.activity.getSystemService(Context.USB_SERVICE);

		if (device.getVendorId() != UsbYubiKey.YUBICO_USB_VENDOR_ID)
			return;

		assert usbManager != null;
		if (usbManager.hasPermission(device)) {
			this.activity.unregisterReceiver(this);

			if ((this.getSupportedConnectionMethods() & CONNECTION_METHOD_NFC) != 0 && this.isActivityResumed)
				NfcAdapter.getDefaultAdapter(this.activity).disableForegroundDispatch(this.activity);

			this.connectReceiver.onYubiKeyConnected(new UsbYubiKey(device, usbManager.openDevice(device)));
			this.connectReceiver = null;
		} else {
			usbManager.requestPermission(device, PendingIntent.getBroadcast(this.activity, 0, new Intent(ACTION_USB_PERMISSION_REQUEST), 0));
		}
	}

	/**
	 * Gets the connection methods (USB and/or NFC) that are supported on the Android device.
	 *
	 * @return A byte that may or may not have the {@link #CONNECTION_METHOD_USB} and
	 * {@link #CONNECTION_METHOD_USB} bits set.
	 */
	public byte getSupportedConnectionMethods() {
		final PackageManager packageManager = this.activity.getPackageManager();
		byte                 result         = 0b0;

		if (packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST))
			result |= CONNECTION_METHOD_USB;

		if (packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) && NfcAdapter.getDefaultAdapter(this.activity).isEnabled())
			result |= CONNECTION_METHOD_NFC;

		return result;
	}

	@Override
	public void onActivitySaveInstanceState(final Activity activity, final Bundle outState) {
	}

	@Override
	public void onActivityDestroyed(final Activity activity) {
		this.activity.getApplication().unregisterActivityLifecycleCallbacks(this);
	}
}

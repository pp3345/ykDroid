package net.pp3345.yubidroid;

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

import net.pp3345.yubidroid.yubikey.NfcYubiKey;
import net.pp3345.yubidroid.yubikey.UsbYubiKey;

class ConnectionManager extends BroadcastReceiver implements Application.ActivityLifecycleCallbacks {
	private final Activity activity;

	private static final String ACTION_USB_PERMISSION_REQUEST = "net.pp3345.yubidroid.intent.action.USB_PERMISSION_REQUEST";

	public static final byte CONNECTION_METHOD_USB = 0b1;
	public static final byte CONNECTION_METHOD_NFC = 0b10;

	private YubiKeyConnectReceiver   connectReceiver;
	private YubiKeyUsbUnplugReceiver unplugReceiver;

	interface YubiKeyConnectReceiver {
		void onYubiKeyConnected(YubiKey yubiKey);
	}

	interface YubiKeyUsbUnplugReceiver {
		void onYubiKeyUnplugged();
	}

	ConnectionManager(final Activity activity) {
		this.activity = activity;
		this.activity.getApplication().registerActivityLifecycleCallbacks(this);
	}

	@Override
	public void onActivityCreated(final Activity activity, final Bundle savedInstanceState) {
	}

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
	}

	public void waitForYubiKeyUnplug(final YubiKeyUsbUnplugReceiver receiver) {
		if ((this.getSupportedConnectionMethods() & CONNECTION_METHOD_USB) == 0) {
			receiver.onYubiKeyUnplugged();
			return;
		}

		final UsbManager usbManager = (UsbManager) this.activity.getSystemService(Context.USB_SERVICE);

		assert usbManager != null;

		HaveKeyPlugged:
		//noinspection LoopStatementThatDoesntLoop
		do {
			for (final UsbDevice device : usbManager.getDeviceList().values()) {
				if (device.getVendorId() == UsbYubiKey.YUBICO_USB_VENDOR_ID)
					break HaveKeyPlugged;
			}

			// No YubiKey is currently connected
			receiver.onYubiKeyUnplugged();
			return;
		} while (false);

		this.unplugReceiver = receiver;
		this.activity.registerReceiver(this, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
	}

	@Override
	public void onReceive(final Context context, final Intent intent) {
		assert intent.getAction() != null;

		switch (intent.getAction()) {
			case ACTION_USB_PERMISSION_REQUEST:
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

			if ((this.getSupportedConnectionMethods() & CONNECTION_METHOD_NFC) != 0)
				NfcAdapter.getDefaultAdapter(this.activity).disableForegroundDispatch(this.activity);

			this.connectReceiver.onYubiKeyConnected(new UsbYubiKey(device, usbManager.openDevice(device)));
			this.connectReceiver = null;
		} else {
			usbManager.requestPermission(device, PendingIntent.getBroadcast(this.activity, 0, new Intent(ACTION_USB_PERMISSION_REQUEST), 0));
		}
	}

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

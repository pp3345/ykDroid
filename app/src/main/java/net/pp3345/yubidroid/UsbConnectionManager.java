package net.pp3345.yubidroid;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

class UsbConnectionManager extends BroadcastReceiver {
	private final Context context;

	private static final String ACTION_USB_PERMISSION_REQUEST = "net.pp3345.yubidroid.intent.action.USB_PERMISSION_REQUEST";
	private YubiKeyUsbConnectReceiver connectReceiver;
	private YubiKeyUsbUnplugReceiver  unplugReceiver;

	interface YubiKeyUsbConnectReceiver {
		void onYubiKeyConnected(UsbDevice device, UsbDeviceConnection connection);
	}

	interface YubiKeyUsbUnplugReceiver {
		void onYubiKeyUnplugged();
	}

	UsbConnectionManager(final Context context) {
		this.context = context;
	}

	public void waitForYubiKey(final YubiKeyUsbConnectReceiver receiver) {
		final UsbManager usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);

		this.connectReceiver = receiver;
		this.context.registerReceiver(this, new IntentFilter(ACTION_USB_PERMISSION_REQUEST));
		this.context.registerReceiver(this, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));

		assert usbManager != null;
		for (final UsbDevice device : usbManager.getDeviceList().values())
			this.requestPermission(device);
	}

	public void waitForYubiKeyUnplug(final YubiKeyUsbUnplugReceiver receiver) {
		final UsbManager usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);

		assert usbManager != null;

		HaveKeyPlugged:
		//noinspection LoopStatementThatDoesntLoop
		do {
			for (final UsbDevice device : usbManager.getDeviceList().values()) {
				if (device.getVendorId() == YubiKey.YUBICO_USB_VENDOR_ID)
					break HaveKeyPlugged;
			}

			// No YubiKey is currently connected
			receiver.onYubiKeyUnplugged();
			return;
		} while (false);

		this.unplugReceiver = receiver;
		this.context.registerReceiver(this, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
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
				if (((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)).getVendorId() == YubiKey.YUBICO_USB_VENDOR_ID) {
					this.context.unregisterReceiver(this);
					this.unplugReceiver.onYubiKeyUnplugged();
					this.unplugReceiver = null;
				}
				break;
		}
	}

	public void stop() {
		if (this.connectReceiver != null || this.unplugReceiver != null)
			this.context.unregisterReceiver(this);
	}

	private void requestPermission(final UsbDevice device) {
		final UsbManager usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);

		if (device.getVendorId() != YubiKey.YUBICO_USB_VENDOR_ID)
			return;

		assert usbManager != null;
		if (usbManager.hasPermission(device)) {
			this.context.unregisterReceiver(this);

			this.connectReceiver.onYubiKeyConnected(device, usbManager.openDevice(device));
			this.connectReceiver = null;
		} else {
			usbManager.requestPermission(device, PendingIntent.getBroadcast(this.context, 0, new Intent(ACTION_USB_PERMISSION_REQUEST), 0));
		}
	}
}

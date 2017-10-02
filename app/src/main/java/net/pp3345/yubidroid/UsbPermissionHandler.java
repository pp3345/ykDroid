package net.pp3345.yubidroid;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

class UsbPermissionHandler extends BroadcastReceiver {
	private final Context context;

	private static final String ACTION_USB_PERMISSION_REQUEST = "net.pp3345.yubidroid.intent.action.USB_PERMISSION_REQUEST";
	private YubiKeyUsbReceiver receiver;

	interface YubiKeyUsbReceiver {
		void onYubiKeyConnected(UsbDevice device, UsbDeviceConnection connection);
	}

	UsbPermissionHandler(final Context context) {
		this.context = context;
	}

	public void waitForYubiKey(final YubiKeyUsbReceiver receiver) {
		final UsbManager usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);

		this.receiver = receiver;
		this.context.registerReceiver(this, new IntentFilter(ACTION_USB_PERMISSION_REQUEST));

		assert usbManager != null;
		for (final UsbDevice device : usbManager.getDeviceList().values()) {
			if (device.getVendorId() == YubiKey.YUBICO_USB_VENDOR_ID) {
				usbManager.requestPermission(device, PendingIntent.getBroadcast(this.context, 0, new Intent(ACTION_USB_PERMISSION_REQUEST), 0));
			}
		}
	}

	@Override
	public void onReceive(final Context context, final Intent intent) {
		final UsbManager usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
		final UsbDevice  device     = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

		assert usbManager != null;
		if (usbManager.hasPermission(device)) {
			this.context.unregisterReceiver(this);

			this.receiver.onYubiKeyConnected(device, usbManager.openDevice(device));
		} else {
			usbManager.requestPermission(device, PendingIntent.getBroadcast(this.context, 0, new Intent(ACTION_USB_PERMISSION_REQUEST), 0));
		}
	}
}

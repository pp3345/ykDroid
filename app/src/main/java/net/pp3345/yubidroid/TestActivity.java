package net.pp3345.yubidroid;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class TestActivity extends Activity {
	private TextView output;
	private UsbManager usbManager;
	private BroadcastReceiver usbPermissionRequestReceiver;
	private YubiKey yubiKey;

	private static final String ACTION_USB_PERMISSION_REQUEST = "net.pp3345.yubidroid.USB_PERMISSION_REQUEST";

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.activity_test);

		this.output = this.findViewById(R.id.debug_output);

		this.usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
		this.usbPermissionRequestReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, final Intent intent) {
				TestActivity.this.requestUSBPermission((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
			}
		};
		this.registerReceiver(this.usbPermissionRequestReceiver, new IntentFilter(ACTION_USB_PERMISSION_REQUEST));

		for (final UsbDevice device : this.usbManager.getDeviceList().values()) {
			if(device.getVendorId() == YubiKey.YUBICO_USB_VENDOR_ID) {
				this.requestUSBPermission(device);
			}
		}
	}

	private void requestUSBPermission(final UsbDevice device) {
		if (!this.usbManager.hasPermission(device)) {
			this.output.append("Requesting permission for USB device: ");
			this.output.append(" manufacturer=" + device.getManufacturerName());
			this.output.append(" deviceName=" + device.getDeviceName());
			this.output.append(" productName=" + device.getProductName());
			this.output.append(" vendorID=" + device.getVendorId());
			this.output.append("\n");

			this.usbManager.requestPermission(device, PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION_REQUEST), 0));
		} else {
			this.unregisterReceiver(this.usbPermissionRequestReceiver);
			this.output.append("Opening connection...\n");
			this.yubiKey = new YubiKey(device, this.usbManager.openDevice(device));
			this.output.append("Connected to " + this.yubiKey.getType().getName() + " (" + this.yubiKey.getType().getVersion() + ")\n");
			try {
				this.output.append("Serial number: " + this.yubiKey.getSerialNumber());
			} catch (final UsbException e) {
				this.output.append("UsbException! :( " + e.getMessage());
				Log.e("YubiDroid", "UsbException", e);
			}
		}
	}
}

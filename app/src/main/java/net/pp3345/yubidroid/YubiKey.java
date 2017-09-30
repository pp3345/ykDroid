package net.pp3345.yubidroid;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

public class YubiKey {
	private final UsbDeviceConnection connection;
	private final UsbDevice           device;

	@SuppressWarnings("WeakerAccess")
	public static final  int  YUBICO_USB_VENDOR_ID                = 0x1050;
	private static final int  YUBIKEY_OPERATION_TIMEOUT_MS        = 2000;
	private static final int  YUBIKEY_USER_INTERACTION_TIMEOUT_MS = 256000;
	private static final char YUBIKEY_CRC_16_OK_RESIDUAL          = 0xf0b8;

	private static final int HID_GET_REPORT                = 0x1;
	private static final int HID_SET_REPORT                = 0x9;
	private static final int REPORT_TYPE_FEATURE           = 0b11 << 8;
	private static final int REPORT_TYPE_FEATURE_DATA_SIZE = 8;

	private static final short STATUS_FLAG_WAITING          = 0x20;
	private static final short STATUS_FLAG_RESPONSE_PENDING = 0x40;
	private static final short STATUS_FLAG_WRITE            = 0x80;

	private static final byte WRITE_PAYLOAD_LENGTH = 64;
	private static final int CHALLENGE_RESPONSE_LENGTH = 20;

	public enum Type {
		STANDARD(0x0010, "YubiKey", "Version 1 or 2"),

		NEO_OTP(0x0110, "YubiKey NEO", "OTP only"),
		NEO_OTP_CCID(0x0111, "YubiKey NEO", "OTP and CCID"),
		NEO_CCID(0x0112, "YubiKey NEO", "CCID only"),
		NEO_U2F(0x0113, "YubiKey NEO", "U2F only"),
		NEO_OTP_U2F(0x0114, "YubiKey NEO", "OTP and U2F"),
		NEO_U2F_CCID(0x0115, "YubiKey NEO", "U2F and CCID"),
		NEO_OTP_U2F_CCID(0x0116, "YubiKey NEO", "OTP, U2F and CCID"),

		YK4_OTP(0x0401, "YubiKey 4", "OTP only"),
		YK4_U2F(0x0402, "YubiKey 4", "U2F only"),
		YK4_OTP_U2F(0x0403, "YubiKey 4", "OTP and U2F"),
		YK4_CCID(0x0404, "YubiKey 4", "CCID only"),
		YK4_OTP_CCID(0x0405, "YubiKey 4", "OTP and CCID"),
		YK4_U2F_CCID(0x0406, "YubiKey 4", "U2F and CCID"),
		YK4_OTP_U2F_CCID(0x0407, "YubiKey 4", "OTP, U2F and CCID"),

		PLUS_U2F_OTP(0x0410, "YubiKey Plus", "OTP and U2F"),

		UNKNOWN(-0x1, "Unknown YubiKey", "");

		private final int    productID;
		private final String name;
		private final String version;

		Type(final int productID, final String name, final String version) {
			this.productID = productID;
			this.name = name;
			this.version = version;
		}

		public int getProductID() {
			return this.productID;
		}

		public String getName() {
			return this.name;
		}

		public String getVersion() {
			return this.version;
		}
	}

	private enum StatusMode {
		SET,
		CLEAR;
	}

	public enum Slot {
		CONFIG_1((byte) 0x1),
		NAV((byte) 0x2),
		CONFIG_2((byte) 0x3),
		UPDATE_1((byte) 0x4),
		UPDATE_2((byte) 0x5),
		SWAP((byte) 0x6),
		NDEF_1((byte) 0x8),
		NDEF_2((byte) 0x9),
		DEVICE_SERIAL((byte) 0x10),
		DEVICE_CONFIGURATION((byte) 0x11),
		SCAN_MAP((byte) 0x12),
		YUBIKEY_4_CAPABILITIES((byte) 0x13),
		CHALLENGE_OTP_1((byte) 0x20),
		CHALLENGE_OTP_2((byte) 0x28),
		CHALLENGE_HMAC_1((byte) 0x30),
		CHALLENGE_HMAC_2((byte) 0x38);

		private final byte address;

		Slot(final byte address) {
			this.address = address;
		}

		public byte getAddress() {
			return this.address;
		}

		public boolean isChallengeResponseSlot() {
			return this == CHALLENGE_HMAC_1 || this == CHALLENGE_HMAC_2;
		}
	}

	public YubiKey(final UsbDevice device, final UsbDeviceConnection connection) {
		this.device = device;
		this.connection = connection;
	}

	public Type getType() {
		for (final Type type : Type.values()) {
			if (type.getProductID() == this.device.getProductId()) {
				return type;
			}
		}

		return Type.UNKNOWN;
	}

	public int getSerialNumber() throws UsbException {
		this.write(Slot.DEVICE_SERIAL, new byte[REPORT_TYPE_FEATURE_DATA_SIZE * 2]);

		final byte[] response = this.readResponse(4, true);

		return (response[0] << 24) + (response[1] << 16) + (response[2] << 8) + (response[3] & 0xff);
	}

	public byte[] challengeResponse(final Slot slot, final byte[] challenge) throws UsbException {
		if(!slot.isChallengeResponseSlot())
			throw new InvalidSlotException();

		this.write(slot, challenge);

		return this.readResponse(CHALLENGE_RESPONSE_LENGTH, true);
	}

	private char CRC16(final byte[] buffer, final int bytes) {
		char crc = 0xffff;

		for (int x = 0; x < bytes; x++) {
			crc ^= buffer[x] & 0xff;

			for (int i = 0; i < 8; i++) {
				final boolean j = (crc & 0b1) == 1;
				crc >>= 1;

				if (j) {
					crc ^= 0x8408;
				}
			}
		}

		return crc;
	}

	private void verifyCRC16(final byte[] buffer, final int bytes) throws CRC16Exception {
		if (this.CRC16(buffer, bytes) != YUBIKEY_CRC_16_OK_RESIDUAL)
			throw new CRC16Exception();
	}

	private boolean reset() throws UsbException {
		// TODO: stub
		return true;
	}

	private void tryClaim() throws UsbException {
		if (!this.connection.claimInterface(this.device.getInterface(0), true)) // We need to detach the kernel driver from the device to get exclusive access
			throw new UsbException("Failed to claim interface");
	}

	private byte[] waitForStatus(final boolean mayBlock, final short mask, final StatusMode mode) throws UsbException {
		int          waitInterval              = 1;
		boolean      waitingForUserInteraction = false;
		final byte[] data                      = new byte[REPORT_TYPE_FEATURE_DATA_SIZE];

		for (int waitTime = 0; waitTime <= (waitingForUserInteraction ? YUBIKEY_USER_INTERACTION_TIMEOUT_MS : YUBIKEY_OPERATION_TIMEOUT_MS); waitTime += waitInterval) {
			try {
				Thread.sleep(waitInterval);
			} catch (final InterruptedException ignored) {
			}

			waitInterval *= 2;

			this.tryClaim();
			try {
				final int bytes = this.connection.controlTransfer(UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_DIR_IN | 0x1, HID_GET_REPORT, REPORT_TYPE_FEATURE, 0, data, REPORT_TYPE_FEATURE_DATA_SIZE, YUBIKEY_OPERATION_TIMEOUT_MS);

				if (bytes != REPORT_TYPE_FEATURE_DATA_SIZE)
					throw new UsbException("controlTransfer failed: " + bytes);
			} finally {
				this.connection.releaseInterface(this.device.getInterface(0));
			}

			switch (mode) {
				case SET:
					if ((data[REPORT_TYPE_FEATURE_DATA_SIZE - 1] & mask) == mask) {
						return data;
					}

					break;
				case CLEAR:
					if ((data[REPORT_TYPE_FEATURE_DATA_SIZE - 1] & mask) == 0) {
						return data;
					}

					break;
			}

			if ((data[REPORT_TYPE_FEATURE_DATA_SIZE - 1] & STATUS_FLAG_WAITING) == STATUS_FLAG_WAITING) {
				if (mayBlock) {
					waitingForUserInteraction = true;
				} else {
					this.reset();
					throw new BlockingOperationException();
				}
			} else if (waitingForUserInteraction) {
				// User interaction timed out
				throw new TimeoutException();
			}
		}

		this.reset();
		throw new TimeoutException();
	}

	private byte[] readResponse(final int expectedBytes, final boolean mayBlock) throws UsbException {
		final byte[] response  = new byte[Math.max(REPORT_TYPE_FEATURE_DATA_SIZE * (expectedBytes / REPORT_TYPE_FEATURE_DATA_SIZE + 1), REPORT_TYPE_FEATURE_DATA_SIZE * 8)];
		int          bytesRead = REPORT_TYPE_FEATURE_DATA_SIZE - 1;

		System.arraycopy(this.waitForStatus(mayBlock, STATUS_FLAG_RESPONSE_PENDING, StatusMode.SET), 0, response, 0, REPORT_TYPE_FEATURE_DATA_SIZE - 1);

		while (bytesRead + REPORT_TYPE_FEATURE_DATA_SIZE <= response.length || expectedBytes == 0) {
			final byte[] data = new byte[REPORT_TYPE_FEATURE_DATA_SIZE];

			this.tryClaim();
			try {
				final int bytes = this.connection.controlTransfer(UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_DIR_IN | 0x1, HID_GET_REPORT, REPORT_TYPE_FEATURE, 0, data, REPORT_TYPE_FEATURE_DATA_SIZE, YUBIKEY_OPERATION_TIMEOUT_MS);

				if (bytes != REPORT_TYPE_FEATURE_DATA_SIZE)
					throw new UsbException("controlTransfer failed: " + bytes);
			} finally {
				this.connection.releaseInterface(this.device.getInterface(0));
			}

			if ((data[REPORT_TYPE_FEATURE_DATA_SIZE - 1] & STATUS_FLAG_RESPONSE_PENDING) == STATUS_FLAG_RESPONSE_PENDING) {
				if ((data[REPORT_TYPE_FEATURE_DATA_SIZE - 1] & 0b11111) == 0) {
					if (expectedBytes > 0) {
						this.verifyCRC16(response, expectedBytes + 2);
					}

					this.reset();

					if (response.length > expectedBytes) {
						final byte[] result = new byte[expectedBytes];
						System.arraycopy(response, 0, result, 0, expectedBytes);

						return result;
					}

					return response;
				}

				System.arraycopy(data, 0, response, bytesRead, REPORT_TYPE_FEATURE_DATA_SIZE - 1);
				bytesRead += REPORT_TYPE_FEATURE_DATA_SIZE - 1;
			} else {
				this.reset();

				throw new InvalidResponseException();
			}
		}

		this.reset();
		throw new InvalidResponseException();
	}

	private void write(final Slot slot, final byte[] data) throws UsbException {
		final byte[] payload = new byte[WRITE_PAYLOAD_LENGTH];
		final byte[] frame   = new byte[WRITE_PAYLOAD_LENGTH + 6];

		System.arraycopy(data, 0, payload, 0, data.length);
		System.arraycopy(payload, 0, frame, 0, WRITE_PAYLOAD_LENGTH);

		final char crc = this.CRC16(payload, payload.length);
		frame[WRITE_PAYLOAD_LENGTH] = slot.getAddress();
		frame[WRITE_PAYLOAD_LENGTH + 1] = (byte) (crc & 0xff);
		frame[WRITE_PAYLOAD_LENGTH + 2] = (byte) (crc >> 8);

		int offset = 0;

		for (int sequence = 0; offset != frame.length; sequence++) {
			final byte[] sequenceData = new byte[REPORT_TYPE_FEATURE_DATA_SIZE];

			System.arraycopy(frame, offset, sequenceData, 0, REPORT_TYPE_FEATURE_DATA_SIZE - 1);
			offset += REPORT_TYPE_FEATURE_DATA_SIZE - 1;

			boolean hasData = false;

			for (int i = 0; i < REPORT_TYPE_FEATURE_DATA_SIZE - 1; i++) {
				if (sequenceData[i] != 0) {
					hasData = true;
					break;
				}
			}

			if (!hasData && sequence != 0 && offset != frame.length)
				continue;

			sequenceData[REPORT_TYPE_FEATURE_DATA_SIZE - 1] = (byte) (sequence | STATUS_FLAG_WRITE);

			this.waitForStatus(false, STATUS_FLAG_WRITE, StatusMode.CLEAR);

			this.tryClaim();
			try {
				//noinspection PointlessBitwiseExpression
				final int bytes = this.connection.controlTransfer(UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_DIR_OUT | 0x1, HID_SET_REPORT, REPORT_TYPE_FEATURE, 0, sequenceData, REPORT_TYPE_FEATURE_DATA_SIZE, YUBIKEY_OPERATION_TIMEOUT_MS);

				if (bytes != REPORT_TYPE_FEATURE_DATA_SIZE)
					throw new UsbException("controlTransfer failed: " + bytes);
			} finally {
				this.connection.releaseInterface(this.device.getInterface(0));
			}
		}
	}
}

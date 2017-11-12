package net.pp3345.yubidroid.apdu.command.iso;

import net.pp3345.yubidroid.apdu.CommandApdu;

/**
 * ISO 7816-4 6.11.3
 */
public class SelectFileApdu extends CommandApdu {
	public enum SelectionControl {
		FILE_IDENTIFIER_MF_DF_OR_EF((byte) 0b0000),
		FILE_IDENTIFIER_CHILD_DF((byte) 0b0001),
		FILE_IDENTIFIER_EF_UNDER_CURRENT_DF((byte) 0b0010),
		FILE_IDENTIFIER_PARENT_DF_OF_CURRENT_DF((byte) 0b0011),
		DF_NAME_DIRECT((byte) 0b0100),
		PATH_FROM_MF((byte) 0b1000),
		PATH_FROM_CURRENT_DF((byte) 0b1001);

		private final byte p1;

		SelectionControl(final byte p1) {
			this.p1 = p1;
		}

		public byte getP1() {
			return this.p1;
		}
	}

	public enum RecordOffset {
		FIRST_RECORD((byte) 0b00),
		LAST_RECORD((byte) 0b01),
		NEXT_RECORD((byte) 0b10),
		PREVIOUS_RECORD((byte) 0b11);

		private final byte p2;

		RecordOffset(final byte p2) {
			this.p2 = p2;
		}

		public byte getP2() {
			return this.p2;
		}
	}

	private final SelectionControl p1;
	private final RecordOffset     p2;

	public SelectFileApdu(final SelectionControl p1, final RecordOffset p2, final byte[] identifier) {
		this.p1 = p1;
		this.p2 = p2;
		this.data = identifier;
	}

	@Override
	protected byte getCommandClass() {
		return (byte) 0x00;
	}

	@Override
	protected byte getInstruction() {
		return (byte) 0xa4;
	}

	@Override
	protected byte[] getParameters() {
		return new byte[]{this.p1.getP1(), this.p2.getP2()};
	}

	@Override
	protected byte getExpectedLength() {
		return (byte) 0x00;
	}
}

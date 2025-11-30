package dev.fileeditor.votl.objects;

public enum ExitCodes {
	NORMAL(0),
	ERROR(1),
	RESTART(10),
	UPDATE(11);

	public final int v;

	ExitCodes(int i) {
		this.v = i;
	}

	public static ExitCodes fromInt(int i) {
		return switch (i) {
			case 1 -> ERROR;
			case 10 -> RESTART;
			case 11 -> UPDATE;
			default -> NORMAL;
		};
	}
}

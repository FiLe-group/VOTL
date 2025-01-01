package dev.fileeditor.votl.utils;

public class MiscUtil {
	
	public static int countChar(String str, char v) {
		if (str.isEmpty()) return 0;
		
		int count = 0;
		for (char c : str.toCharArray()) {
			if (c == v) count++;
		}
		return count;
	}
	
}

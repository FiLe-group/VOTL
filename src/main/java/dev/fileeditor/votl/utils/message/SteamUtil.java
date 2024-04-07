package dev.fileeditor.votl.utils.message;

public class SteamUtil {

	public SteamUtil() {}

	public String convertSteam64toSteamID(String steam64) {
		Long steam64id = Long.valueOf(steam64);

		var universe = (steam64id >> 56) & 0xFF;
		if (universe == 1) universe = 0L;

		var accountIdLowBit = steam64id & 1;

		var accountIdHighBits = (steam64id >> 1) & 0x7FFFFFF;

		var steamId = "STEAM_" + universe + ":" + accountIdLowBit + ":" + accountIdHighBits;

		return String.valueOf(steamId);
	}

	public String convertSteamIDtoSteam64(String steamId) {
		Long steam64id = 76561197960265729L;
		String[] id_split = steamId.split(":");

    	steam64id += Long.parseLong(id_split[2]) * 2;
    	if (id_split[1] == "1") steam64id += 1;
    	
		return String.valueOf(steam64id);
	}
	
}

# VOTL
 [![Build status](https://github.com/FileEditor97/VOTL/actions/workflows/build.yml/badge.svg)](https://github.com/FileEditor97/VOTL/actions/workflows/build.yml)  
 Voice of the Lord - discord bot written in Java using JDA library.  
 Functions: server moderation and sync blacklists, custom voice channels and verification, ticketing.  

Visit https://votl.fileeditor.dev/ to learn more about available commands and to view documentation.

## Download or build
 Stable JAR file can be downloaded from latest Release [here](https://github.com/FileEditor97/VOTL/releases/latest).  
 Additional Snapshot builds can be accessed [here](https://github.com/FileEditor97/VOTL/actions/workflows/build.yml).
 
 Build using from source `.\gradlew shadowJar`.

## Config file
 data/config.json:
 ```json
 {
	"bot-token": "",
	"owner-id": "owner's ID",
	"dev-servers": [
		"dev server's IDs"
	],
	"webhook": "link to webhook, if you want to receive ERROR level logs"
 }
 ```

## Inspiration/Credits
 Thanks to Chew (JDA-Chewtils and Chewbotcca bot) and jagrosh (JDA-Utilities)  
 [PurrBot](https://github.com/purrbot-site/PurrBot) by Andre_601 (purrbot.site)  
 [AvaIre](https://github.com/avaire/avaire) by Senither  
 Ryzeon & Inkception for [Discord (JDA) HTML Transcripts](https://github.com/Ryzeon/discord-html-transcripts)
package dev.fileeditor.votl.scheduler.jobs;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Job;

public class RemoveEmptyVoiceChannels extends Job {

	public RemoveEmptyVoiceChannels(App bot) {
		super(bot, 2, 2);
	}

	@Override
	public void run() {
		bot.getDBUtil().voice.checkCache(bot.JDA);
	}

}

package dev.fileeditor.votl.scheduler.jobs;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Job;

public class ProcessVoiceExperience extends Job {

	public ProcessVoiceExperience(App bot) {
		super(bot, 2, 2);
	}

	@Override
	public void run() {
		bot.getLevelUtil().processVoiceCache();
	}

}

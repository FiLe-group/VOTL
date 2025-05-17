package dev.fileeditor.votl.scheduler.jobs;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.contracts.scheduler.Job;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class ProcessVoiceExperience extends Job {

	public ProcessVoiceExperience(App bot) {
		super(bot, 2, 2, TimeUnit.MINUTES);
	}

	@Override
	public void run() {
		bot.getLevelUtil().processVoiceCache();
	}

}

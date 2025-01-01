package dev.fileeditor.votl.listeners;

import dev.fileeditor.votl.metrics.Metrics;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class EventListener extends ListenerAdapter {

	@Override
	public void onGenericEvent(GenericEvent event) {
		Metrics.jdaEvents.labelValue(event.getClass().getSimpleName()).inc();
	}

}

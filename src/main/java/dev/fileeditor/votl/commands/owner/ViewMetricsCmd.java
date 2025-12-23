package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.metrics.Metrics;
import dev.fileeditor.votl.metrics.timeseries.PingData;
import dev.fileeditor.votl.objects.CmdAccessLevel;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ViewMetricsCmd extends SlashCommand {

	public ViewMetricsCmd() {
		this.name = "metrics";
		this.path = "bot.owner.metrics";
		this.children = new SlashCommand[]{
			new CommandInfo(), new CommandStats(), new ButtonStats(), new Ping()
		};
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.guildOnly = false;
		this.ephemeral = true;
	}

	@Override
	public void execute(SlashCommandEvent event) {}

	private class CommandInfo extends SlashCommand {
		public CommandInfo() {
			this.name = "command";
			this.path = "bot.owner.metrics.command";
			this.options = List.of(
				new OptionData(OptionType.STRING, "command-name", lu.getText(path+".command-name.help"), true)
			);
		}

		@Override
		public void execute(SlashCommandEvent event) {
			String commandName = event.optString("command-name");

			long received = Metrics.commandsReceived.labelValue(commandName).get();
			if (received == 0) {
				editError(event, path+".not_found");
				return;
			}
			long executed = Metrics.commandsExecuted.labelValue(commandName).get();
			long exceptions = Metrics.commandExceptions.labelValue(commandName).get();
			// Execution time in seconds
			double percentile95 = Metrics.executionTime.labelValue(commandName).getPercentile(95) * 1000;
			double percentile90 = Metrics.executionTime.labelValue(commandName).getPercentile(80) * 1000;
			double average = Metrics.executionTime.labelValue(commandName).getAverage() * 1000;

			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle("Full name: "+commandName)
				.addField("Count", "Executed: `%s`/`%s`\nException caught: `%s`".formatted(executed, received, exceptions), false)
				.addField("Execution time", "Average: `%.2f` ms\n95%%: `%.2f` ms | 90%%: `%.2f` ms".formatted(average, percentile95, percentile90), false)
				.build();

			editEmbed(event, embed);
		}
	}

	private class CommandStats extends SlashCommand {
		public CommandStats() {
			this.name = "command-stats";
			this.path = "bot.owner.metrics.command-stats";
		}

		@Override
		public void execute(SlashCommandEvent event) {
			StringBuilder builder = new StringBuilder("```\n");
			Metrics.commandsReceived.collect()
				.entrySet()
				.stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.forEach(e -> builder.append("%4d | %s\n".formatted(e.getValue(), e.getKey())));

			if (builder.length() < 6) {
				event.getHook().editOriginal(Constants.FAILURE+" Empty").queue();
			} else {
				builder.append("```");
				if (builder.length() > 2048) builder.setLength(2048);
				editMsg(event, builder.toString());
			}
		}
	}

	private class ButtonStats extends SlashCommand {
		public ButtonStats() {
			this.name = "button-stats";
			this.path = "bot.owner.metrics.button-stats";
		}

		@Override
		public void execute(SlashCommandEvent event) {
			StringBuilder builder = new StringBuilder("```\n");
			Metrics.interactionReceived.collect()
				.entrySet()
				.stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.forEach(e -> builder.append("%4d | %s\n".formatted(e.getValue(), e.getKey())));

			if (builder.length() < 6) {
				event.getHook().editOriginal(Constants.FAILURE+" Empty").queue();
			} else {
				builder.append("```");
				if (builder.length() > 2048) builder.setLength(2048);
				editMsg(event, builder.toString());
			}
		}
	}

	private class Ping extends SlashCommand {
		public Ping() {
			this.name = "ping";
			this.path = "bot.owner.metrics.ping";
		}

		@Override
		public void execute(SlashCommandEvent event) {
			File chartFile = generateGraph(Metrics.pingDataStore.getRecords());

			if (chartFile != null) {
				event.getHook().editOriginalAttachments(FileUpload.fromData(chartFile)).queue();
			} else {
				editError(event, path+".failed");
			}
		}

		@Nullable
		private File generateGraph(Deque<PingData.PingRecord> pingRecords) {
			if (pingRecords.isEmpty()) return null;

			// Add time series
			var webSocketSeries = new TimeSeries("WebSocket Ping");
			var restSeries = new TimeSeries("REST Ping");

			pingRecords.forEach(record -> {
				var time = new FixedMillisecond(record.timestamp);
				webSocketSeries.add(time, record.webSocketPing);
				restSeries.add(time, record.restPing);
			});

			// Dataset
			var dataset = new TimeSeriesCollection();
			dataset.addSeries(webSocketSeries);
			dataset.addSeries(restSeries);

			// Create chart
			var chart = ChartFactory.createTimeSeriesChart(
				"Ping Metrics",
				"Timestamp",
				"Ping (ms)",
				dataset
			);

			chart.setBackgroundPaint(Color.LIGHT_GRAY);

			// Visuals
			var plot = (XYPlot) chart.getPlot();
			plot.setBackgroundPaint(Color.WHITE);
			plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
			plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
			plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
			plot.setDomainCrosshairVisible(true);
			plot.setRangeCrosshairVisible(true);

			// Set x-axis time format
			var axis = (DateAxis) plot.getDomainAxis();
			axis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));

			// Save chart as PNG
			try {
				File chartFile = File.createTempFile("ping_chart-", ".png");
				ImageIO.write(chart.createBufferedImage(600, 500),  "png", chartFile);
				return chartFile;
			} catch (IOException e) {
				App.getLogger().error("Error generating ping chart", e);
			}
			return null;
		}
	}

}

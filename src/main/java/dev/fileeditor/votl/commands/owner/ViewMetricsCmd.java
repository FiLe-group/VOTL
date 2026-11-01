package dev.fileeditor.votl.commands.owner;

import dev.fileeditor.votl.App;
import dev.fileeditor.votl.base.command.SlashCommand;
import dev.fileeditor.votl.base.command.SlashCommandEvent;
import dev.fileeditor.votl.metrics.GcMonitor;
import dev.fileeditor.votl.metrics.Metrics;
import dev.fileeditor.votl.metrics.MetricsRateLimiter;
import dev.fileeditor.votl.metrics.timeseries.PingData;
import dev.fileeditor.votl.metrics.timeseries.RestData;
import dev.fileeditor.votl.metrics.timeseries.SystemData;
import dev.fileeditor.votl.metrics.timeseries.UsageData;
import dev.fileeditor.votl.objects.AccessPermission;
import dev.fileeditor.votl.objects.constants.CmdCategory;
import dev.fileeditor.votl.objects.constants.Constants;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ViewMetricsCmd extends SlashCommand {

	public ViewMetricsCmd() {
		this.name = "metrics";
		this.path = "bot.owner.metrics";
		this.children = new SlashCommand[]{
			new CommandInfo(), new CommandStats(), new ButtonStats(), new Ping(),
			new SystemStats(), new Rest(), new Usages()
		};
		this.category = CmdCategory.OWNER;
		this.requiredPermission = AccessPermission.DEV;
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

			return stackedChart("Ping Metrics", "ping_chart-", List.of(
				new Panel(new NumberAxis("Ping (ms)"), webSocketSeries, restSeries)
			));
		}
	}

	private class SystemStats extends SlashCommand {
		private static final double MB = 1024 * 1024;

		public SystemStats() {
			this.name = "system";
			this.path = "bot.owner.metrics.system";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
			MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();

			// Current values, CPU is the recent load
			String text = """
				**CPU:** process `%.1f%%` | system `%.1f%%` | cores `%d`
				**Heap:** used `%.0f` MB | committed `%.0f` MB | max `%.0f` MB
				**Non-heap:** used `%.0f` MB
				**System memory:** used `%.0f` / `%.0f` MB
				**Threads:** `%d` | **Uptime:** %s
				**GC pauses since start:** `%d`, total `%.1f` s
				""".formatted(
				os.getProcessCpuLoad() * 100, os.getCpuLoad() * 100, os.getAvailableProcessors(),
				heap.getUsed() / MB, heap.getCommitted() / MB, heap.getMax() / MB,
				nonHeap.getUsed() / MB,
				(os.getTotalMemorySize() - os.getFreeMemorySize()) / MB, os.getTotalMemorySize() / MB,
				ManagementFactory.getThreadMXBean().getThreadCount(),
				TimeFormat.RELATIVE.atTimestamp(ManagementFactory.getRuntimeMXBean().getStartTime()),
				GcMonitor.getCount(), GcMonitor.getTime() / 1000.0
			);

			File chartFile = generateGraph(Metrics.systemData.getRecords());
			if (chartFile != null) {
				event.getHook().editOriginal(text).setFiles(FileUpload.fromData(chartFile)).queue();
			} else {
				editMsg(event, text);
			}
		}

		@Nullable
		private File generateGraph(Deque<SystemData.SystemRecord> systemRecords) {
			if (systemRecords.isEmpty()) return null;

			// Add time series
			var processCpuSeries = new TimeSeries("Process CPU");
			var systemCpuSeries = new TimeSeries("System CPU");
			var heapUsedSeries = new TimeSeries("Heap used");
			var heapCommittedSeries = new TimeSeries("Heap committed");
			var nonHeapSeries = new TimeSeries("Non-heap used");
			var gcTimeSeries = new TimeSeries("GC pause time");
			var gcMaxPauseSeries = new TimeSeries("Longest GC pause");
			var threadsSeries = new TimeSeries("Threads");

			systemRecords.forEach(record -> {
				var time = new FixedMillisecond(record.timestamp);
				if (record.processCpu >= 0) processCpuSeries.add(time, record.processCpu);
				if (record.systemCpu >= 0) systemCpuSeries.add(time, record.systemCpu);
				heapUsedSeries.add(time, record.heapUsed / MB);
				heapCommittedSeries.add(time, record.heapCommitted / MB);
				nonHeapSeries.add(time, record.nonHeapUsed / MB);
				gcTimeSeries.add(time, record.gcTime);
				gcMaxPauseSeries.add(time, record.gcMaxPause);
				threadsSeries.add(time, record.threads);
			});

			var cpuAxis = new NumberAxis("CPU (%)");
			cpuAxis.setRange(0, 100);
			return stackedChart("System Metrics", "system_chart-", List.of(
				new Panel(cpuAxis, processCpuSeries, systemCpuSeries),
				new Panel(new NumberAxis("Memory (MB)"), heapUsedSeries, heapCommittedSeries, nonHeapSeries),
				new Panel(new NumberAxis("GC per 5 min (ms)"), gcTimeSeries, gcMaxPauseSeries),
				new Panel(new NumberAxis("Threads"), threadsSeries)
			));
		}
	}

	private class Rest extends SlashCommand {
		public Rest() {
			this.name = "rest";
			this.path = "bot.owner.metrics.rest";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			// Most rate limited routes since start
			StringBuilder builder = new StringBuilder("**Queued now:** `%d`\n**Rate limited since start:** `%d`\n"
				.formatted(MetricsRateLimiter.getQueueSize(), Metrics.restRateLimited.sum()));
			Metrics.restRateLimited.collect()
				.entrySet()
				.stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
				.limit(10)
				.forEach(e -> builder.append("`%5d` | %s\n".formatted(e.getValue(), e.getKey())));
			String text = builder.toString();
			if (text.length() > 1900) text = text.substring(0, 1900);

			File chartFile = generateGraph(Metrics.restData.getRecords());
			if (chartFile != null) {
				event.getHook().editOriginal(text).setFiles(FileUpload.fromData(chartFile)).queue();
			} else {
				editMsg(event, text);
			}
		}

		@Nullable
		private File generateGraph(Deque<RestData.RestRecord> restRecords) {
			if (restRecords.isEmpty()) return null;

			// Add time series
			var requestsSeries = new TimeSeries("Requests");
			var rateLimitedSeries = new TimeSeries("Rate limited (429)");
			var queuePeakSeries = new TimeSeries("Queue peak");
			var queueNowSeries = new TimeSeries("Queue at sample");

			restRecords.forEach(record -> {
				var time = new FixedMillisecond(record.timestamp);
				requestsSeries.add(time, record.requests);
				rateLimitedSeries.add(time, record.rateLimited);
				queuePeakSeries.add(time, record.queuePeak);
				queueNowSeries.add(time, record.queueNow);
			});

			return stackedChart("Discord REST", "rest_chart-", List.of(
				new Panel(new NumberAxis("Requests per 5 min"), requestsSeries),
				new Panel(new NumberAxis("429 per 5 min"), rateLimitedSeries),
				new Panel(new NumberAxis("Queued requests"), queuePeakSeries, queueNowSeries)
			));
		}
	}

	private class Usages extends SlashCommand {
		public Usages() {
			this.name = "usages";
			this.path = "bot.owner.metrics.usages";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			File chartFile = generateGraph(Metrics.usageData.getRecords());

			if (chartFile != null) {
				event.getHook().editOriginalAttachments(FileUpload.fromData(chartFile)).queue();
			} else {
				editError(event, path+".failed");
			}
		}

		@Nullable
		private File generateGraph(Deque<UsageData.UsageRecord> usageRecords) {
			if (usageRecords.isEmpty()) return null;

			// Add time series
			var commandsSeries = new TimeSeries("Commands");
			var interactionsSeries = new TimeSeries("Interactions");

			usageRecords.forEach(record -> {
				var time = new FixedMillisecond(record.timestamp);
				commandsSeries.add(time, record.commands);
				interactionsSeries.add(time, record.interactions);
			});

			return stackedChart("Usages", "usages_chart-", List.of(
				new Panel(new NumberAxis("Received per 5 min"), commandsSeries, interactionsSeries)
			));
		}
	}

	private record Panel(NumberAxis axis, TimeSeries... series) {}

	/** Chart of panels stacked top to bottom, sharing the time axis. */
	@Nullable
	private static File stackedChart(String title, String filePrefix, List<Panel> panels) {
		// Set x-axis time format
		var axis = new DateAxis("Timestamp");
		axis.setDateFormatOverride(new SimpleDateFormat("HH:mm"));

		var plot = new CombinedDomainXYPlot(axis);
		plot.setGap(10.0);
		List<XYPlot> subplots = new ArrayList<>();
		for (Panel panel : panels) {
			var dataset = new TimeSeriesCollection();
			for (TimeSeries series : panel.series()) dataset.addSeries(series);
			var subplot = new XYPlot(dataset, null, panel.axis(), new XYLineAndShapeRenderer(true, false));
			plot.add(subplot, 1);
			subplots.add(subplot);
		}

		// Create chart
		var chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
		ChartUtils.applyCurrentTheme(chart);

		chart.setBackgroundPaint(Color.LIGHT_GRAY);

		// Visuals
		for (XYPlot subplot : subplots) {
			subplot.setBackgroundPaint(Color.WHITE);
			subplot.setDomainGridlinePaint(Color.LIGHT_GRAY);
			subplot.setRangeGridlinePaint(Color.LIGHT_GRAY);
			subplot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		}

		// Save chart as PNG
		try {
			File chartFile = File.createTempFile(filePrefix, ".png");
			ImageIO.write(chart.createBufferedImage(600, 200 + 250 * panels.size()),  "png", chartFile);
			return chartFile;
		} catch (IOException e) {
			App.getLogger().error("Error generating chart '{}'", title, e);
		}
		return null;
	}

}

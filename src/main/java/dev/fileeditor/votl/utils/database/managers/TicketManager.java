package dev.fileeditor.votl.utils.database.managers;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import dev.fileeditor.votl.utils.CastUtil;
import dev.fileeditor.votl.utils.database.ConnectionUtil;
import dev.fileeditor.votl.utils.database.LiteBase;

public class TicketManager extends LiteBase {
	
	public TicketManager(ConnectionUtil cu) {
		super(cu, "ticket");
	}

	/* tags:
	 *  0 - role request ticket
	 *  1+ - custom tags
	 */

	// add new ticket
	public void addRoleTicket(int ticketId, long userId, long guildId, long channelId, String roleIds) {
		execute("INSERT INTO %s(ticketId, userId, guildId, channelId, tagId, roleIds) VALUES (%d, %s, %s, %s, 0, %s)"
			.formatted(table, ticketId, userId, guildId, channelId, quote(roleIds)));
	}

	public void addTicket(int ticketId, long userId, long guildId, long channelId, int tagId) {
		execute("INSERT INTO %s(ticketId, userId, guildId, channelId, tagId) VALUES (%d, %s, %s, %s, %d)".formatted(table, ticketId, userId, guildId, channelId, tagId));
	}

	// get last ticket's ID
	public int lastIdByTag(long guildId, int tagId) {
		Integer data = selectOne("SELECT ticketId FROM %s WHERE (guildId=%s AND tagId=%d) ORDER BY ticketId DESC LIMIT 1"
			.formatted(table, guildId, tagId), "ticketId", Integer.class);
		return data == null ? 0 : data;
	}

	// update mod
	public void setClaimed(long channelId, long modId) {
		execute("UPDATE %s SET modId=%s WHERE (channelId=%s)".formatted(table, modId, channelId));
	}

	public void setUnclaimed(long channelId) {
		execute("UPDATE %s SET modId=NULL WHERE (channelId=%s)".formatted(table, channelId));
	}

	public Long getClaimer(long channelId) {
		return selectOne("SELECT modId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "modId", Long.class);
	}

	// set status
	public void closeTicket(Instant timeClosed, long channelId, String reason) {
		execute("UPDATE %s SET closed=1, timeClosed=%d, reasonClosed=%s WHERE (channelId=%s)".formatted(table, timeClosed.getEpochSecond(), quote(reason), channelId));
	}

	public void forceCloseTicket(long channelId) {
		execute("UPDATE %s SET closed=1 WHERE (channelId=%s)".formatted(table, channelId));
	}

	// get status
	public boolean isOpened(long channelId) {
		Integer data = selectOne("SELECT closed FROM %s WHERE (channelId=%s)".formatted(table, channelId), "closed", Integer.class);
		return data==null ? false : data==0;
	}

	public Long getOpenedChannel(long userId, long guildId, int tagId) {
		return selectOne("SELECT channelId FROM %s WHERE (userId=%s AND guildId=%s AND tagId=%s AND closed=0)".formatted(table, userId, guildId, tagId),
			"channelId", Long.class);
	}

	public List<Long> getOpenedChannel(long userId, long guildId) {
		return select("SELECT channelId FROM %s WHERE (userId=%s AND guildId=%s AND closed=0)".formatted(table, userId, guildId),
			"channelId", Long.class);
	}

	public int countOpenedByUser(long userId, long guildId, int tagId) {
		return count("SELECT COUNT(*) FROM %s WHERE (userId=%s AND guildId=%s AND tagId=%s AND closed=0)".formatted(table, userId, guildId, tagId));
	}

	public int countAllOpenedByUser(long userId, long guildId) {
		return count("SELECT COUNT(*) FROM %s WHERE (userId=%s AND guildId=%s AND closed=0)".formatted(table, userId, guildId));
	}

	public List<Long> getOpenedChannels() {
		return select("SELECT channelId FROM %s WHERE (closed=0 AND closeRequested=0)".formatted(table), "channelId", Long.class);
	}

	public List<Long> getExpiredTickets() {
		return select("SELECT channelId FROM %s WHERE (closed=0 AND closeRequested>0 AND closeRequested<=%d)".formatted(table, Instant.now().getEpochSecond()),
			"channelId", Long.class);
	}

	public List<Long> getRoleIds(String channelId) {
		String data = selectOne("SELECT roleIds FROM %s WHERE (channelId=%s)".formatted(table, channelId), "roleIds", String.class);
		if (data == null) return Collections.emptyList();
		return Stream.of(data.split(";")).map(CastUtil::castLong).toList();
	}

	public Long getUserId(long channelId) {
		return selectOne("SELECT userId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "userId", Long.class);
	}

	public Long getTicketId(long channelId) {
		return selectOne("SELECT ticketId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "ticketId", Long.class);
	}

	public boolean isRoleTicket(long channelId) {
		Integer data = selectOne("SELECT tagId FROM %s WHERE (channelId=%s)".formatted(table, channelId), "tagId", Integer.class);
		return data==null ? false : data==0;
	}

	public int countTicketsByMod(long guildId, long modId, Instant afterTime, Instant beforeTime, boolean roleTag) {
		String tagType = roleTag ? "tagId=0" : "tagId>=1";
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND modId=%s AND timeClosed>=%d AND timeClosed<=%d AND %s)"
			.formatted(table, guildId, modId, afterTime.getEpochSecond(), beforeTime.getEpochSecond(), tagType));
	}

	public int countTicketsByMod(long guildId, long modId, Instant afterTime, boolean roleTag) {
		String tagType = roleTag ? "tagId=0" : "tagId>=1";
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND modId=%s AND timeClosed>=%d AND %s)"
			.formatted(table, guildId, modId, afterTime.getEpochSecond(), tagType));
	}

	/**
	 * Close requested:<p>
	 *  0 - not requested;
	 *  >1 - requested, await, close when time expires;  
	 *  <-1 - closure canceled, do not request.
	 * @param channelId Ticket's channel ID
	 * @param closeRequested Time in epoch seconds
	 */
	public void setRequestStatus(long channelId, long closeRequested) {
		execute("UPDATE %s SET closeRequested=%d WHERE (channelId=%s)".formatted(table, closeRequested, channelId));
	}

	public long getTimeClosing(long channelId) {
		Long data = selectOne("SELECT closeRequested FROM %s WHERE (channelId=%s);".formatted(table, channelId), "closeRequested", Long.class);
		return data == null ? 0L : data;

	}
}

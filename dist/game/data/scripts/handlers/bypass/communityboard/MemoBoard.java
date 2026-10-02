/*
 * Copyright (c) L2J Mobius.
 *
 * Licensed under the GNU General Public License v3.
 * You may obtain a copy of the License at:
 * http://www.gnu.org/licenses/
 */
package handlers.bypass.communityboard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.entity.actor.Player;
import org.l2jmobius.gameserver.entity.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.handler.IWriteBoardHandler;
import org.l2jmobius.gameserver.managers.PremiumManager;

/**
 * Raid Boss Info board (database-driven version) Shows live DB raidboss_spawnlist info with pagination and level display. Author: Ovidijus
 */
public class MemoBoard implements IWriteBoardHandler
{
	private static final String[] COMMANDS =
	{
		"_bbsmemo",
		"_bbstopics"
	};
	private static final int PER_PAGE = 23;
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	// private static final String SELECT_RAIDS = "SELECT boss_id, respawn_time, currentHp, currentMp, loc_x, loc_y, loc_z FROM raidboss_spawnlist ORDER BY boss_id ASC";
	// Pridėjom Raido mirties laiką
	private static final String SELECT_RAIDS = "SELECT boss_id, respawn_time, death_time, currentHp, currentMp, loc_x, loc_y, loc_z FROM raidboss_spawnlist ORDER BY boss_id ASC";
	
	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
	
	@Override
	public boolean onCommand(String command, Player player)
	{
		
		System.out.println(">>> MEMOBOARD: " + command);
		
		// CommunityBoardHandler.separateAndSend(HtmCache.getInstance().getHtm(player, "html/adventurer_guildsman/31797.htm"), player);
		
		int page = 1;
		try
		{
			String[] parts = command.split(" ");
			if (parts.length > 1)
			{
				page = Integer.parseInt(parts[1]);
			}
		}
		catch (Exception ignored)
		{
		}
		
		showRaidBossList(player, page);
		return true;
	}
	
	private void showRaidBossList(Player player, int page)
	{
		if (PremiumManager.getInstance().getPremiumExpiration(player.getAccountName()) == 0)
		{
			final String deny = "<html><body><br><br><center><br1><br1><font color=LEVEL>Only premium accounts can use this feature.</font></center></body></html>";
			CommunityBoardHandler.separateAndSend(deny, player);
			return;
		}
		
		class BossInfo
		{
			String name;
			int bossid;
			int level;
			boolean alive;
			long respawn;
			long death; // naujas mirties raido laukas
			// double hp;
			// double mp;
			// int x, y, z;
		}
		
		final List<BossInfo> bosses = new ArrayList<>();
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_RAIDS);
			ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				int bossId = rs.getInt("boss_id");
				NpcTemplate template = NpcData.getInstance().getTemplate(bossId);
				if (template == null)
				{
					continue;
				}
				
				BossInfo info = new BossInfo();
				info.bossid = bossId;
				info.name = template.getName();
				info.level = template.getLevel();
				info.respawn = rs.getLong("respawn_time");
				info.death = rs.getLong("death_time");
				// info.hp = rs.getDouble("currentHp");
				// info.mp = rs.getDouble("currentMp");
				/*
				 * info.x = rs.getInt("loc_x"); info.y = rs.getInt("loc_y"); info.z = rs.getInt("loc_z");
				 */
				info.alive = (info.respawn <= System.currentTimeMillis());
				bosses.add(info);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		// Sort: Alive first, then by level descending - DEATH LAST
		bosses.sort(Comparator.<BossInfo, Boolean> comparing(b -> !b.alive) // alive first
			.thenComparing((BossInfo b) -> b.level, Comparator.reverseOrder()));
		
		// Rūšiuoja tik pagal level, gyvybingumo statusas nekinta
		// bosses.sort(Comparator.comparingInt((BossInfo b) -> b.level).reversed());
		
		int totalPages = Math.max(1, (int) Math.ceil((double) bosses.size() / PER_PAGE));
		page = Math.max(1, Math.min(page, totalPages));
		int start = (page - 1) * PER_PAGE;
		int end = Math.min(start + PER_PAGE, bosses.size());
		
		StringBuilder html = new StringBuilder(4096);
		
		html.append("<html><body><br><br><center><br1><br1><title>Raid Boss Info</title></center><center>");
		html.append("<table width=600 bgcolor=000000 border=0>");
		html.append("<tr><td><center><font color=LEVEL>Account: Premium</font></center></td></tr>");
		html.append("<tr><td><center>Page: ").append(page).append(" / ").append(totalPages).append("</center></td></tr>");
		// html.append("<tr><td><br1></td></tr>");
		
		html.append("<table width=500>");
		/*
		 * html.append("<tr bgcolor=222222>"); html.append("<td width=170><font color=LEVEL>Name</font></td>"); html.append("<td width=50><font color=LEVEL>Lvl</font></td>"); html.append("<td width=70><font color=LEVEL>Status</font></td>");
		 * html.append("<td width=150><font color=LEVEL>Respawn Time</font></td>"); html.append("<td width=100><font color=LEVEL>Coords</font></td>"); html.append("<td width=60><font color=LEVEL>HP</font></td>"); html.append("<td width=60><font color=LEVEL>MP</font></td>"); html.append("</tr>");
		 */
		
		for (int i = start; i < end; i++)
		{
			BossInfo rb = bosses.get(i);
			String color = rb.alive ? "00FF00" : "FF0000";
			String respawnText = rb.alive ? "-" : DATE_FORMAT.format(rb.respawn);
			String deathText = (rb.death == 0 ? "-" : DATE_FORMAT.format(rb.death));
			
			/* <a action="bypass -h Quest RaidbossInfo 25095">Elf Renoa (lv29)</a> */
			
			html.append("<tr>");
			html.append("<td><font size=20 color=LEVEL><a action=\"bypass -h Quest RaidbossInfo ").append(rb.bossid).append("\">").append(rb.name).append("</a></font></td>");
			// html.append("<td><font size=20 color=LEVEL><a action=\"bypass -h npc_%objectId%_Quest RaidbossInfo ").append(rb.bossid).append("\">").append(rb.name).append("</a></font></td>");
			html.append("<td>").append(rb.level).append("</td>");
			html.append("<td><font color=").append(color).append(">").append(rb.alive ? "Alive" : "Dead").append("</font></td>");
			// html.append("<td>").append(respawnText).append("</td>");
			html.append("<td>").append(deathText).append("</td>");
			
			// html.append("<td>").append(rb.x).append(",").append(rb.y).append("</td>");
			// html.append("<td>").append((int) rb.hp).append("</td>");
			// html.append("<td>").append((int) rb.mp).append("</td>");
			html.append("</tr>");
		}
		
		html.append("</table>");
		
		// Page navigation
		html.append("<center><table><tr>");
		for (int p = 1; p <= totalPages; p++)
		{
			if (p == page)
			{
				html.append("<td width=20><font color=LEVEL>[<b>").append(p).append("</b>]</font></td>");
			}
			else
			{
				html.append("<td width=20><a action=\"bypass -h _bbsmemo ").append(p).append("\">[").append(p).append("]</a></td>");
			}
		}
		
		html.append("</tr></table></center>");
		html.append("</body></html>");
		
		CommunityBoardHandler.separateAndSend(html.toString(), player);
	}
	
	@Override
	public boolean writeCommunityBoardCommand(Player player, String arg1, String arg2, String arg3, String arg4, String arg5)
	{
		return false;
	}
}

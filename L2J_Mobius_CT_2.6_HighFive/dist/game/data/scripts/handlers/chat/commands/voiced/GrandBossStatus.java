/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package handlers.chat.commands.voiced;

/**
 * @author Ovis
 *
 */
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
import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.managers.PremiumManager;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * Grand Boss status board using data from grandboss_data table. Shows Alive/Dead status, death time, supports pagination and premium restriction. Author: ChatGPT + Ovidijus
 */
public class GrandBossStatus implements IVoicedCommandHandler
{
	
	private static final String[] VOICED_COMMANDS =
	{
		"grandboss"
	};
	
	private static final int PER_PAGE = 20;
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	private static final String SELECT_BOSS = "SELECT boss_id, respawn_time, death_time FROM grandboss_data";
	
	private static class BossInfo
	{
		String name;
		int level;
		boolean alive;
		long deathTime;
		long respawnTime;
	}
	
	@Override
	public boolean onCommand(String command, Player activeChar, String target)
	{
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
		
		if (command.startsWith("grandboss"))
		{
			return showGrandBossStatus(activeChar, page);
		}
		return false;
	}
	
	private boolean showGrandBossStatus(Player player, int page)
	{
		// Tik Premium
		long premium = PremiumManager.getInstance().getPremiumExpiration(player.getAccountName());
		if (premium == 0)
		{
			NpcHtmlMessage deny = new NpcHtmlMessage(5);
			deny.setHtml("<html><body><center><font color=\"LEVEL\">Only premium accounts can use this feature.</font></center></body></html>");
			player.sendPacket(deny);
			return true;
		}
		
		final List<BossInfo> bosses = new ArrayList<>();
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_BOSS);
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
				info.name = template.getName();
				info.level = template.getLevel();
				info.respawnTime = rs.getLong("respawn_time");
				info.deathTime = rs.getLong("death_time");
				info.alive = (info.respawnTime <= System.currentTimeMillis());
				bosses.add(info);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		// Surūšiuojame pagal level (aukščiausi viršuje)
		bosses.sort(Comparator.comparing((BossInfo b) -> b.level).reversed());
		
		int totalPages = Math.max(1, (int) Math.ceil((double) bosses.size() / PER_PAGE));
		page = Math.max(1, Math.min(page, totalPages));
		int start = (page - 1) * PER_PAGE;
		int end = Math.min(start + PER_PAGE, bosses.size());
		
		StringBuilder html = new StringBuilder(4096);
		html.append("<html><body><title>Grand Boss Status</title><center>");
		html.append("<table width=340>");
		html.append("<tr><td><center><font color=LEVEL>Account: Premium</font></center></td></tr>");
		html.append("<tr><td><center>Page: ").append(page).append(" / ").append(totalPages).append("</center></td></tr>");
		html.append("<tr><td><br></td></tr>");
		
		html.append("<table width=310>");
		html.append("<tr><td width=140><font color=LEVEL>Name</font></td>");
		html.append("<td width=40><font color=LEVEL>Lvl</font></td>");
		html.append("<td width=60><font color=LEVEL>Status</font></td>");
		html.append("<td width=60><font color=LEVEL>Last Death</font></td></tr>");
		
		for (int i = start; i < end; i++)
		{
			BossInfo rb = bosses.get(i);
			String color = rb.alive ? "00FF00" : "FF0000";
			String status = rb.alive ? "Alive" : "Dead";
			
			// Jei boss gyvas -> tiesiog "-"
			String deathText = rb.alive ? "-" : ((rb.deathTime > 0) ? DATE_FORMAT.format(new java.util.Date(rb.deathTime)) : "-");
			
			html.append("<tr>");
			html.append("<td><font color=LEVEL>").append(rb.name).append("</font></td>");
			html.append("<td>").append(rb.level).append("</td>");
			html.append("<td><font color=").append(color).append(">").append(status).append("</font></td>");
			html.append("<td>").append(deathText).append("</td>");
			html.append("<tr><td colspan=4><font color=555555>------------------</font></td></tr>");
			html.append("</tr>");
		}
		
		html.append("</table>");
		
		// Puslapiavimas
		html.append("<br><center><table><tr>");
		for (int p = 1; p <= totalPages; p++)
		{
			if (p == page)
			{
				html.append("<font color=LEVEL>[<b>").append(p).append("</b>]</font> ");
			}
			else
			{
				html.append("<a action=\"bypass -h user_grandboss ").append(p).append("\">[").append(p).append("]</a> ");
			}
		}
		html.append("</tr></table></center>");
		
		html.append("</center></body></html>");
		
		NpcHtmlMessage msg = new NpcHtmlMessage(5);
		msg.setHtml(html.toString());
		player.sendPacket(msg);
		return true;
	}
	
	@Override
	public String[] getCommandList()
	{
		return VOICED_COMMANDS;
	}
}

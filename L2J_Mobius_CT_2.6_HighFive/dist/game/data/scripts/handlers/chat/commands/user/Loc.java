package handlers.chat.commands.user;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.entity.actor.Player;
import org.l2jmobius.gameserver.entity.actor.enums.creature.Race;
import org.l2jmobius.gameserver.entity.zone.type.RespawnZone;
import org.l2jmobius.gameserver.handler.IUserCommandHandler;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

public class Loc implements IUserCommandHandler
{
	private static final Logger LOGGER = Logger.getLogger("FakePlayerPoints");
	
	private static final int[] COMMAND_IDS =
	{
		0
	};
	
	@Override
	public boolean onCommand(int id, Player player)
	{
		int region;
		final RespawnZone zone = ZoneManager.getInstance().getZone(player, RespawnZone.class);
		if (zone != null)
		{
			region = MapRegionData.getInstance().getRestartRegionLocId(player, zone.getAllRespawnPoints().get(Race.HUMAN));
		}
		else
		{
			region = MapRegionData.getInstance().getMapRegionLocId(player);
		}
		
		SystemMessage sm;
		if (region > 0)
		{
			sm = new SystemMessage(region);
			if (sm.getSystemMessageId().getParamCount() == 3)
			{
				sm.addInt(player.getX());
				sm.addInt(player.getY());
				sm.addInt(player.getZ());
			}
		}
		else
		{
			sm = new SystemMessage(SystemMessageId.CURRENT_LOCATION_S1);
			sm.addString(player.getX() + ", " + player.getY() + ", " + player.getZ());
		}
		
		// Išsaugoti koordinates į log failą
		final String pointLine = String.format("<point X=\"%d\" Y=\"%d\" Z=\"%d\" delay=\"10\" run=\"true\" />", player.getX(), player.getY(), player.getZ());
		LOGGER.info(pointLine);
		
		player.sendPacket(sm);
		return true;
	}
	
	@Override
	public int[] getCommandList()
	{
		return COMMAND_IDS;
	}
}
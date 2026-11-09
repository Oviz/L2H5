package custom;

import org.l2jmobius.gameserver.entity.actor.Player;
import org.l2jmobius.gameserver.entity.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.mechanics.events.Containers;
import org.l2jmobius.gameserver.mechanics.events.EventType;
import org.l2jmobius.gameserver.mechanics.events.holders.actor.player.OnPlayerLevelChanged;
import org.l2jmobius.gameserver.mechanics.events.listeners.ConsumerEventListener;

public class Level40Reward
{
	private static final int REQUIRED_LEVEL = 40;
	private static final int ADENA_ID = 57;
	private static final long ADENA_AMOUNT = 4000000L;
	private static final String REWARD_FLAG = "Level40Adena";
	
	public Level40Reward()
	{
		Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_LEVEL_CHANGED, (OnPlayerLevelChanged event) -> onPlayerLevelChanged(event), this));
	}
	
	private void onPlayerLevelChanged(OnPlayerLevelChanged event)
	{
		final Player player = event.getPlayer();
		if (player == null)
		{
			return;
		}
		
		if ((event.getOldLevel() < REQUIRED_LEVEL) && (event.getNewLevel() >= REQUIRED_LEVEL))
		{
			// NAUDOJAME VEIKĖJO KINTAMUOSIUS (ne account), KAD KIEKVIENAS VEIKĖJAS GALĖTŲ GAUTI ATSKIRAI
			if (!player.getVariables().getBoolean(REWARD_FLAG, false))
			{
				player.getInventory().addItem(ItemProcessType.REWARD, ADENA_ID, ADENA_AMOUNT, player, null);
				
				// Išsaugome veikėjo lygmenyje
				player.getVariables().set(REWARD_FLAG, true);
				
				player.sendMessage("Sveikiname pasiekus 40 lygį! Gavote 4 000 000 Adena dovanų.");
			}
		}
	}
	
	public static void main(String[] args)
	{
		new Level40Reward();
	}
}
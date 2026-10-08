# AscendantCore 1.1

Paper 1.21.11, Java 21. Open the panel with `/ascendantcore:gui`, `/ascdcore:gui` or `/ascendantcore gui`.
Left click toggles, right click configures, numbers are typed in an anvil. Black and white GUI, noteblock "bit" sounds.

## Build
`mvn clean package` -> `target/AscendantCore.jar` (or let the GitHub Action in `.github/workflows` build it).

## Commands
- `/ascendantcore [gui|reload|revive <player>|restart [cancel|status]]` (aliases `/ascdcore`, `/acore`)
- `/ascdrestart [cancel|status]` - start the restart countdown / see RAM, TPS, uptime and the restart method
- `/string` - fills your inventory with string
- `/withdrawheart [amount]`

## What's new in 1.2
- Villagers are picked up as a player-head item (shift + right-click) and come back when you place the head, with profession, level and trades.
- Custom crafting menus use one pane colour. Rituals: wither spawn sound + 4 lightning strikes 5 blocks around the crafting table.
- Combat: item cooldowns while in combat, kill sound, head drops (current / SkinsRestorer skin), lightning on kill.
- Enchant limiter icons are real enchanted books. Item cooldown and rituals have 20 slots.

## Notes
- Villager head texture: looked up once from Mojang (MHF_Villager) when the server starts. Or paste a base64 texture value into
  modules.click-villagers.head-texture in config.yml.
- Restart method AUTO uses the spigot.yml restart-script when the file exists, otherwise it shuts down (your host/panel starts it again).
- Safezone wall needs WorldGuard (regions with pvp = deny). It only ever affects players who are in combat.
- Entity tracking range has no live API: the GUI button writes it to spigot.yml (backup made), restart to apply.
- A true "disable health indicator mod" needs packet-level spoofing (ProtocolLib/PacketEvents) and is NOT included.
- Anti-minimap codes are only a request to the client: mods that ignore them can't be stopped by a server plugin.

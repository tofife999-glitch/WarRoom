# War Room

Follow the sector's wars as stories, in the Intel screen. Starsector 0.98a-RC8; needs Nexerelin and LazyLib.

Open the Intel screen and pick the **War Room** tag. It reads like an intelligence report on your ship.

- **Hostilities**: a card per war (your wars first) with a bar of who holds the advantage, who is gaining
  ground, battles won, colonies captured, ships lost and the latest event; alliance wars as one block.
  Sort by war age, battles, colonies captured or advantage.
- **Full report** per war: its war log (with turning points, and the systems where the fighting has been),
  operations under way, the military report, strength comparison and advantage over time; jump to faction
  profiles, raid intel or the map.
- **Archive**: a timeline of every war on record (1 to 10 cycles or all) with icons for colonies captured,
  major battles, bombardments and peace, and a Chronicle of every event and the Sector news.
- **Faction and Alliance Archive**: click a faction's flag or name (or an alliance's name) in the report, or
  the small button on the Intel screen's Factions tab, for the Archive of just that faction or alliance, with
  its service record on top. If a game update changes the Factions tab, its button just doesn't appear.
- **Standings**: every faction (or alliance) holding colonies, by colony size, with its change since records
  began, the colony size it took and lost in war, and its wars under way. Click a heading to sort.
- **Briefing**: plain explanations of war weariness, peace, alliances, fleet and ground pools, raids and
  invasions, and how the war score works.
- **Recorded**: battles near you exactly, fights far away as estimates marked "~", captures, raids
  (yours included), invasions, bombardments, ceasefires and peace treaties, alliances, factions losing
  their last colony. Pirate wars are hidden unless
  you are in them; one click shows them.
- **Screen touches**: a report number, addressee, relay light (naming the relay), a short sweep, a title that
  flickers on and a trace of the Sector's tension in the header; a clickable intercept line, engaged lights,
  momentum arrows, a mark for new wars and gauge marks on Hostilities; distances on a war report's theatre line;
  a now line, cycle bands, stamped months and struck names in the Archive; ranks, struck names and a Sector
  total in Standings, and ranks in faction tooltips; quiet interface sounds; a shipboard signature and "End of
  assessment" at the bottom of each tab. Each one can be switched off in `data/config/settings.json`, or all
  at once with `"warRoom_flourishes":false`.
- **Demo**: with Console Commands, `warroom_demo` shows a made-up history from your sector's real factions to
  try the pages out; nothing of it is saved, and `warroom_demo off` brings your records back.

War Room records from the moment it is installed; for the full history of every war, start a new save.
The war score weights, the size of a "large battle" and how far back "Past 90 days" looks are in
`data/config/settings.json`.

You can add it to a running save and remove it again: it keeps only plain values in the save. Removing it
loses what it has recorded.

MIT license. Credits: Histidine (Nexerelin), LazyWizard (LazyLib, Console Commands); some icon shapes follow
Tabler Icons (MIT, see graphics/warroom/icons).

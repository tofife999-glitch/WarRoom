package warroom.ui;

/**
 * The Briefing's text: how the Sector's wars work and how this report counts them, for Nexerelin 0.12.2d.
 * Written from the game's and Nexerelin's sources and fact-checked against them (2026-10-21). A paragraph
 * starting with "- " is a bullet; the highlights are shown in the highlight colour where a paragraph
 * contains them.
 */
final class BriefingText {
    private BriefingText() {
    }

    static final class Section {
        final String title;
        final String[] paragraphs;
        final String[] highlights;

        Section(String title, String[] paragraphs, String[] highlights) {
            this.title = title;
            this.paragraphs = paragraphs;
            this.highlights = highlights;
        }
    }

    static final Section[] SECTIONS = {
            new Section("About this Briefing", new String[]{
                    "This Briefing explains how the Sector's wars work and how this report counts them. Nexerelin's rules decide what happens in the Sector; War Room's rules only decide how this report counts and words it, and change nothing in the game.",
                    "Figures are those of Nexerelin 0.12.2d and War Room's default settings. Changed settings or other mods may differ."},
                    new String[]{"Nexerelin's rules", "War Room's rules", "Nexerelin 0.12.2d"}),
            new Section("Reading the report", new String[]{
                    "Each war's bar shows each side's share of the war score; the line under it reads, by the leader's share:",
                    "- Nothing to report yet: no one has scored.",
                    "- Evenly matched: both under 55%.",
                    "- Hegemony holds the advantage: 55% or more.",
                    "- Hegemony holds a clear advantage: 75% or more. Ended wars say \"held\".",
                    "The war log notes each turning point: a side taking the advantage (55% or more), or the two being evenly matched again (back within 47% to 53%). Between those lines nothing is logged, so a war hovering near one does not fill the log.",
                    "Past {days} days weighs roughly the last {days} days' points: under {quiet} in all is quiet, a side with 60% or more is gaining ground, anything else is an even exchange. How far back it looks is a War Room setting. The line under the title counts what happened since you last opened War Room; hover it for the list.",
                    "Battles won counts confirmed battles, fought near you with every ship counted, and reported ones, settled far away; on a war page, the row \"of these, reported\" gives the far-away part.",
                    "A ~ marks a number that includes estimates. For the split, hover the number on a war card, or the row name on a war page.",
                    "A war under 30 days old, begun after records began, is marked as a new conflict. Ended wars stay under Recently ended for 14 days."},
                    new String[]{"Nothing to report yet", "Evenly matched", "holds the advantage", "holds a clear advantage", "55%", "75%", "Past {days} days", "60%", "gaining ground", "confirmed", "reported", "of these, reported", "~", "new conflict", "Recently ended", "14 days", "turning point", "47% to 53%"}),
            new Section("War score", new String[]{
                    "War Room's measure of who is winning, counted from the war's start or from when records began. A side scores for:",
                    "- Territory: each colony size it captures or bombards away; a destroyed colony counts its full size.",
                    "- Enemy losses: 1 point per 10 fleet points, estimates included.",
                    "- Raids and battles: 5 per raid won, 5 per invasion repelled, 3 per reported battle won.",
                    "Territory weighs more against a small faction: a colony size is worth 20 points if the enemy's colonies totaled size 20 at the war's start, 5 if 80, never more than 80.",
                    "On the bar each side gets a hidden head start of 20 points, so 40 points to 0 shows as 75%, not 100%. The War score row shows the points without it.",
                    "The weights are War Room settings. Changing them redraws every war score bar and the War score row, ended wars included; Past {days} days, Advantage over time and the Archive timeline keep the points as scored. Service records judge the wars the Archive has dropped as they stood when it dropped them."},
                    new String[]{"War Room's measure", "1 point per 10 fleet points", "5 per raid won", "5 per invasion repelled", "3 per reported battle won", "20 points", "5 if 80", "never more than 80", "head start of 20 points", "75%", "Archive timeline"}),
            new Section("Battles near you and far away", new String[]{
                    "Starsector creates real ships within about 1.6 light-years of your fleet and keeps them a while after you leave. Farther out, most fleets are only planned routes, though stations and some others are always real.",
                    "Confirmed battles are fought by real fleets, AI against AI included, every ship lost counted. A win counts only if a ship was lost, and only for the lead fleets' factions. Losses of 200 fleet points or more, both sides together, make a major battle for the war log.",
                    "Reported battles: when an offensive reaches its target far from you, Nexerelin settles the fight without spawning ships. Every warfleet there hostile to just one side joins (not traders, smugglers or your fleet), as does the target's station. In up to 10 rounds both sides deal 10% of their strength, the stronger side's damage raised, the weaker's lowered; below 40% of its starting strength a side withdraws. Fleets lose strength, usually not ships.",
                    "War Room credits the winner and estimates losses from lost strength, marked ~. A reported battle with 200 fleet points or more lost in all also goes into the war log as a major battle, marked reported, with where it was fought."},
                    new String[]{"1.6 light-years", "most fleets", "Confirmed battles", "200 fleet points", "major battle", "Reported battles", "10 rounds", "10%", "40%", "~"}),
            new Section("Colonies and bombardment", new String[]{
                    "Colony size measures population, each size about ten times the one below: thousands at 3, where yours start; millions at 6, where yours stop by default; tens of millions from 7. Sizes 1 and 2 are mostly stations and outposts added by mods.",
                    "Saturation bombardment lowers a colony one size, disrupts its industries for a year or more and costs 10 stability; at size 4 or smaller, unless story-critical, it destroys the colony (decivilized and ownerless). Tactical bombardment disrupts military industries and costs 1 stability. Either kind pollutes a habitable world.",
                    "AI factions saturation-bomb only enemies they are Vengeful toward, and only with Nexerelin's NPC bombardment setting on (off by default). Two exceptions aim at you:",
                    "- After you saturation-bomb its worlds, a faction may send a bombardment fleet at your colonies.",
                    "- A colony of yours of size 4 or smaller in a system another faction claims draws a tribute demand. Refuse, or be at war with that faction, and it may send an expedition to saturation-bomb the colony."},
                    new String[]{"ten times", "thousands at 3", "millions at 6", "one size", "10 stability", "size 4 or smaller", "destroys the colony", "1 stability", "Vengeful", "off by default", "tribute demand"}),
            new Section("Raids, invasions and ground battles", new String[]{
                    "An offensive organizes, assembles, travels, then acts at its target. Launched means its fleets set out.",
                    "Invasion: the fleets must win in orbit first, or it ends. Troops then fight a ground battle, one turn a day, until one side has no units left; an AI attacker withdraws after 6 turns without combat, and peace ends it. For 75 days after a capture, only the former owner and its allies aim AI raids or invasions at the colony.",
                    "Raid: sent at 85% of an invasion's size. Each colony it beats in orbit loses up to 3 stability, plus an industry to disruption unless its ground defences outweigh the raiders' about three to one; far from you, its station is disrupted too. It fails if it wins nowhere, and turns back below 40% of its starting fleet points or, usually, when every target looks too strong.",
                    "Raids won and Invasions won show won of launched, such as 1 of 3. Launched raids include base strikes and bombardment campaigns; a bombardment campaign never counts as won, but scores the colony size it removes. Your own raids and tactical bombardments count as raids won for the faction you fight for."},
                    new String[]{"Launched", "one turn a day", "6 turns", "75 days", "85%", "up to 3 stability", "an industry", "three to one", "40%", "1 of 3"}),
            new Section("War weariness", new String[]{
                    "How tired a faction is of fighting. It rises:",
                    "- By 20 every 3 days per enemy, about 2,400 a cycle per war, except pirates, factions outside Nexerelin's diplomacy such as the independents, and enemies it can never make peace with.",
                    "- By losses in fleet points: all of a losing lead fleet's, half of a winning one's (not in pirate or reported battles); half of a failed offensive's, all once its fleets set out.",
                    "- By up to 55 per colony lost; your attacks on a size-6 colony add about 20 per raid, 180 per tactical bombardment and 1,800 per saturation bombardment it survives.",
                    "It falls 20 every 3 days with no such war, and 3,000 at a ceasefire or treaty. From 5,000 a faction will talk peace; around 7,500 it seeks peace and starts no new wars.",
                    "Allies share every change, so alliances tire together. Traits scale gains: Stalwart two thirds, Weak-willed half again, Forever War none. The figure shown, which Nexerelin tests, is a quarter higher per extra enemy, a quarter lower with none."},
                    new String[]{"20 every 3 days", "2,400", "half of a winning one's", "55", "180", "1,800", "3,000", "5,000", "7,500", "Stalwart", "Weak-willed", "Forever War", "a quarter"}),
            new Section("Ceasefires and peace", new String[]{
                    "From 5,000 war weariness a faction will talk peace, given all of these:",
                    "- The enemy is at 5,000 too. If it is your faction, your figure does not matter: you get a ceasefire offer, at most every 60 days per faction, refused if ignored.",
                    "- No war has begun in the Sector for 30 days; this one is at least 180 days old.",
                    "- The two can make peace at all, with no invasion, raid or ground battle between them or their allies under way.",
                    "The chance is about 90% with both at 5,000 and you at level 15. Under the strategic AI (the default), factions decide at monthly strategy meetings, usually pressing for peace only from about 7,500.",
                    "Between Hostile factions 3 in 10 deals are treaties, the rest ceasefires; Vengeful pairs only get ceasefires. Both cut each side's weariness by 3,000 (0.12.2d ignores the 6,000 set for treaties).",
                    "A ceasefire lifts relations by 30 to 45, to at least Inhospitable but no higher than Neutral; a treaty by 50 to 65, to at least Suspicious but no higher than Favorable. A war's log says which one ended it; peace made any other way, such as by an alliance vote, reads Hostilities ceased.",
                    "A faction that loses its last colony gets a ceasefire with each enemy except you and pirates; respawn invasions, off by default, can bring it back."},
                    new String[]{"5,000", "60 days", "30 days", "180 days", "90%", "7,500", "3 in 10", "3,000", "6,000", "30 to 45", "50 to 65"}),
            new Section("How wars begin", new String[]{
                    "At -50 or worse (Hostile or Vengeful) two factions are at war. A faction declares war itself only if:",
                    "- no war has begun in the Sector for 30 days;",
                    "- its war weariness is 7,500 or less, with no ceasefire (240 days) running with the target;",
                    "- relations are Inhospitable or worse (Predatory: Welcoming or worse, toward you Suspicious or worse; Paranoid: Suspicious or worse; Pacifist: never);",
                    "- its disposition toward the target is -20 or worse (Predatory: any);",
                    "- its case for war rates over about 40, growing with dislike, militarism, strength, the target's share of the Sector and the target's other enemies, shrinking with its own other wars.",
                    "Wars also start through random diplomatic events (about every 10 to 20 days, sooner with more factions; Shots Fired forces war), alliance votes, allies stepping in, agent operations and your actions.",
                    "Above 7,500 a faction also joins no ally's war, yet random events and alliance votes can still drag it in; a ceasefire stops random events, not votes. How often the strategic AI declares war is uncertain."},
                    new String[]{"-50", "30 days", "7,500", "240 days", "toward you Suspicious", "-20", "about 40", "10 to 20 days", "Shots Fired"}),
            new Section("Alliances and their votes", new String[]{
                    "From day 120, two non-pirate factions at Friendly or better with a shared alignment can ally. A faction Friendly with an alliance on average, of a compatible alignment and hostile to none of its members can join. Members leave when two of them go to war, or when one's relations with the rest drop below Welcoming.",
                    "When a member starts or ends a war with an outsider, its alliance votes on following. Yes comes from the starter, members already in that state and those with Helps Allies. No to war comes from any Friendly or better with the target or never able to fight it; no to peace, from any never able to make peace with it. The rest weigh feelings, strength, militarism, war weariness and chance; more yes than no passes.",
                    "Then every member follows: for war, relations drop 10, to Hostile at best, then 5 more; for peace, they rise 30, to at least Inhospitable, then drop 5. A no-voter may defy, losing standing with the others, which can push it out."},
                    new String[]{"day 120", "Friendly", "compatible alignment", "Welcoming", "Helps Allies", "drop 10", "5 more", "rise 30", "defy"}),
            new Section("Fleet pool", new String[]{
                    "A faction's reserve of warships, in fleet points, for patrols, raids, invasions, task groups, vengeance fleets and counter-invasions; Nexerelin's Fleet Pool intel lists it.",
                    "Each colony adds to it daily from its ship hulls, supplies and fuel: what it makes counts 8 times, an import from its own faction once, from another three quarters. Stability 10 gives four times as much as stability 0. AI factions also get a small flat amount, more at your higher levels.",
                    "It starts at 100 and holds at most a year of its latest daily gain.",
                    "Every raid and invasion, strategy-meeting orders included, takes its full fleet points at launch, even into the negative; an automatic launch also needs over 500. Patrols take theirs on setting out and return 98% of what survives. A cancelled offensive gives back all before setting out, 75% while travelling, 60% at the target, 40% later.",
                    "Above half the cap, its fleets grow up to 15%; below 0 they shrink, to half size near minus the cap."},
                    new String[]{"fleet points", "8 times", "three quarters", "four times", "100", "a year", "full fleet points", "500", "98%", "75%", "60%", "40%", "15%", "half size"}),
            new Section("Invasion points", new String[]{
                    "Invasion points, hidden in normal play, set how often a faction launches its own raids and invasions. An offensive's size follows the target's defences, capped by the attacker's economy; the fleet pool pays, and keeps the faction from targets far beyond its means.",
                    "They come daily: a flat amount that grows with your level, plus a share of colony output; half as much at peace or at war only with pirates. Each offensive under way cuts all gains by 7.5%; none comes with 10 or more, or in a game's first 180 days.",
                    "Under the strategic AI (the default), strategy meetings order offensives: an invasion from 30,000 points, a raid from 22,500, less likely with a low fleet pool, never with an empty one; above 60,000 a faction also launches on its own. Without that AI, it launches at 30,000 and a fleet pool over 500, alternating raids and invasions.",
                    "An invasion costs 30,000 per 500 fleet points, at least 24,000; a raid, 65% of that. On the flat amount alone, at war and you at level 15, 30,000 takes about 667 days."},
                    new String[]{"hidden", "target's defences", "half as much", "7.5%", "10 or more", "180 days", "30,000", "22,500", "60,000", "500", "24,000", "65%", "667 days"}),
            new Section("Ground pool", new String[]{
                    "A faction's reserve of ground forces; one point is 10 marines. Colonies fill it daily from supplies, marines and heavy armaments, by the fleet pool's rules, plus a small flat amount; it holds at most a year of gain. Nexerelin's Fleet Pool intel shows it. It pays for:",
                    "- Invasion troops: three quarters of the estimated garrison, nearly all of it if the target cannot be bombarded; 100 to 16,000 marines.",
                    "- Raid troops: 2 marines per fleet point.",
                    "- Rebuilding garrisons after ground battles, over 3 months.",
                    "In 0.12.2d it never limits anything: every draw succeeds, the pool can go negative, and nothing reads its level except the displays. Read it as a sign of a faction's ground-war economy, not a limit.",
                    "A colony's base garrison doubles with each size: 100 at size 3, 200 at 4, 400 at 5, 800 at 6, 1,600 at 7, before industries and conditions change it."},
                    new String[]{"10 marines", "three quarters", "100 to 16,000", "2 marines", "3 months", "never limits anything", "100", "1,600"}),
            new Section("Strength comparison", new String[]{
                    "The Strength bar of an alliance war is War Room's estimate of how the two sides compare now. Half of it is each side's share of total colony size, half its share of reserves: fleet pool plus ground pool. Each side counts its alliance's members plus any outsider still fighting with it. If neither side has reserves, colony size alone decides. Fleets in space are not counted, because most exist only near your fleet.",
                    "The words, by the stronger side's share: Evenly matched under 55%, superior from 55%, far superior from 75%, No estimate when there is nothing to compare.",
                    "Treat it as a rough guide: it adds fleet points to tens of marines, and either pool can be negative.",
                    "On the page of a war under way, Strength comparison lists each side's colonies, total colony size, fleet pool, ground pool and war weariness, without a verdict. An ended war's page shows Territory instead; an alliance war's page shows just the Strength bar."},
                    new String[]{"Strength", "total colony size", "reserves", "most exist only near your fleet", "Evenly matched", "superior", "far superior", "No estimate", "55%", "75%", "rough guide", "Territory"}),
            new Section("Records", new String[]{
                    "Records begin the first day War Room runs in a save; wars already under way count from then, shown as, say, 412+ days.",
                    "A war is on record while two factions that hold colonies are hostile; factions without colonies, such as remnants and derelicts, are left out. It closes when the two stop being hostile (checked daily). If one side has lost all its colonies, it closes only after 30 days without any, even if a ceasefire came first, and reads as eliminated.",
                    "Each war log keeps 12 events, dropping the least significant first; the start and the newest always stay, and first and last captures are favoured. The Archive keeps the last 30 ended wars plus 20 pirate wars, and 60 Sector news entries.",
                    "Service records and Standings also count the wars the Archive no longer keeps. Standings compare each faction's total colony size with what it was when records began, or when it first held colonies after that. In a save from before War Room 0.6, that is the day 0.6 first ran, unless the faction was at war when records began.",
                    "After more than 3 days without War Room, it takes stock again: changes in the gap get no date, wars that ended in it close on the last day seen, and wars wholly inside it are missing. Only Operations under way depends on your intel; the rest covers the whole Sector."},
                    new String[]{"412+ days", "checked daily", "30 days", "eliminated", "12 events", "30 ended wars", "20 pirate wars", "60 Sector news entries", "3 days", "Operations under way"}),
            new Section("Pirate wars", new String[]{
                    "Pirate factions (the pirates, the Luddic Path and the others Nexerelin marks as pirates) are hostile to most of the Sector at once, and Nexerelin's peace talks leave them out, so their wars rarely end. Shown in full, they would bury the rest.",
                    "Like the independents and other factions outside Nexerelin's diplomacy, they do not tire of war over time, and nothing they decide depends on war weariness, so War Room shows their war weariness as -.",
                    "Pirate wars are hidden behind the pirate toggle, except your own wars, your employer's, and any in which a colony was captured in the last 30 days. Their war logs keep 4 events instead of 12, and the Archive keeps up to 20 of them."},
                    new String[]{"pirate toggle", "do not tire of war over time", "your attacks", "30 days", "4 events", "20"}),
    };
}

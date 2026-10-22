# Nog nodig van David

Bijgewerkt: 20 oktober 2026. Dit is het centrale overzicht voor jouw bijdragen
aan de eerste release. Werk dit document bij zodra iets geregeld is. De agent
houdt de status bij en gaat ondertussen verder met herkenbare placeholders en
concepten. Een placeholder geldt nooit als een afgeronde integratie, geldende
beleidsafspraak, geslaagde telefoontest of goedgekeurde Play-publicatie.

Deze repository is openbaar. Zet hier geen wachtwoorden, privésleutels,
walletbestanden, service-accountbestanden, API-sleutels of privébedrijfsgegevens.
Noteer alleen de status of een verwijzing naar een afgesproken private locatie.

Op 19 oktober heb je bevestigd dat deze zaken voorlopig geparkeerd mogen worden.
De agent rondt de zelfstandige appontwikkeling af met herkenbare placeholders.
Daadwerkelijke activering en controles blijven releasevoorwaarden in GitHub #36;
afgeronde ontwikkelingstickets hoeven daarop niet te blijven wachten. Je hoeft
nu geen KVK-inschrijving, account of telefoontest tussendoor te regelen.

## Bedrijf en Google Play

| Nodig | Status | Waarvoor |
| --- | --- | --- |
| Uitgeversnaam | Gekozen: **Angels’ Foundry**, met apostrof na de s. | Vermelding van de uitgever. |
| KVK-inschrijving en de uiteindelijke geregistreerde naam | Nog niet afgerond, door David bevestigd op 19 oktober. | Werkelijke bedrijfs- en accountgegevens voor #36. Privédocumenten buiten Git bewaren. |
| Zakelijke openbare contactgegevens | Nog niet aangeleverd. | Support, privacy- en verwijderverzoeken, moderatie en aanspreekpunt voor kindveiligheid. Dit mogen dezelfde contactkanalen zijn als dat daadwerkelijk zo wordt ingericht. Geen mailbox verzinnen. |
| Play-account en verificatie | Nog te regelen/bevestigen, inclusief D-U-N-S waar de gekozen registratie dat vereist. | Console, testtrack, uiteindelijke verklaringen en inzending #36. Geen inloggegevens in dit bestand. |
| Herstelkopie van de bestaande Android-ondertekeningssleutel | Door eigenaar te bevestigen, buiten Git. | Continuïteit van updates en latere Play App Signing-inrichting. De agent bewaart de bestaande ondertekeningsidentiteit. |
| Releasegebieden en doelgroep/leeftijd | Nog te bevestigen. | Definitieve winkelinstellingen en passend beleid. De app wordt niet stilzwijgend als 18+ aangemerkt. |

## Latere branding

| Nodig | Status | Waarvoor |
| --- | --- | --- |
| Eigen productnaam | Nog te kiezen in een latere brandingbespreking. De platformverwijzing is onder #38 afgemaakt; de huidige appnaam Red Square blijft voorlopig staan. | De afgesproken richting is een eigen naam met een herkenbare verwijzing naar RedSquare op Saito. Dit is geen nieuwe releaseblokkade. |

## Beleidskeuzes en echte uitvoering

De agent bereidt leesbare voorstellen en bijbehorende appschermen voor. Deze
punten vragen uiteindelijk jouw bevestiging en waar nodig een werkende procedure.
De concepten zijn geen al vastgestelde toezeggingen van het bedrijf.

| Nodig | Status | Waarvoor |
| --- | --- | --- |
| Akkoord op privacyverklaring, gebruiksvoorwaarden, gedragsregels en kindveiligheidsstandaarden | Concepten beschikbaar in beta50, via Settings en Privacy and safety. Instemming en contact-/verzoekroutes zijn voorbereid; echte kanalen, definitieve teksten en goedkeuring volgen. | Definitieve teksten, openbare hosting en versiegebonden instemming #36. |
| Moderatieproces voor jouw backend en meldingen aan de appuitgever | David heeft verantwoordelijkheid geaccepteerd; bereikbare kanalen, behandeling, escalatie en daadwerkelijk toezicht nog te bepalen. | Werkende rapportage en moderatie. Een verzonden melding betekent niet automatisch beoordeeld of verwijderd. |
| Privacy- en verwijderproces | Nog te bepalen: bereikbaar verzoekkanaal, eigendomscontrole zonder privésleutel, afhandeling en terugkoppeling. | Werkelijk uitvoerbare verzoeken en een webroute naast de app. Wallet vervangen of een bericht verbergen is geen volledige gegevensverwijdering. |
| Bewaartermijnen en uitzonderingen | Nog te bepalen voor eigen serverlogs, archieven/back-ups, meldingen, verzoeken en eventuele pushgateway. | Waarheidsgetrouwe privacytekst en daadwerkelijke opruimprocedures. Geen vaste termijn of juridische uitzondering als feit invullen. |
| Verhouding met de andere backend en dienstverleners | Nog te bevestigen voor definitieve verklaringen. | Wie behandelt wat, en wat kun je werkelijk verwijderen of toezeggen? Zelfstandige netwerk-/ontvangerkopieën blijven apart. |
| Extra crashrapportage | Nog geen keuze; er wordt hiervoor geen nieuwe SDK toegevoegd. | Definitieve gegevensinventaris en Data safety. Geen uitgebreide gebruikstracking is de afgesproken richting. |

## Externe diensten

| Nodig | Status | Waarvoor |
| --- | --- | --- |
| Geautoriseerde Android Giphy-configuratie voor Posts en Chat | Open, bestaande vraag. Gescheiden configuratie per integratie/variant. | Echte zoekresultaten, selecteren, attributie en providercontrole onder #36 (GIPHY_POSTS en GIPHY_CHAT), na afgeronde native #16/#31. Geen browserkey lenen; sleutels alleen via de private configuratieroute in `docs/OPERATIONS.md`. |
| Firebase/GCP-project en juiste Android-appassociaties | Open, bestaande vraag. | Echte achtergrondmeldingen onder #36 (SOCIAL_PUSH, PRIVATE_PUSH en GROUP_PUSH), na afgeronde native #32/#33/#34. Applicatie-ID, Firebase-app/project/sender en resolver moeten bij elkaar passen. Geen service-accountbestanden in Git. |
| Werkelijke hosting en inrichting van de pushgateway | Nog te regelen binnen afgesproken beheerprocedure. | Publiek bereikbare HTTPS-resolver, afzender en echte gesloten-appbezorging. De agent kan de code en beheerinstructies verder voorbereiden. |

## Controle op je Pixel 7

De gewone [beta62](https://davidengelen.nl/redsquare/) staat online. Deze update
bevat het compactere appframe, de Node-benaming, gedeelde statusmeldingen,
consistente postkaarten, een gerichte schrijfinterface met duidelijkere
opgeslagen en wachtende posts en herhaald liken met een limiet van twee nieuwe
likes per seconde per wallet. Chats hebben compactere koppen, berichtmenu’s en
meer ruimte voor de geschiedenis naast het toetsenbord. Je leespositie blijft
behouden bij nieuwe berichten, met een knop om naar de nieuwste te gaan.
De verdere UI-verbeteringen zijn nog in uitvoering
onder #55. Download, versie en ondertekening zijn gecontroleerd.

[Timingbuild43](https://davidengelen.nl/redsquare/performance/) is een ouder
checkpoint en bevat nog niet de nieuwe Node-tekst of het huidige appframe. Gebruik hem
niet als definitieve meting van de actuele app. De eerdere geslaagde Android10/17
installatieproef gebruikte gewone referentie35 en timing43: gescheiden wallets en
opslag, met behoud van de gewone testidentiteit. Dat bewijst geen fysieke update
naar beta62. Voor de definitieve metingen levert de agent een passende timingbuild.

De Pixel-controles blijven open onder #36 (PIXEL_CONTINUITY, PIXEL_CACHED en
PIXEL_FRESH). #35 bewaart het afgeronde ontwikkelcheckpoint. De precieze meetwijze
staat in `docs/research/FEED_PERFORMANCE_PROTOCOL.md`.

De timing-app gebruikt een eigen wallet. Gebruik hiervoor geen herstelbestand van
je gewone wallet. Push en GIF-diensten zijn nog niet geconfigureerd. Als de
releaseconfiguratie of opstartcode verandert, levert de agent een bijpassende
nieuwe timingbuild en worden de definitieve metingen herhaald. Een eerdere
meting blijft dan een checkpoint.

- [ ] Een gewone update installeren vanaf <https://davidengelen.nl/redsquare/>,
  zonder verwijderen of opslag wissen. Controleer hetzelfde publieke walletadres,
  concept, beschikbare gecachete items en gekozen backend voor en na de update.
- [ ] Vijf koude starts met bestaande cache meten in de aparte timing-app, doel
  maximaal twee seconden tot een bruikbare feed.
- [ ] Vijf eerste starts op stabiele wifi meten met een lege, afzonderlijke
  timing-installatie, doel maximaal vijf seconden. Nooit de gewone wallet wissen.
- [ ] Na echte configuratie meldingen met gesloten app testen: replies, mentions,
  privéchat en groepschat, inclusief systeemtoestemming en mute/privacykeuzes.
- [ ] Een bestaand bericht met een YouTube-video openen en op afspelen tikken.
  Noteer of de video daadwerkelijk binnen de app speelt (#17). Een afbeelding,
  laadscherm of geopende externe YouTube-app is nog geen bewijs van ingebed
  afspelen. De agent onderzoekt de technische kant; deze telefoontest kan helpen
  het verschil met de huidige providerbeperkingen op de emulators vast te stellen.

## Werk voor de agent, geen extra formulier voor David

De agent blijft eigenaar van implementatie, tests, reviews, commits en signed
reviewbuilds. De resterende YouTube-afspeelcontrole (#17) en beschikbaarheid van
de Owner Registry (#19) zijn technische open punten. Alleen een concrete nieuwe
beheeractie die buiten bestaande toestemming valt komt terug in dit overzicht.
Play-inzending en wijzigingen aan live nodes/cloud volgen hun eigen concrete
publicatie- of beheerprocedure. Ontbrekende bewijsstukken worden niet vervangen
door placeholders om een issue te kunnen sluiten.

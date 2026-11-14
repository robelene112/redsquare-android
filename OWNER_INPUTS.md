# Nog nodig van David

Bijgewerkt: 13 november 2026. Dit is het centrale overzicht voor jouw bijdragen
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
| Fileshare op echte toestellen en netwerken | Native en gecontroleerde Android10/17-/browsercontroles zijn afgerond in beta116. Aanvullende praktijkkwalificatie blijft geparkeerd. | #36 (FILESHARE_LIVE): echte ARM/OEM-toestellen, gekozen-node Relay/Stun, TURN/NAT en een volledig geïnitialiseerde communityclient. Dit vraagt nu geen herhaling van gewone ontwikkeltests op jouw telefoon; eventuele node- of providerwijzigingen volgen apart de beheerprocedure. |
| Saito Talk op echte toestellen en netwerken | Native implementatie en gecontroleerde Android10/17-/browsercontroles zijn afgerond in beta 127. De aanvullende praktijkkwalificatie blijft open onder #36. | TALK_LIVE: echte ARM/OEM-toestellen en audio-/videokwaliteit, gekozen-node Relay/Stun, TURN/NAT en een volledig geïnitialiseerde communityclient. De normale verwerking van HTTPS-links door externe apps vraagt echte domeinassociatie. Gecontroleerde Android-/browserpeers bewijzen dit niet. Er wordt geen betrouwbare inkomende oproep beloofd wanneer de app beëindigd is. Je hoeft gewone ontwikkeltests nu niet op je telefoon te herhalen; node-/providerwijzigingen blijven apart geautoriseerd. |
| Vault op echte toestellen en opslagdiensten | Lezen, upload/herstel en geavanceerde regels/permanente overdracht/verhuurmaster (#106–#108) zijn gecontroleerd op Android10/17, met onafhankelijke protocolreferenties. De gezamenlijke Vault/Store-beta volgt pas bij #115. | #36 (VAULT_LIVE): echte ARM/OEM-providerbestanden, geselecteerde opslagdienst, daadwerkelijk gefinancierde sleutelbevestiging/eigendom, permanente overdracht en creator/huurder/verloop/delegatie bij een echte provider, en interoperabiliteit met een volledig geïnitialiseerde communityclient. Gecontroleerde peers/WASM-walletreferenties bewijzen dit niet. Deze praktijkkwalificatie blijft geparkeerd; gewone ontwikkeltests blijven bij de agent en node-/provideractivering of uitgaven vragen afzonderlijke beheerautorisatie. |
| Store op echte toestellen en diensten | De native catalogus, exacte links en niet-verzonden verkopersconcepten (#109), plus SAITO-aankopen, exacte levering, terugbetalingswaarneming en herstel (#110), en aanmaken/verkopen van items, goedkeuring en terughalen van voorraad (#111), zijn gecontroleerd op Android 10/17 met eigen testpartijen. Een openbare alleen-lezen aanvraag levert 24 door de echte parser geaccepteerde aanbiedingen op. De gezamenlijke beta volgt bij #115. | #36 (STORE_LIVE): echte ARM/OEM-toestellen en een volledig geïnitialiseerde communityclient, de geselecteerde live Store, actuele eigendom/voorraad, werkelijk gefinancierde betaling/levering/terugbetaling, aanmaken/verkopen/terughalen en marketplace-goedkeuring, en de volledig geïnitialiseerde terugbetalingsruntime blijven afzonderlijke praktijkkwalificaties. De openbare aanvraag bewijst alleen transport en het catalogusformaat. Alternatieve betalingen en oorspronkelijk-orderherstel (#112) zijn eveneens gecontroleerd op beide Android-versies met eigen testpartijen. Daarvoor is bij een echte provider daarnaast de compatibiliteitsaanpassing uit `integrations/saito/store-payment-recovery.patch` nodig om de oorspronkelijke factuur veilig terug te vinden. Deze aanpassing is niet geactiveerd. Activering moet ook één actieve schrijver voor het Mixin-betaalregister of gelijkwaardige databasecoördinatie vaststellen; de wijziging coördineert geen meerdere serverprocessen. De publieke Store meldde op 14 november alleen-lezen `enabled: false`. Gewone ontwikkeltests blijven bij de agent; eventuele node-/provideractivering en echte financiële handelingen volgen aparte autorisatie. |

## Optioneel: Stack op je eigen node

Op 11 november bood jouw node geen Stack-archief aan. Voor deze beta is geen
beheeractie nodig: via de expliciet gekozen node saito.io zijn echte openbare
artikelen beschikbaar. Als je Stack ook op jouw eigen node wilt aanbieden,
volgt daarvoor apart de bestaande beheerprocedure. De app activeert geen
productiemodule en wisselt niet stilzwijgend van node.

## Beschikbare beta en latere toestelcontroles

De gewone [beta 127](https://davidengelen.nl/redsquare/) staat online. Deze versie
herstelt het goedgekeurde prototype in de echte app, met de modulekiezer,
accounttoegang, compactere postacties, profielen, chat en gegroepeerde activiteit.
Na succesvol plaatsen keer je terug naar de feed of conversatie met kort
"Post submitted". Een trage node laat geen misleidende Retry-knop meer staan.
Onder iedere post in een gesprek opent de knop Reply nu rechtstreeks een
antwoord op die specifieke post. Het losse ballonnetje bovenaan is verwijderd.
Concepten blijven bij schermrotatie behouden. Ook met grote tekst blijft het
schrijfveld bereikbaar.
De feedmelding staat nu bovenaan onder de tabbladen, ook tijdens verder lezen.
Berichten eronder blijven aanklikbaar. De profieluitleg gebruikt een neutraal
informatie-icoon en "History may be limited", met Details voor de toelichting.
Stack toont nu echte openbare artikelen van de gekozen node, met echte auteurs,
covers, Markdown en offline lezen. Jouw node heeft momenteel geen Stack-archief;
kies saito.io in Settings voor de beschikbare artikelen. Lokale schrijfconcepten
blijven bewaard; publiceren gaat voorlopig via de echte Stack-website.
De naamherkenning gebruikt nu ook de optionele Saito-naamdirectory, met aparte
caches en automatisch herstel na tijdelijke fouten. De gemelde accounts leveren
daar digirok@saito en CryptoKen@saito op. Identity details toont de naambron;
je publieke sleutel en gekozen node blijven behouden. De aparte lookup is uit
te schakelen onder Settings, Node.
Stack-links openen nu het echte artikel of auteursprofiel binnen de app. De
zichtbare URL-bron overschrijft jouw gekozen node niet; Back keert terug naar
het oorspronkelijke bericht en de leespositie.
Fileshare is beschikbaar via privéchat: kies een bestand, laat de ontvanger
expliciet accepteren en volg de voortgang. Tijdens een lopende overdracht werken
achtergrond, scherm uit en annuleren via de Android-melding. Ontvangen bestanden
sla je zelf op met Save as. Beide gebruikers moeten online zijn; er is geen
wachtende offline bestandsbezorging. Onderbrekingen worden eerlijk weergegeven.
Saito Talk biedt Audio call en Video call in privéchat en groepsgesprekken via
de modulekiezer. Een uitnodiging openen start geen microfoon of camera;
starten/aannemen vraagt toestemming. Stemgeluid loopt door in de achtergrond en
met scherm uit; camera pauzeert buiten zicht. Procesbeëindiging onderbreekt de
oproep. De gecontroleerde tests vervangen geen fysieke beeld-/geluidskwaliteit
of echte netwerk-/communityclientkwalificatie.
Herhaald liken heeft geen lokale limiet van twee per seconde en wacht niet op bevestiging in de interface.
Bij snel scrollen wacht het tekenen van postacties niet meer op achtergrondopslag.
De gerichte regressie en de complete controles zijn op beide emulators geslaagd.
Dit verhelpt een gemeten blokkade; het bewijst nog geen algemene prestatiewinst
op fysieke toestellen.

De complete Android 10- en 17-emulatorcontroles en de echte ondertekende update
van beta 83 naar 127 zijn geslaagd. Wallet, concepten, gecachte inhoud, voorkeuren
en scheiding van de debug-app zijn daarbij gecontroleerd. Je hoeft deze gewone
ontwikkelcontroles niet op je eigen telefoon te herhalen. Dat bewijst nog geen
fysieke Pixel-prestaties of echte providerbezorging.

[Timingbuild43](https://davidengelen.nl/redsquare/performance/) is een ouder
checkpoint. Gebruik die niet als definitieve meting van beta 127. Voor definitieve
fysieke prestatiemetingen levert de agent later een passende aparte timingbuild.

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

## Beheeractie voor de achterlopende node (#94)

- [x] David heeft op6november expliciet toestemming gegeven voor één gecontroleerde
  herstart van `saito.service`, gevolgd door controle van synchronisatie en actuele
  saldosnapshots. De herstart is uitgevoerd om11:08:07UTC; het nieuwe proces heeft
  PID3257586. De toestemming hoeft niet opnieuw te worden gevraagd.
- [x] De node is weer bereikbaar en bijgewerkt. Om11:22UTC toonden twee
  opeenvolgende metingen dezelfde actuele blokhoogte en blokhash als Saito.io.
  Red Square, Snake en de publieke opties antwoordden metHTTP200.

Voor de herstart leverde jouw node block1180213 van5november02:30:55UTC. De app
weigerde dat oude saldo terecht als actueel te tonen; de vijftienminutengrens
blijft behouden. De herstart herstelde de waargenomen synchronisatie; de oorspronkelijke oorzaak
is nog niet bewezen. Er zijn
geen nodeconfiguraties, wallets of databases gewijzigd.

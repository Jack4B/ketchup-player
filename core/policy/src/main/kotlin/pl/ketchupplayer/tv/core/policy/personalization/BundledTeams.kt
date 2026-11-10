package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.personalization.Team
import pl.ketchupplayer.tv.core.model.personalization.TeamAlias

/**
 * Wbudowany, mały katalog drużyn piłkarskich (polska liga i najczęściej transmitowane kluby europejskie) z aliasami.
 * To tylko słownik nazw do rozpoznawania tytułów EPG: bez terminarza, bez zewnętrznych usług. Użytkownik może dodać
 * własną drużynę. Aliasy słabe (np. nazwa miasta) liczą się tylko w parze „A - B”; alias wspólny dla kilku drużyn
 * (sama „Wisła”, „Manchester”, derby) jest niejednoznaczny i nie wskazuje żadnej z nich.
 */
object BundledTeams {
    const val VERSION = 1

    private class Def(
        val id: String,
        val name: String,
        val competition: String,
        val country: String,
        val aliases: List<String> = emptyList(),
        val weak: List<String> = emptyList(),
    )

    private fun pl(
        id: String,
        name: String,
        vararg aliases: String,
        weak: List<String> = emptyList(),
    ) = Def(id, name, "pl-ekstraklasa", "PL", aliases.toList(), weak)

    private fun eu(
        id: String,
        name: String,
        competition: String,
        country: String,
        vararg aliases: String,
        weak: List<String> = emptyList(),
    ) = Def(id, name, competition, country, aliases.toList(), weak)

    private val DEFS =
        listOf(
            pl("legia-warszawa", "Legia Warszawa", "Legia"),
            pl("lech-poznan", "Lech Poznań", "Lech", "Kolejorz"),
            pl("rakow-czestochowa", "Raków Częstochowa", "Raków"),
            pl("jagiellonia-bialystok", "Jagiellonia Białystok", "Jagiellonia", "Jaga"),
            pl("pogon-szczecin", "Pogoń Szczecin", "Pogoń"),
            pl("gornik-zabrze", "Górnik Zabrze", "Górnik"),
            pl("cracovia", "Cracovia", "Cracovia Kraków"),
            pl("widzew-lodz", "Widzew Łódź", "Widzew"),
            pl("piast-gliwice", "Piast Gliwice", "Piast"),
            pl("zaglebie-lubin", "Zagłębie Lubin", "Zagłębie"),
            pl("korona-kielce", "Korona Kielce", weak = listOf("Korona")),
            pl("radomiak-radom", "Radomiak Radom", "Radomiak"),
            pl("motor-lublin", "Motor Lublin", weak = listOf("Motor")),
            pl("gks-katowice", "GKS Katowice"),
            pl("lechia-gdansk", "Lechia Gdańsk", "Lechia"),
            pl("arka-gdynia", "Arka Gdynia", weak = listOf("Arka")),
            pl("wisla-plock", "Wisła Płock"),
            pl("wisla-krakow", "Wisła Kraków"),
            pl("termalica-nieciecza", "Bruk-Bet Termalica Nieciecza", "Termalica", "Bruk-Bet Termalica"),
            pl("slask-wroclaw", "Śląsk Wrocław", weak = listOf("Śląsk")),
            pl("ruch-chorzow", "Ruch Chorzów", weak = listOf("Ruch")),
            pl("lks-lodz", "ŁKS Łódź", "ŁKS"),
            pl("stal-mielec", "Stal Mielec", weak = listOf("Stal")),
            pl("puszcza-niepolomice", "Puszcza Niepołomice", weak = listOf("Puszcza")),
            eu("real-madryt", "Real Madryt", "es-laliga", "ES", "Real Madrid", weak = listOf("Real")),
            eu("fc-barcelona", "FC Barcelona", "es-laliga", "ES", "Barca", "Barça", weak = listOf("Barcelona")),
            eu("atletico-madryt", "Atlético Madryt", "es-laliga", "ES", "Atletico Madrid", "Atletico"),
            eu("manchester-city", "Manchester City", "en-pl", "GB", "Man City"),
            eu("manchester-united", "Manchester United", "en-pl", "GB", "Man United", "Man Utd"),
            eu("liverpool", "Liverpool FC", "en-pl", "GB", weak = listOf("Liverpool")),
            eu("arsenal", "Arsenal FC", "en-pl", "GB", "Arsenal Londyn", weak = listOf("Arsenal")),
            eu("chelsea", "Chelsea FC", "en-pl", "GB", "Chelsea Londyn", weak = listOf("Chelsea")),
            eu("tottenham", "Tottenham Hotspur", "en-pl", "GB", "Tottenham"),
            eu(
                "bayern-monachium",
                "Bayern Monachium",
                "de-bundesliga",
                "DE",
                "Bayern",
                "Bayern Munich",
                "Bayern München",
            ),
            eu("borussia-dortmund", "Borussia Dortmund", "de-bundesliga", "DE", "BVB"),
            eu("bayer-leverkusen", "Bayer Leverkusen", "de-bundesliga", "DE", "Leverkusen"),
            eu("rb-lipsk", "RB Lipsk", "de-bundesliga", "DE", "RB Leipzig", "Lipsk"),
            eu("juventus", "Juventus Turyn", "it-seriea", "IT", "Juventus", "Juve"),
            eu(
                "inter-mediolan",
                "Inter Mediolan",
                "it-seriea",
                "IT",
                "Inter Milan",
                "Internazionale",
                weak = listOf("Inter"),
            ),
            eu("ac-milan", "AC Milan", "it-seriea", "IT", weak = listOf("Milan")),
            eu("napoli", "SSC Napoli", "it-seriea", "IT", weak = listOf("Napoli")),
            eu("as-roma", "AS Roma", "it-seriea", "IT", weak = listOf("Roma")),
            eu("psg", "Paris Saint-Germain", "fr-ligue1", "FR", "PSG", "Paris SG"),
            eu("ajax", "Ajax Amsterdam", "nl-eredivisie", "NL", weak = listOf("Ajax")),
            eu("benfica", "Benfica Lizbona", "pt-liga", "PT", "Benfica"),
            eu("fc-porto", "FC Porto", "pt-liga", "PT", weak = listOf("Porto")),
        )

    /** Aliasy wspólne dla kilku drużyn: parser widzi niejednoznaczność zamiast zgadywać. */
    private val AMBIGUOUS =
        listOf(
            Triple("wisla", "wisla", listOf("wisla-krakow", "wisla-plock")),
            Triple("manchester", "manchester", listOf("manchester-city", "manchester-united")),
            Triple("borussia", "borussia", listOf("borussia-dortmund")),
            Triple("derby-krakowa", "derby krakowa", listOf("wisla-krakow", "cracovia")),
            Triple("derby-trojmiasta", "derby trojmiasta", listOf("lechia-gdansk", "arka-gdynia")),
            Triple("derby-lodzi", "derby lodzi", listOf("widzew-lodz", "lks-lodz")),
            Triple("derby-manchesteru", "derby manchesteru", listOf("manchester-city", "manchester-united")),
            Triple("derby-mediolanu", "derby mediolanu", listOf("inter-mediolan", "ac-milan")),
            Triple("derby-madrytu", "derby madrytu", listOf("real-madryt", "atletico-madryt")),
            Triple("el-clasico", "el clasico", listOf("real-madryt", "fc-barcelona")),
        )

    val teams: List<Team> = DEFS.map { Team(it.id, it.name, it.competition, it.country) }

    val aliases: List<TeamAlias> =
        buildList {
            DEFS.forEach { d ->
                (listOf(d.name) + d.aliases).forEach { add(TeamAlias(d.id, Channel.normalizeName(it))) }
                d.weak.forEach { add(TeamAlias(d.id, Channel.normalizeName(it), weak = true)) }
            }
            AMBIGUOUS.forEach { (group, alias, ids) ->
                ids.forEach { add(TeamAlias(it, alias, ambiguousGroup = group)) }
            }
        }.distinctBy { it.teamId to it.aliasNorm }

    /** Aliasy drużyny dodanej ręcznie: jej nazwa i podane przez użytkownika warianty. */
    fun userAliases(
        teamId: String,
        name: String,
        extra: List<String> = emptyList(),
    ): List<TeamAlias> =
        (listOf(name) + extra)
            .map { Channel.normalizeName(it) }
            .filter { it.length >= MIN_ALIAS }
            .distinct()
            .map { TeamAlias(teamId, it) }

    fun userTeamId(name: String): String = "user-" + Channel.normalizeName(name).replace(' ', '-')

    private const val MIN_ALIAS = 3
}

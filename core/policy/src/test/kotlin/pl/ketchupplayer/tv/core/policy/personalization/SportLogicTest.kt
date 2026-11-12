package pl.ketchupplayer.tv.core.policy.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.personalization.FollowState
import pl.ketchupplayer.tv.core.model.personalization.SportEventStatus
import pl.ketchupplayer.tv.core.model.personalization.TeamAlias
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Sugestie obserwowania, wydarzenia z EPG, kanał i czas (punkty 10 i 16 konspektu). Dane w całości zmyślone. */
class SportLogicTest {
    private val p = PersonalizationParams.DEFAULT
    private val index =
        AliasIndex(
            listOf(
                TeamAlias("wisla-nadmorska", "wisla nadmorska"),
                TeamAlias("hutnik-lesny", "hutnik lesny"),
                TeamAlias("gornik-zmyslony", "gornik zmyslony"),
                TeamAlias("stal-probna", "stal probna"),
            ),
        )
    private val channels =
        mapOf(
            "ch_sport_1" to InventoryChannel("ch_sport_1", "Sport Testowy 1"),
            "ch_sport_2" to InventoryChannel("ch_sport_2", "Sport Testowy 2"),
        )

    private fun utc(
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): Long = LocalDateTime.of(2026, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun seen(
        team: String,
        at: Long,
        opponent: String? = null,
        strength: Double = 3.0,
    ) = TeamEvidence(team, at, strength, opponent)

    private fun match(
        a: String,
        b: String,
        at: Long,
    ) = listOf(seen(a, at, b), seen(b, at, a))

    // ---- sugestie obserwowania ----

    @Test
    fun pojedynczyMeczNieTworzySugestii() {
        val now = utc(3, 8, 12)
        assertTrue(
            FollowSuggester
                .propose(
                    match("wisla-nadmorska", "hutnik-lesny", utc(3, 7, 17)),
                    emptyMap(),
                    now,
                    p,
                ).isEmpty(),
        )
    }

    @Test
    fun dwieDatyToZaMalo() {
        val history =
            match("wisla-nadmorska", "hutnik-lesny", utc(3, 7, 17)) +
                match("wisla-nadmorska", "gornik-zmyslony", utc(3, 14, 17))
        assertTrue(FollowSuggester.propose(history, emptyMap(), utc(3, 15, 12), p).isEmpty())
    }

    @Test
    fun trzyDatyIPrzewagaNadPrzeciwnikiemDajaSugestie() {
        val history =
            match("wisla-nadmorska", "hutnik-lesny", utc(3, 7, 17)) +
                match("wisla-nadmorska", "gornik-zmyslony", utc(3, 14, 17)) +
                match("wisla-nadmorska", "stal-probna", utc(3, 21, 17))
        val proposals = FollowSuggester.propose(history, emptyMap(), utc(3, 22, 12), p)
        assertEquals(listOf(listOf("wisla-nadmorska")), proposals.map { it.teamIds })
        assertEquals(listOf(utc(3, 7, 17), utc(3, 14, 17), utc(3, 21, 17)), proposals.single().datesUtcMs)
    }

    @Test
    fun trzyPrzelaczeniaJednegoPopoludniaToJednaData() {
        val history =
            listOf(utc(3, 7, 13), utc(3, 7, 16), utc(3, 7, 20)).flatMap {
                match("wisla-nadmorska", "stal-probna", it)
            }
        assertEquals(1, FollowSuggester.independentDates(history.map { it.programmeStartMs }, p).size)
        assertTrue(FollowSuggester.propose(history, emptyMap(), utc(3, 8, 12), p).isEmpty())
    }

    @Test
    fun zawszeTaSamaParaToPytanieOObieDruzyny() {
        val history =
            listOf(utc(3, 7, 17), utc(3, 14, 17), utc(3, 21, 17)).flatMap {
                match("wisla-nadmorska", "hutnik-lesny", it)
            }
        val proposals = FollowSuggester.propose(history, emptyMap(), utc(3, 22, 12), p)
        assertEquals(1, proposals.size)
        assertEquals(setOf("wisla-nadmorska", "hutnik-lesny"), proposals.single().teamIds.toSet())
    }

    @Test
    fun obserwowanaIOdrzuconaNieSaProponowaneAzDoKoncaKarencji() {
        val history =
            match("wisla-nadmorska", "hutnik-lesny", utc(3, 7, 17)) +
                match("wisla-nadmorska", "gornik-zmyslony", utc(3, 14, 17)) +
                match("wisla-nadmorska", "stal-probna", utc(3, 21, 17))
        val now = utc(3, 22, 12)
        val followed = mapOf("wisla-nadmorska" to FollowDecision(FollowState.FOLLOWED, now))
        assertTrue(FollowSuggester.propose(history, followed, now, p).isEmpty())
        val rejected =
            mapOf(
                "wisla-nadmorska" to FollowDecision(FollowState.REJECTED, now - 10 * PersonalizationParams.DAY_MS),
            )
        assertTrue(FollowSuggester.propose(history, rejected, now, p).isEmpty())
        val longAgo =
            mapOf(
                "wisla-nadmorska" to FollowDecision(FollowState.REJECTED, now - 91 * PersonalizationParams.DAY_MS),
            )
        assertEquals(1, FollowSuggester.propose(history, longAgo, now, p).size)
    }

    @Test
    fun dowodySprzedOknaSzescdziesieciuDniNieLiczaSie() {
        val history =
            match("wisla-nadmorska", "hutnik-lesny", utc(1, 3, 17)) +
                match("wisla-nadmorska", "gornik-zmyslony", utc(3, 14, 17)) +
                match("wisla-nadmorska", "stal-probna", utc(3, 21, 17))
        assertTrue(FollowSuggester.propose(history, emptyMap(), utc(3, 22, 12), p).isEmpty())
    }

    // ---- wydarzenia z EPG ----

    private fun slot(
        channel: String,
        start: Long,
        title: String = "Liga Nadmorska: Wisła Nadmorska — Hutnik Leśny",
        minutes: Int = 120,
        description: String? = null,
    ) = EpgSlot(channel, start, start + minutes * 60_000L, title, description, "Sport")

    private val followed = setOf("wisla-nadmorska")

    @Test
    fun tenSamMeczNaDwochKanalachToJednaKartaZDwomaKanalami() {
        val now = utc(3, 20, 10)
        val kickoff = utc(3, 21, 17)
        val events =
            SportEvents.build(
                listOf(slot("ch_sport_1", kickoff), slot("ch_sport_2", kickoff)),
                followed,
                index,
                channels,
                now,
                p,
            )
        assertEquals(1, events.size)
        val card = SportEvents.cards(events, channels, now, p).single()
        assertEquals(listOf("Sport Testowy 1", "Sport Testowy 2"), card.channels.map { it.channelName })
        assertEquals(kickoff, card.kickoffUtcMs)
    }

    @Test
    fun kanalSpozaListyAlboUkrytyToKanalNiepotwierdzony() {
        val now = utc(3, 20, 10)
        val gone = SportEvents.build(listOf(slot("ch_usuniety", utc(3, 21, 17))), followed, index, channels, now, p)
        assertTrue(
            gone
                .single()
                .availability
                .single()
                .abstain,
        )
        assertTrue(
            "karta zostaje, ale bez kanału",
            SportEvents
                .cards(gone, channels, now, p)
                .single()
                .channels
                .isEmpty(),
        )
        val hidden = channels + ("ch_sport_1" to InventoryChannel("ch_sport_1", "Sport Testowy 1", hidden = true))
        val events = SportEvents.build(listOf(slot("ch_sport_1", utc(3, 21, 17))), followed, index, hidden, now, p)
        assertTrue(
            SportEvents
                .cards(events, hidden, now, p)
                .single()
                .channels
                .isEmpty(),
        )
    }

    @Test
    fun niskaPewnoscPowiazaniaKanaluOznaczaWstrzymanieSie() {
        val low = EventAvailability("e", "ch_sport_1", 0.6, false, 0, 1, 0)
        val high = EventAvailability("e", "ch_sport_2", 0.95, false, 0, 1, 0)
        assertEquals(ChannelVerdict.Abstain, SportEvents.channelFor(listOf(low), p.channelThreshold))
        assertEquals(ChannelVerdict.Known(listOf(high)), SportEvents.channelFor(listOf(low, high), p.channelThreshold))
        val unsure = mapOf("ch_sport_1" to InventoryChannel("ch_sport_1", "Sport Testowy 1", mapping = 0.6))
        val events =
            SportEvents.build(
                listOf(slot("ch_sport_1", utc(3, 21, 17))),
                followed,
                index,
                unsure,
                utc(3, 20, 10),
                p,
            )
        assertTrue(
            SportEvents
                .cards(events, unsure, utc(3, 20, 10), p)
                .single()
                .channels
                .isEmpty(),
        )
    }

    @Test
    fun meczeNieobserwowanychDruzynINiejednoznaczneNieTworzaWydarzen() {
        val now = utc(3, 20, 10)
        val other = slot("ch_sport_1", utc(3, 21, 17), "Górnik Zmyślony - Stal Próbna")
        assertTrue(SportEvents.build(listOf(other), followed, index, channels, now, p).isEmpty())
        assertTrue(
            SportEvents
                .build(
                    listOf(slot("ch_sport_1", utc(3, 21, 17))),
                    emptySet(),
                    index,
                    channels,
                    now,
                    p,
                ).isEmpty(),
        )
    }

    @Test
    fun powtorkaJestOsobnoINizej() {
        val now = utc(3, 20, 10)
        val slots =
            listOf(
                slot("ch_sport_1", utc(3, 21, 17)),
                slot("ch_sport_2", utc(3, 21, 22), "Wisła Nadmorska - Hutnik Leśny (powtórka)"),
                slot("ch_sport_2", utc(3, 22, 9)),
            )
        val cards = SportEvents.cards(SportEvents.build(slots, followed, index, channels, now, p), channels, now, p)
        assertEquals(listOf(false, false, true), cards.map { it.replay })
        assertEquals(listOf(utc(3, 21, 17), utc(3, 22, 9), utc(3, 21, 22)), cards.map { it.kickoffUtcMs })
    }

    @Test
    fun pozniejszaEmisjaTejSamejParyTegoSamegoDniaToPowtorka() {
        val now = utc(3, 20, 10)
        val slots = listOf(slot("ch_sport_1", utc(3, 21, 14)), slot("ch_sport_2", utc(3, 21, 19)))
        val events = SportEvents.build(slots, followed, index, channels, now, p)
        assertEquals(listOf(false, true), events.map { it.replay })
    }

    @Test
    fun zmianaGodzinyAktualizujeKarteAStaryTerminZnika() {
        val now = utc(3, 20, 10)
        val before = SportEvents.build(listOf(slot("ch_sport_1", utc(3, 21, 17))), followed, index, channels, now, p)
        val after = SportEvents.build(listOf(slot("ch_sport_1", utc(3, 21, 19, 30))), followed, index, channels, now, p)
        val merged = SportEvents.reconcile(before, after, now)
        assertEquals("ten sam mecz tego samego dnia = ta sama karta", before.single().eventId, after.single().eventId)
        assertEquals(1, merged.size)
        assertEquals(utc(3, 21, 19, 30), merged.single().kickoffUtcMs)
    }

    @Test
    fun meczKtoryZniknalZEpgJestWycofanyAPrzeniesionyNaInnyDzienMaNowaKarte() {
        val now = utc(3, 20, 10)
        val before = SportEvents.build(listOf(slot("ch_sport_1", utc(3, 21, 17))), followed, index, channels, now, p)
        val moved = SportEvents.build(listOf(slot("ch_sport_1", utc(3, 28, 17))), followed, index, channels, now, p)
        val merged = SportEvents.reconcile(before, moved, now)
        assertEquals(setOf(SportEventStatus.SCHEDULED, SportEventStatus.RETRACTED), merged.map { it.status }.toSet())
        assertEquals(listOf(utc(3, 28, 17)), SportEvents.cards(merged, channels, now, p).map { it.kickoffUtcMs })
        val gone = SportEvents.reconcile(before, emptyList(), now)
        assertTrue(SportEvents.cards(gone, channels, now, p).isEmpty())
    }

    @Test
    fun odwolanyIPrzelozonyMajaStatusZamiastGodzinyNaZywo() {
        val now = utc(3, 20, 10)
        val canceled = slot("ch_sport_1", utc(3, 21, 17), "Wisła Nadmorska - Hutnik Leśny (odwołany)")
        val postponed =
            slot("ch_sport_1", utc(3, 22, 17), "Wisła Nadmorska - Stal Próbna", description = "Mecz przełożony.")
        val events = SportEvents.build(listOf(canceled, postponed), followed, index, channels, now, p)
        assertEquals(listOf(SportEventStatus.CANCELED, SportEventStatus.POSTPONED), events.map { it.status })
    }

    @Test
    fun trwajacyMeczJestNaZywoAZakonczonyZnika() {
        val kickoff = utc(3, 21, 17)
        val slots = listOf(slot("ch_sport_1", kickoff))
        val during = SportEvents.build(slots, followed, index, channels, kickoff + 30 * 60_000L, p)
        assertTrue(SportEvents.cards(during, channels, kickoff + 30 * 60_000L, p).single().live)
        val after = kickoff + 3 * PersonalizationParams.HOUR_MS
        assertTrue(
            SportEvents
                .cards(
                    SportEvents.build(slots, followed, index, channels, after, p),
                    channels,
                    after,
                    p,
                ).isEmpty(),
        )
    }

    // ---- czas: UTC w danych, Europe/Warsaw na ekranie ----

    @Test
    fun godzinaWWarszawieZimaILatem() {
        assertEquals("zima: UTC+1", "15:00", WarsawTime.hhmm(utc(1, 15, 14)))
        assertEquals("lato: UTC+2", "16:00", WarsawTime.hhmm(utc(7, 15, 14)))
    }

    @Test
    fun zmianaCzasuWMarcuPrzesuwaTylkoORzeczywistyOffset() {
        assertEquals("15:00", WarsawTime.hhmm(utc(3, 28, 14)))
        assertEquals("16:00", WarsawTime.hhmm(utc(3, 29, 14)))
        assertEquals("ostatnia minuta czasu zimowego", "01:59", WarsawTime.hhmm(utc(3, 29, 0, 59)))
        assertEquals("godzina 2:00 nie istnieje, od razu 3:00", "03:00", WarsawTime.hhmm(utc(3, 29, 1)))
    }

    @Test
    fun zmianaCzasuWPazdziernikuPowtarzaGodzineAleNieGubiKolejnosci() {
        assertEquals("16:00", WarsawTime.hhmm(utc(10, 24, 14)))
        assertEquals("15:00", WarsawTime.hhmm(utc(10, 25, 14)))
        assertEquals("02:30", WarsawTime.hhmm(utc(10, 25, 0, 30)))
        assertEquals("ta sama godzina ścienna godzinę później", "02:30", WarsawTime.hhmm(utc(10, 25, 1, 30)))
        assertTrue(utc(10, 25, 0, 30) < utc(10, 25, 1, 30))
    }

    @Test
    fun dzienLiczonyWWarszawieNieWUtc() {
        // 23:30 UTC 14 lipca to już 01:30 15 lipca w Warszawie.
        assertEquals("15.07", WarsawTime.dayMonth(utc(7, 14, 23, 30)))
        assertEquals(DayKind.TOMORROW, WarsawTime.dayKind(utc(7, 14, 23, 30), utc(7, 14, 12)))
        assertEquals(DayKind.TODAY, WarsawTime.dayKind(utc(7, 14, 20), utc(7, 14, 12)))
        assertEquals(DayKind.OTHER, WarsawTime.dayKind(utc(7, 18, 20), utc(7, 14, 12)))
    }

    @Test
    fun meczWNocZmianyCzasuMaJedenKluczIJednaKarte() {
        val now = utc(10, 24, 10)
        val slots = listOf(slot("ch_sport_1", utc(10, 25, 0, 30)), slot("ch_sport_2", utc(10, 25, 0, 30)))
        val events = SportEvents.build(slots, followed, index, channels, now, p)
        assertEquals(1, events.size)
        assertEquals("02:30", WarsawTime.hhmm(events.single().kickoffUtcMs))
    }

    @Test
    fun nocnaPracaJestPlanowanaWedlugCzasuWarszawskiego() {
        assertEquals(utc(7, 15, 1, 30), WarsawTime.nextAt(utc(7, 14, 12), 3, 30))
        assertEquals(utc(1, 15, 2, 30), WarsawTime.nextAt(utc(1, 14, 12), 3, 30))
        assertEquals("już po 3:30: następnego dnia", utc(7, 15, 1, 30), WarsawTime.nextAt(utc(7, 14, 2), 3, 30))
    }
}

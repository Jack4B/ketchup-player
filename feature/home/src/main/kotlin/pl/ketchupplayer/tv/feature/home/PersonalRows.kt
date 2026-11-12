package pl.ketchupplayer.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.Choice
import pl.ketchupplayer.tv.core.designsystem.ChoiceDialog
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.InfoPill
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.PosterCard
import pl.ketchupplayer.tv.core.designsystem.panel
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.FollowSuggestion
import pl.ketchupplayer.tv.core.model.personalization.SportCard
import pl.ketchupplayer.tv.core.model.personalization.SportEventStatus
import pl.ketchupplayer.tv.core.model.personalization.WhyChip
import pl.ketchupplayer.tv.core.policy.personalization.DayKind
import pl.ketchupplayer.tv.core.policy.personalization.WarsawTime

internal const val TAG_BECAUSE_ROW = "home_because_row"
internal const val TAG_SPORT_ROW = "home_sport_row"
internal const val TAG_FOLLOW_CARD = "home_follow_card"

/** Powód rekomendacji słowami: „Reżyser: Anna Testowa”. Tylko z cech, które naprawdę podniosły pozycję. */
@Composable
private fun whyLabel(chip: WhyChip): String =
    when (chip.namespace) {
        FeatureNamespace.DIRECTOR -> stringResource(R.string.home_why_director, chip.label)
        FeatureNamespace.CAST -> stringResource(R.string.home_why_cast, chip.label)
        else -> stringResource(R.string.home_why_genre, chip.label)
    }

/** Jedna linia pod kartą (mieści się w jej szerokości): najważniejszy powód; pełne uzasadnienie jest w oknie oceny. */
@Composable
private fun cardReason(entry: BecauseEntry): String? =
    when {
        entry.liked -> stringResource(R.string.home_because_liked)
        entry.explore -> stringResource(R.string.home_because_explore)
        else -> entry.why.firstOrNull()?.let { whyLabel(it) }
    }

@Composable
private fun whyText(entry: BecauseEntry): String =
    if (entry.explore) {
        stringResource(R.string.home_because_explore)
    } else {
        entry.why.map { whyLabel(it) }.joinToString(" · ")
    }

/**
 * Pas „Bo oglądasz”: pozycje z katalogu użytkownika z powodem pod tytułem. OK otwiera szczegóły, przytrzymanie OK
 * otwiera ocenę („Lubię to”, „Nie pokazuj tego”) razem z pełnym uzasadnieniem.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun BecauseRow(
    entries: List<BecauseEntry>,
    onOpenDetails: (String) -> Unit,
    actions: PersonalActions,
) {
    val c = KetchupTheme.colors
    var menuFor by remember { mutableStateOf<BecauseEntry?>(null) }
    Column(Modifier.testTag(TAG_BECAUSE_ROW), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(20.dp).background(c.accent, RoundedCornerShape(2.dp)))
            Text(
                stringResource(R.string.home_row_because),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                modifier = Modifier.padding(start = 10.dp, end = 12.dp),
            )
            Text(
                stringResource(R.string.home_because_hint),
                fontSize = 14.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(entries.size.toString(), fontSize = 14.sp, color = c.textSecondary)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.entry.item.id }) { e ->
                PosterCard(
                    e.entry.item.title,
                    { onOpenDetails(e.entry.item.id) },
                    imageUrl = e.entry.item.poster,
                    subtitle = cardReason(e),
                    cardWidth = BECAUSE_CARD_WIDTH,
                    onLongClick = { menuFor = e },
                    ratingBadge = e.entry.ratingLabel(stringResource(R.string.home_rating_provider)),
                )
            }
        }
    }
    menuFor?.let { e ->
        FeedbackDialog(e, onOpenDetails, actions) { menuFor = null }
    }
}

@Composable
private fun FeedbackDialog(
    entry: BecauseEntry,
    onOpenDetails: (String) -> Unit,
    actions: PersonalActions,
    onDismiss: () -> Unit,
) {
    val id = entry.entry.item.id
    val reason =
        if (entry.explore) {
            stringResource(R.string.home_feedback_why_explore)
        } else {
            stringResource(R.string.home_feedback_why, whyText(entry))
        }
    ChoiceDialog(
        title = entry.entry.item.title,
        choices =
            listOf(
                Choice(
                    FEEDBACK_LIKE,
                    stringResource(if (entry.liked) R.string.home_feedback_unlike else R.string.home_feedback_like),
                    reason,
                ),
                Choice(FEEDBACK_HIDE, stringResource(R.string.home_feedback_hide)),
                Choice(FEEDBACK_DETAILS, stringResource(R.string.home_feedback_details)),
            ),
        selectedId = "",
        onSelect = { choice ->
            when (choice) {
                FEEDBACK_LIKE -> actions.onLike(id, !entry.liked)
                FEEDBACK_HIDE -> actions.onHide(id)
                FEEDBACK_DETAILS -> onOpenDetails(id)
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/**
 * Pas „Twoje drużyny”: najbliższe transmisje obserwowanych drużyn według EPG. Godzina zawsze w czasie warszawskim.
 * Karta bez potwierdzonego kanału mówi o tym wprost i prowadzi do Na żywo (nie zgadujemy kanału, ale też nie
 * zostawiamy martwego kafla).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SportRow(
    cards: List<SportCard>,
    nowMs: Long,
    onOpenChannel: (String) -> Unit,
    onOpenGuide: () -> Unit,
) {
    var pickFor by remember { mutableStateOf<SportCard?>(null) }
    Column(Modifier.testTag(TAG_SPORT_ROW), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PersonalHeader(stringResource(R.string.home_row_sport), cards.size)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(cards, key = { it.eventId }) { card ->
                SportEventCard(card, nowMs) {
                    when (card.channels.size) {
                        0 -> onOpenGuide()
                        1 -> onOpenChannel(card.channels.single().channelId)
                        else -> pickFor = card
                    }
                }
            }
        }
    }
    pickFor?.let { card ->
        ChoiceDialog(
            title = stringResource(R.string.home_sport_pick_channel),
            choices = card.channels.map { Choice(it.channelId, it.channelName) },
            selectedId = "",
            onSelect = {
                pickFor = null
                onOpenChannel(it)
            },
            onDismiss = { pickFor = null },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SportEventCard(
    card: SportCard,
    nowMs: Long,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    val status = sportStatus(card)
    FocusItem(onClick, Modifier.width(SPORT_CARD_WIDTH).height(SPORT_CARD_HEIGHT)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Stała wysokość wiersza stanu: karta z plakietką („Na żywo”) jest tak samo wysoka jak bez niej.
            Row(
                Modifier.height(SPORT_STATUS_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status != null) InfoPill(status, highlight = card.live && card.statusNote.isOnSchedule())
                Text(
                    sportWhen(card.kickoffUtcMs, nowMs),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.textPrimary,
                    maxLines = 1,
                )
            }
            Text(
                card.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                // Zawsze miejsce na dwie linie: krótki i długi tytuł dają kartę tej samej wysokości.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (card.channels.isEmpty()) {
                    stringResource(R.string.home_sport_channel_unconfirmed)
                } else {
                    card.channels.joinToString(", ") { it.channelName }
                },
                fontSize = 14.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun SportEventStatus.isOnSchedule() = this == SportEventStatus.SCHEDULED || this == SportEventStatus.LIVE

/** Stan transmisji słowami (nie samym kolorem): odwołanie i zmiana terminu mają pierwszeństwo przed „na żywo”. */
@Composable
private fun sportStatus(card: SportCard): String? =
    when {
        card.statusNote == SportEventStatus.CANCELED -> stringResource(R.string.home_sport_canceled)
        card.statusNote == SportEventStatus.POSTPONED -> stringResource(R.string.home_sport_postponed)
        card.replay -> stringResource(R.string.home_sport_replay)
        card.live -> stringResource(R.string.home_sport_live)
        else -> null
    }

/** „Dziś 20:30”, „Jutro 18:00” albo „sob. 14.03, 15:00”, zawsze w czasie warszawskim. */
@Composable
internal fun sportWhen(
    utcMs: Long,
    nowMs: Long,
): String {
    val time = WarsawTime.hhmm(utcMs)
    return when (WarsawTime.dayKind(utcMs, nowMs)) {
        DayKind.TODAY -> stringResource(R.string.home_sport_today, time)
        DayKind.TOMORROW -> stringResource(R.string.home_sport_tomorrow, time)
        DayKind.OTHER -> {
            val days = stringArrayResource(R.array.home_weekdays_short)
            val day = days.getOrElse(WarsawTime.dayOfWeek(utcMs) - 1) { "" }
            stringResource(R.string.home_sport_date, day, WarsawTime.dayMonth(utcMs), time)
        }
    }
}

/**
 * Pytanie „Obserwować drużynę?” z datami transmisji, które do niego doprowadziły. Aplikacja nigdy nie zaczyna
 * obserwować sama: bez „Obserwuj” nic się nie zmienia, a „Nie, dziękuję” wycisza pytanie.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FollowSuggestionCard(
    suggestion: FollowSuggestion,
    actions: PersonalActions,
) {
    val c = KetchupTheme.colors
    val ids = suggestion.teams.map { it.id }
    val dates = suggestion.watchedDatesUtcMs.joinToString(", ") { WarsawTime.dayMonth(it) }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(TAG_FOLLOW_CARD)
            .panel()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.home_follow_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
        )
        Text(
            stringResource(R.string.home_follow_reason, dates, suggestion.teams.joinToString(", ") { it.name }),
            fontSize = 15.sp,
            color = c.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            suggestion.teams.forEach { team ->
                KetchupButton(
                    stringResource(R.string.home_follow_one, team.name),
                    { actions.onFollow(listOf(team.id)) },
                    primary = suggestion.teams.size == 1,
                )
            }
            if (suggestion.teams.size > 1) {
                KetchupButton(stringResource(R.string.home_follow_both), { actions.onFollow(ids) })
            }
            KetchupButton(stringResource(R.string.home_follow_no), { actions.onDecline(ids) })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PersonalHeader(
    title: String,
    count: Int,
) {
    val c = KetchupTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).background(c.accent, RoundedCornerShape(2.dp)))
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text(count.toString(), fontSize = 14.sp, color = c.textSecondary)
    }
}

private const val FEEDBACK_LIKE = "like"
private const val FEEDBACK_HIDE = "hide"
private const val FEEDBACK_DETAILS = "details"
private val BECAUSE_CARD_WIDTH = 170.dp
private val SPORT_CARD_WIDTH = 300.dp
private val SPORT_CARD_HEIGHT = 136.dp
private val SPORT_STATUS_HEIGHT = 32.dp

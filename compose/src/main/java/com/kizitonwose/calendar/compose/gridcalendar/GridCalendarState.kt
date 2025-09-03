package com.kizitonwose.calendar.compose.gridcalendar

import android.util.Log
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.kizitonwose.calendar.compose.CalendarInfo
import com.kizitonwose.calendar.compose.CalendarState
import com.kizitonwose.calendar.compose.VisibleItemState
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Creates a [CalendarState] that is remembered across compositions.
 *
 * @param startMonth the initial value for [CalendarState.startMonth]
 * @param endMonth the initial value for [CalendarState.endMonth]
 * @param firstDayOfWeek the initial value for [CalendarState.firstDayOfWeek]
 * @param firstVisibleMonth the initial value for [CalendarState.firstVisibleMonth]
 * @param outDateStyle the initial value for [CalendarState.outDateStyle]
 */
@Composable
public fun rememberGridCalendarState(
    startMonth: YearMonth = YearMonth.now(),
    endMonth: YearMonth = startMonth,
    firstVisibleMonth: YearMonth = startMonth,
    firstDayOfWeek: DayOfWeek = firstDayOfWeekFromLocale(),
    outDateStyle: OutDateStyle = OutDateStyle.EndOfRow,
): GridCalendarState {
    return rememberSaveable(
        inputs = arrayOf(
            startMonth,
            endMonth,
            firstVisibleMonth,
            firstDayOfWeek,
            outDateStyle,
        ),
        saver = GridCalendarState.Saver,
    ) {
        GridCalendarState(
            startMonth = startMonth,
            endMonth = endMonth,
            firstDayOfWeek = firstDayOfWeek,
            firstVisibleMonth = firstVisibleMonth,
            outDateStyle = outDateStyle,
            visibleItemState = null,
        )
    }
}

/**
 * A state object that can be hoisted to control and observe calendar properties.
 *
 * This should be created via [rememberCalendarGridState].
 *
 * @param startMonth the first month on the calendar.
 * @param endMonth the last month on the calendar.
 * @param firstDayOfWeek the first day of week on the calendar.
 * @param firstVisibleMonth the initial value for [CalendarState.firstVisibleMonth]
 * @param outDateStyle the preferred style for out date generation.
 */
@Stable
public class GridCalendarState internal constructor(
    startMonth: YearMonth,
    endMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    firstVisibleMonth: YearMonth,
    outDateStyle: OutDateStyle,
    visibleItemState: VisibleItemState?,
) : ScrollableState {
    /** Backing state for [startMonth] */
    private var _startMonth by mutableStateOf(startMonth)

    /** The first month on the calendar. */
    public var startMonth: YearMonth
        get() = _startMonth
        set(value) {
            if (value != startMonth) {
                _startMonth = value
                monthDataChanged()
            }
        }

    /** Backing state for [endMonth] */
    private var _endMonth by mutableStateOf(endMonth)

    /** The last month on the calendar. */
    public var endMonth: YearMonth
        get() = _endMonth
        set(value) {
            if (value != endMonth) {
                _endMonth = value
                monthDataChanged()
            }
        }

    /** Backing state for [firstDayOfWeek] */
    private var _firstDayOfWeek by mutableStateOf(firstDayOfWeek)

    /** The first day of week on the calendar. */
    public var firstDayOfWeek: DayOfWeek
        get() = _firstDayOfWeek
        set(value) {
            if (value != firstDayOfWeek) {
                _firstDayOfWeek = value
                monthDataChanged()
            }
        }

    /** Backing state for [outDateStyle] */
    private var _outDateStyle by mutableStateOf(outDateStyle)

    /** The preferred style for out date generation. */
    public var outDateStyle: OutDateStyle
        get() = _outDateStyle
        set(value) {
            if (value != outDateStyle) {
                _outDateStyle = value
                monthDataChanged()
            }
        }

    /**
     * The first month that is visible.
     *
     * @see [lastVisibleMonth]
     */
    public val firstVisibleMonth: CalendarMonth by derivedStateOf {
        val (_, firstVisibleMonth) = store[gridState.firstVisibleItemIndex] ?: return@derivedStateOf getCalendarMonthData(
            startMonth = startMonth,
            offset = 0,
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = outDateStyle,
        ).calendarMonth
        firstVisibleMonth
    }

    /**
     * The last month that is visible.
     *
     * @see [firstVisibleMonth]
     */
    public val lastVisibleMonth: CalendarMonth by derivedStateOf {
        val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val (_, lastVisibleMonth) = store[lastVisibleIndex] ?: return@derivedStateOf getCalendarMonthData(
            startMonth = startMonth,
            offset = 0,
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = outDateStyle,
        ).calendarMonth
        lastVisibleMonth
    }

    /**
     * The first day that is visible.
     *
     * @see [lastVisibleDay]
     */
    public val firstVisibleDay: CalendarDay? by derivedStateOf {
        val (firstVisibleItem) = store[gridState.firstVisibleItemIndex] ?: return@derivedStateOf null
        when (firstVisibleItem) {
            is GridCalendarItem.Day   -> firstVisibleItem.day
            is GridCalendarItem.Month -> {
                // Find the first day after this month header
                val nextIndex = gridState.firstVisibleItemIndex + 1
                val (nextItem) = store[nextIndex] ?: return@derivedStateOf null
                (nextItem as? GridCalendarItem.Day)?.day
            }
        }
    }

    /**
     * The last day that is visible.
     *
     * @see [firstVisibleDay]
     */
    public val lastVisibleDay: CalendarDay? by derivedStateOf {
        val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val (lastVisibleItem) = store[lastVisibleIndex] ?: return@derivedStateOf null
        when (lastVisibleItem) {
            is GridCalendarItem.Day   -> lastVisibleItem.day
            is GridCalendarItem.Month -> {
                // Find the last day before this month header
                val prevIndex = lastVisibleIndex - 1
                if (prevIndex >= 0) {
                    val (prevItem) = store[prevIndex] ?: return@derivedStateOf null
                    (prevItem as? GridCalendarItem.Day)?.day
                } else null
            }
        }
    }

    /**
     * The first week that is visible, represented as a list of 7 days.
     *
     * @see [lastVisibleWeek]
     */
    public val firstVisibleWeek: List<CalendarDay> by derivedStateOf {
        val firstDay = firstVisibleDay ?: return@derivedStateOf emptyList()

        // Find the month this day belongs to
        val calendarMonth = this.firstVisibleMonth

        // Find which week contains this day
        for (week in calendarMonth.weekDays) {
            if (week.contains(firstDay)) {
                return@derivedStateOf week
            }
        }

        emptyList()
    }

    /**
     * The last week that is visible, represented as a list of 7 days.
     *
     * @see [firstVisibleWeek]
     */
    public val lastVisibleWeek: List<CalendarDay> by derivedStateOf {
        val lastDay = lastVisibleDay ?: return@derivedStateOf emptyList()

        // Find the month this day belongs to
        val calendarMonth = lastVisibleMonth

        // Find which week contains this day
        for (week in calendarMonth.weekDays) {
            if (week.contains(lastDay)) {
                return@derivedStateOf week
            }
        }

        emptyList()
    }

    /**
     * The object of [CalendarLayoutInfo] calculated during the last layout pass. For example,
     * you can use it to calculate what items are currently visible.
     *
     * Note that this property is observable and is updated after every scroll or remeasure.
     * If you use it in the composable function it will be recomposed on every change causing
     * potential performance issues including infinity recomposition loop.
     * Therefore, avoid using it in the composition.
     *
     * If you need to use it in the composition then consider wrapping the calculation into a
     * derived state in order to only have recompositions when the derived value changes.
     * See Example6Page in the sample app for usage.
     *
     * If you want to run some side effects like sending an analytics event or updating a state
     * based on this value consider using "snapshotFlow".
     *
     * see [LazyListLayoutInfo]
     */
    private val layoutInfo: GridCalendarLayoutInfo
        get() = GridCalendarLayoutInfo(gridState.layoutInfo) { index ->
            val (_, month) = store[index] ?: return@GridCalendarLayoutInfo getCalendarMonthData(
                startMonth = startMonth,
                offset = 0,
                firstDayOfWeek = firstDayOfWeek,
                outDateStyle = outDateStyle,
            ).calendarMonth
            month
        }

    /**
     * [InteractionSource] that will be used to dispatch drag events when this
     * calendar is being dragged. If you want to know whether the fling (or animated scroll) is in
     * progress, use [isScrollInProgress].
     */
    public val interactionSource: InteractionSource
        get() = gridState.interactionSource

    public val gridState: LazyGridState = LazyGridState(
        firstVisibleItemIndex = visibleItemState?.firstVisibleItemIndex ?: 0,
        firstVisibleItemScrollOffset = visibleItemState?.firstVisibleItemScrollOffset ?: 0,
    )

    internal var calendarInfo by mutableStateOf(
        CalendarInfo(
            indexCount = 0,
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = outDateStyle,
        ),
    )

    public val allItems: List<GridCalendarItem>
        get() = (0 until calendarInfo.indexCount).mapNotNull { store[it]?.first }

    // Single store for all grid items (indexed by grid position)
    internal val store = DataStore { index ->
        generateGridItem(index)
    }

    private fun generateGridItem(index: Int): Pair<GridCalendarItem, CalendarMonth>? {
        var currentIndex = 0
        for (monthOffset in 0 until calendarInfo.indexCount) {
            val monthData = getCalendarMonthData(
                startMonth = this.startMonth,
                offset = monthOffset,
                firstDayOfWeek = this.firstDayOfWeek,
                outDateStyle = this.outDateStyle,
            )
            val month = monthData.calendarMonth

            // Month header
            if (currentIndex == index) {
                return GridCalendarItem.Month(month) to month
            }
            currentIndex++

            // Days in month
            val days = month.weekDays.flatMap { it }
            for (day in days) {
                if (currentIndex == index) {
                    return GridCalendarItem.Day(day) to month
                }
                currentIndex++
            }
        }
        return null
    }

    init {
        monthDataChanged()
        if (visibleItemState == null && firstVisibleMonth != startMonth) {
            scrollToInitialMonth(firstVisibleMonth)
        }
    }

    private fun monthDataChanged() {
        store.clear()
        checkRange(startMonth, endMonth)
        val monthCount = getMonthIndicesCount(startMonth, endMonth)

        // Calculate total item count (month headers + days)
        var itemCount = 0
        for (monthOffset in 0 until monthCount) {
            val monthData = getCalendarMonthData(
                startMonth = this.startMonth,
                offset = monthOffset,
                firstDayOfWeek = this.firstDayOfWeek,
                outDateStyle = this.outDateStyle,
            )
            itemCount++ // Month header
            itemCount += monthData.calendarMonth.weekDays.flatMap { it }.size // Days
        }
        // itemCount is now stored in calendarInfo.indexCount

        // Read the firstDayOfWeek and outDateStyle properties to ensure recomposition
        // even though they are unused in the CalendarInfo. Alternatively, we could use
        // mutableStateMapOf() as the backing store for DataStore() to ensure recomposition
        // but not sure how compose handles recomposition of a lazy list that reads from
        // such map when an entry unrelated to the visible indices changes.
        calendarInfo = CalendarInfo(
            indexCount = itemCount, // Total grid items, not just months
            firstDayOfWeek = firstDayOfWeek,
            outDateStyle = outDateStyle,
        )
    }

    /**
     * Instantly brings the [month] to the top of the viewport.
     *
     * @param month the month to which to scroll. Must be within the
     * range of [startMonth] and [endMonth].
     *
     * @see [animateScrollToMonth]
     */
    public suspend fun scrollToMonth(month: YearMonth) {
        gridState.scrollToItem(getScrollIndex(month) ?: return)
    }

    internal fun scrollToInitialMonth(month: YearMonth) {
        gridState.requestScrollToItem(getScrollIndex(month) ?: return)
    }

    /**
     * Animate (smooth scroll) to the given [month].
     *
     * @param month the month to which to scroll. Must be within the
     * range of [startMonth] and [endMonth].
     *
     * @see [scrollToMonth]
     */
    public suspend fun animateScrollToMonth(month: YearMonth) {
        gridState.animateScrollToItem(getScrollIndex(month) ?: return)
    }

    /**
     * Instantly brings the [date] to the top of the viewport.
     *
     * @param date the date to which to scroll. Must be within the
     * range of [startMonth] and [endMonth].
     * @param position the position of the date in the month.
     *
     * @see [animateScrollToDate]
     */
    public suspend fun scrollToDate(
        date: LocalDate,
        position: DayPosition,
    ): Unit = scrollToDay(CalendarDay(date, position))

    /**
     * Animate (smooth scroll) to the given [date].
     *
     * @param date the date to which to scroll. Must be within the
     * range of [startMonth] and [endMonth].
     * @param position the position of the date in the month.
     *
     * @see [scrollToDate]
     */
    public suspend fun animateScrollToDate(
        date: LocalDate,
        position: DayPosition,
    ): Unit = animateScrollToDay(CalendarDay(date, position))

    /**
     * Instantly brings the [day] to the top of the viewport.
     *
     * @param day the day to which to scroll. Must be within the
     * range of [startMonth] and [endMonth].
     *
     * @see [animateScrollToDay]
     */
    public suspend fun scrollToDay(day: CalendarDay): Unit =
        scrollToDay(day, animate = false)

    /**
     * Animate (smooth scroll) to the given [day].
     *
     * @param day the day to which to scroll. Must be within the
     * range of [startMonth] and [endMonth].
     *
     * @see [scrollToDay]
     */
    public suspend fun animateScrollToDay(day: CalendarDay): Unit =
        scrollToDay(day, animate = true)

    private suspend fun scrollToDay(day: CalendarDay, animate: Boolean) {
        // Find the day in the store
        for (index in 0 until calendarInfo.indexCount) {
            val (item) = store[index] ?: continue
            if (item is GridCalendarItem.Day && item.day == day) {
                if (animate) {
                    gridState.animateScrollToItem(index)
                } else {
                    gridState.scrollToItem(index)
                }
                return
            }
        }
    }

    private fun getScrollIndex(month: YearMonth): Int? {
        if (month !in startMonth..endMonth) {
            Log.d("CalendarState", "Attempting to scroll out of range: $month")
            return null
        }
        // Find the month header in the store
        for (index in 0 until calendarInfo.indexCount) {
            val (item) = store[index] ?: continue
            if (item is GridCalendarItem.Month && item.month.yearMonth == month) {
                return index
            }
        }
        return null
    }

    /**
     * Whether this [ScrollableState] is currently scrolling by gesture, fling or programmatically.
     */
    override val isScrollInProgress: Boolean
        get() = gridState.isScrollInProgress

    override fun dispatchRawDelta(delta: Float): Float = gridState.dispatchRawDelta(delta)

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ): Unit = gridState.scroll(scrollPriority, block)

    public companion object {
        internal val Saver: Saver<GridCalendarState, Any> = listSaver(
            save = {
                listOf(
                    it.startMonth,
                    it.endMonth,
                    it.firstVisibleMonth.yearMonth,
                    it.firstDayOfWeek,
                    it.outDateStyle,
                    it.gridState.firstVisibleItemIndex,
                    it.gridState.firstVisibleItemScrollOffset,
                )
            },
            restore = {
                GridCalendarState(
                    startMonth = it[0] as YearMonth,
                    endMonth = it[1] as YearMonth,
                    firstVisibleMonth = it[2] as YearMonth,
                    firstDayOfWeek = it[3] as DayOfWeek,
                    outDateStyle = it[4] as OutDateStyle,
                    visibleItemState = VisibleItemState(
                        firstVisibleItemIndex = it[5] as Int,
                        firstVisibleItemScrollOffset = it[6] as Int,
                    ),
                )
            },
        )
    }
}

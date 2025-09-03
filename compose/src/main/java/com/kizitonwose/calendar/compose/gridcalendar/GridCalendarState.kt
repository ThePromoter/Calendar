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
        val state = GridCalendarState(
            startMonth = startMonth,
            endMonth = endMonth,
            firstDayOfWeek = firstDayOfWeek,
            firstVisibleMonth = firstVisibleMonth,
            outDateStyle = outDateStyle,
            visibleItemState = null,
        )
        if (firstVisibleMonth != startMonth) state.scrollToInitialMonth(firstVisibleMonth)
        state
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
    visibleItemState: VisibleItemState?
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
        store[gridState.firstVisibleItemIndex]
    }

    /**
     * The last month that is visible.
     *
     * @see [firstVisibleMonth]
     */
    public val lastVisibleMonth: CalendarMonth by derivedStateOf {
        store[gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0]
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
        get() = GridCalendarLayoutInfo(gridState.layoutInfo) { index -> store[index] }

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

    internal var calendarInfo by mutableStateOf(CalendarInfo(indexCount = 0))

    internal val allMonths: List<GridCalendarItem.Month> get() = (0 until calendarInfo.indexCount)
        .map { monthOffset -> GridCalendarItem.Month(store[monthOffset]) }

    public val allItems: List<GridCalendarItem> get() = allMonths
        .flatMap { monthItem ->
            listOf(
                monthItem,
                *monthItem.month.weekDays.flatMap { it }.map { it.asGridCalendarItem() }.toTypedArray()
            )
        }

    internal val store = DataStore { offset ->
        getCalendarMonthData(
            startMonth = this.startMonth,
            offset = offset,
            firstDayOfWeek = this.firstDayOfWeek,
            outDateStyle = this.outDateStyle,
        ).calendarMonth
    }

    init {
        monthDataChanged() // Update indexCount initially.
    }

    private fun monthDataChanged() {
        store.clear()
        checkRange(startMonth, endMonth)
        // Read the firstDayOfWeek and outDateStyle properties to ensure recomposition
        // even though they are unused in the CalendarInfo. Alternatively, we could use
        // mutableStateMapOf() as the backing store for DataStore() to ensure recomposition
        // but not sure how compose handles recomposition of a lazy list that reads from
        // such map when an entry unrelated to the visible indices changes.
        calendarInfo = CalendarInfo(
            indexCount = getMonthIndicesCount(startMonth, endMonth),
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
        val dayIndex = allItems.indexOfFirstOrNull { it is GridCalendarItem.Day && it.day == day } ?: return
        if (animate) {
            gridState.animateScrollToItem(dayIndex)
        } else {
            gridState.scrollToItem(dayIndex)
        }
    }

    private fun getScrollIndex(month: YearMonth): Int? {
        if (month !in startMonth..endMonth) {
            Log.d("CalendarState", "Attempting to scroll out of range: $month")
            return null
        }
        return allItems.indexOfFirstOrNull { it is GridCalendarItem.Month && it.month.yearMonth == month }
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

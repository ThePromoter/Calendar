package com.kizitonwose.calendar.compose.gridcalendar

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import com.kizitonwose.calendar.compose.CalendarItemInfo
import com.kizitonwose.calendar.core.CalendarMonth

/**
 * Contains useful information about the currently displayed layout state of the calendar.
 * For example you can get the list of currently displayed months.
 *
 * Use [GridCalendarState.layoutInfo] to retrieve this.
 * @see LazyListLayoutInfo
 */
public class GridCalendarLayoutInfo(public val info: LazyGridLayoutInfo, private val month: (Int) -> CalendarMonth) {
    /**
     * The list of [CalendarItemInfo] representing all the currently visible months.
     */
    public val visibleMonthsInfo: List<GridCalendarItemInfo>
        get() = info.visibleItemsInfo.map {
            GridCalendarItemInfo(it, month(it.index))
        }
}

/**
 * Contains useful information about an individual [CalendarMonth] on the calendar.
 *
 * @param month The month in the list.
 *
 * @see GridCalendarLayoutInfo
 * @see LazyListItemInfo
 */
public class GridCalendarItemInfo(public val info: LazyGridItemInfo, public val month: CalendarMonth)

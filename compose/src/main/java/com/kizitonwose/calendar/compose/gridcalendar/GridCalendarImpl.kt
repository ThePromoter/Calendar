package com.kizitonwose.calendar.compose.gridcalendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.kizitonwose.calendar.compose.ContentHeightMode
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth

@Suppress("FunctionName")
internal fun LazyGridScope.GridCalendarItems(
    itemCount: Int,
    itemData: (offset: Int) -> GridCalendarItem?,
    contentHeightMode: ContentHeightMode,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)?,
) {
    val fillHeight = when (contentHeightMode) {
        ContentHeightMode.Wrap -> false
        ContentHeightMode.Fill -> true
    }

    items(
        count = itemCount,
        key = { index ->
            when (val item = itemData(index)) {
                is GridCalendarItem.Month -> "month-${item.month.yearMonth}"
                is GridCalendarItem.Day   -> "day-${item.day.date}-${item.day.position}"
                null                      -> "null-$index"
            }
        },
        span = { index ->
            when (itemData(index)) {
                is GridCalendarItem.Month -> GridItemSpan(maxCurrentLineSpan)
                else                      -> GridItemSpan(1)
            }
        },
        contentType = { index ->
            when (val item = itemData(index)) {
                is GridCalendarItem.Month -> "monthHeader"
                is GridCalendarItem.Day   -> item.day.position
                null                      -> "null"
            }
        },
    ) { index ->
        when (val item = itemData(index)) {
            is GridCalendarItem.Month -> monthHeader?.let {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fillHeight) {
                                Modifier.fillMaxHeight()
                            } else {
                                Modifier.wrapContentHeight()
                            },
                        ),
                ) {
                    monthHeader(this, item.month)
                }
            }
            is GridCalendarItem.Day   -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                dayContent(item.day)
            }
            // Empty item, shouldn't happen in practice
            null                      -> Unit
        }
    }
}

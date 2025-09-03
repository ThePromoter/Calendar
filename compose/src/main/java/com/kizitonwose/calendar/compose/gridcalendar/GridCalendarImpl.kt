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
    monthCount: Int,
    monthData: (offset: Int) -> CalendarMonth,
    contentHeightMode: ContentHeightMode,
    dayContent: @Composable BoxScope.(CalendarDay) -> Unit,
    monthHeader: (@Composable ColumnScope.(CalendarMonth) -> Unit)?,
) {
    (0 until monthCount).map { monthOffset ->
        val month = monthData(monthOffset)
        val fillHeight = when (contentHeightMode) {
            ContentHeightMode.Wrap -> false
            ContentHeightMode.Fill -> true
        }

        monthHeader?.let {
            item(
                key = "header-$month",
                span = { GridItemSpan(maxCurrentLineSpan) },
                contentType = "monthHeader"
            ) {
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
                    monthHeader(this, month)
                }
            }
        }

        val allDaysInMonth = month.weekDays.flatten()
        items(
            count = allDaysInMonth.count(),
            key = { index -> allDaysInMonth[index] },
            contentType = { allDaysInMonth[it].position }
        ) {
            val day = allDaysInMonth[it]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                dayContent(day)
            }
        }
    }
}

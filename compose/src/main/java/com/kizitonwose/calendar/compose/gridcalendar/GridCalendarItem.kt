package com.kizitonwose.calendar.compose.gridcalendar

import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import java.time.LocalDate

public sealed interface GridCalendarItem {
    public data class Month(val month: CalendarMonth) : GridCalendarItem
    public data class Day(val day: CalendarDay) : GridCalendarItem
}

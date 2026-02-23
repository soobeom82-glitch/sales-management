package com.example.vmmswidget.data

import java.time.LocalDate

object HolidayCalendar {
    fun holidaysForYears(years: Set<Int>): Set<LocalDate> {
        val out = mutableSetOf<LocalDate>()
        years.forEach { year ->
            out.addAll(holidaysForYear(year))
        }
        return out
    }

    // 2026 Korea public holidays including substitute holidays and Constitution Day
    fun holidaysForYear(year: Int): Set<LocalDate> {
        if (year != 2026) return emptySet()
        return setOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 16),
            LocalDate.of(2026, 2, 17),
            LocalDate.of(2026, 2, 18),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 2), // substitute
            LocalDate.of(2026, 5, 5),
            LocalDate.of(2026, 5, 24),
            LocalDate.of(2026, 5, 25), // substitute
            LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 6, 6),
            LocalDate.of(2026, 7, 17),
            LocalDate.of(2026, 8, 15),
            LocalDate.of(2026, 8, 17), // substitute
            LocalDate.of(2026, 9, 24),
            LocalDate.of(2026, 9, 25),
            LocalDate.of(2026, 9, 26),
            LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 10, 5), // substitute
            LocalDate.of(2026, 10, 9),
            LocalDate.of(2026, 12, 25)
        )
    }
}

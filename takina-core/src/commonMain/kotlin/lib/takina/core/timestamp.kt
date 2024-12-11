
package lib.takina.core

import kotlinx.datetime.*
import lib.takina.core.exceptions.TakinaException

fun timestampToISO8601(timestamp: Instant): String = buildString {
	timestamp.toLocalDateTime(TimeZone.UTC)
		.let {
			append(
				it.year.toString()
					.padStart(4, '0')
			)
			append("-")
			append(
				it.monthNumber.toString()
					.padStart(2, '0')
			)
			append("-")
			append(
				it.dayOfMonth.toString()
					.padStart(2, '0')
			)
			append("T")
			append(
				it.hour.toString()
					.padStart(2, '0')
			)
			append(":")
			append(
				it.minute.toString()
					.padStart(2, '0')
			)
			append(":")
			append(
				it.second.toString()
					.padStart(2, '0')
			)
			append(".")
			append(
				(it.nanosecond / 1000000).toString()
					.padStart(3, '0')
			)
			append("Z")
		}
}

fun parseISO8601(date: String): Instant {
	val rx =
		"^(\\d{4})-(\\d\\d)-(\\d\\d)([T ](\\d\\d):(\\d\\d):(\\d\\d)(\\.\\d+)?(Z|([+-])(\\d\\d)(:(\\d\\d))?)?)?\$".toRegex()
	val x = rx.find(date) ?: throw TakinaException("Invalid ISO-8601 date.")

	val year = x.groupValues[1].toInt()
	val month = x.groupValues[2].toInt() - 1
	val day = x.groupValues[3].toInt()


	if (x.groupValues[4].isBlank()) {
		return LocalDate(year, Month.values()[month], day).atStartOfDayIn(TimeZone.UTC)
	}

	val hour = x.groupValues[5].toInt()
	val minute = x.groupValues[6].toInt()
	val second = x.groupValues[7].toInt()
	val ms = x.groupValues[8].let {
		if (it.isNotEmpty()) "${it}000".substring(1, 4)
			.toInt() else 0
	}

	if (x.groupValues[9].isBlank()) {
		return LocalDateTime(year, Month.values()[month], day, hour, minute, second, ms).toInstant(TimeZone.UTC)
	}

	return Instant.parse(date)
}

fun String.fromISO8601(): Instant = parseISO8601(this)
/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.scheduling.support;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Locale;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Extension of {@link CronField} for
 * <a href="https://www.quartz-scheduler.org>Quartz</a> -specific fields.
 * Created using the {@code parse*} methods, uses a {@link TemporalAdjuster}
 * internally.
 *
 * @author Arjen Poutsma
 * @since 5.3
 */
final class QuartzCronField extends CronField {

	/**
	 * Temporal adjuster that returns the last weekday of the month.
	 */
	private static final TemporalAdjuster lastWeekdayOfMonth = temporal -> {
		Temporal lastDayOfMonth = TemporalAdjusters.lastDayOfMonth().adjustInto(temporal);
		int dayOfWeek = lastDayOfMonth.get(ChronoField.DAY_OF_WEEK);
		if (dayOfWeek == 6) { // Saturday
			return lastDayOfMonth.minus(1, ChronoUnit.DAYS);
		}
		else if (dayOfWeek == 7) { // Sunday
			return lastDayOfMonth.minus(2, ChronoUnit.DAYS);
		}
		else {
			return lastDayOfMonth;
		}
	};


	private final Type rollOverType;

	private final TemporalAdjuster adjuster;

	private final String value;


	private QuartzCronField(Type type, TemporalAdjuster adjuster, String value) {
		this(type, type, adjuster, value);
	}

	private QuartzCronField(Type type, Type rollOverType, TemporalAdjuster adjuster, String value) {
		super(type);
		this.adjuster = adjuster;
		this.value = value;
		this.rollOverType = rollOverType;
	}


	/**
	 * Parse the given value into a days of months {@code QuartzCronField}, the fourth entry of a cron expression.
	 * Expects a "L" or "W" in the given value.
	 */
	public static QuartzCronField parseDaysOfMonth(String value) {
		int idx = value.lastIndexOf('L');
		if (idx != -1) {
			TemporalAdjuster adjuster;
			if (idx != 0) {
				throw new IllegalArgumentException("Unrecognized characters before 'L' in '" + value + "'");
			}
			else if (value.length() == 2 && value.charAt(1) == 'W') { // "LW"
				adjuster = lastWeekdayOfMonth;
			}
			else {
				if (value.length() == 1) { // "L"
					adjuster = TemporalAdjusters.lastDayOfMonth();
				}
				else { // "L-[0-9]+"
					int offset = Integer.parseInt(value.substring(idx + 1));
					if (offset >= 0) {
						throw new IllegalArgumentException("Offset '" + offset + " should be < 0 '" + value + "'");
					}
					adjuster = lastDayWithOffset(offset);
				}
			}
			return new QuartzCronField(Type.DAY_OF_MONTH, adjuster, value);
		}
		idx = value.lastIndexOf('W');
		if (idx != -1) {
			if (idx == 0) {
				throw new IllegalArgumentException("No day-of-month before 'W' in '" + value + "'");
			}
			else if (idx != value.length() - 1) {
				throw new IllegalArgumentException("Unrecognized characters after 'W' in '" + value + "'");
			}
			else { // "[0-9]+W"
				int dayOfMonth = Integer.parseInt(value.substring(0, idx));
				dayOfMonth = Type.DAY_OF_MONTH.checkValidValue(dayOfMonth);
				TemporalAdjuster adjuster = weekdayNearestTo(dayOfMonth);
				return new QuartzCronField(Type.DAY_OF_MONTH, adjuster, value);
			}
		}
		throw new IllegalArgumentException("No 'L' or 'W' found in '" + value + "'");
	}

	/**
	 * Parse the given value into a days of week {@code QuartzCronField}, the sixth entry of a cron expression.
	 * Expects a "L" or "#" in the given value.
	 */
	public static QuartzCronField parseDaysOfWeek(String value) {
		int idx = value.lastIndexOf('L');
		if (idx != -1) {
			if (idx != value.length() - 1) {
				throw new IllegalArgumentException("Unrecognized characters after 'L' in '" + value + "'");
			}
			else {
				TemporalAdjuster adjuster;
				if (idx == 0) {
					adjuster = lastDayOfWeek(Locale.getDefault());
				}
				else {
					DayOfWeek dayOfWeek = parseDayOfWeek(value.substring(0, idx));
					adjuster = TemporalAdjusters.lastInMonth(dayOfWeek);
				}
				return new QuartzCronField(Type.DAY_OF_WEEK, Type.DAY_OF_MONTH, adjuster, value);
			}
		}
		idx = value.lastIndexOf('#');
		if (idx != -1) {
			if (idx == 0) {
				throw new IllegalArgumentException("No day-of-week before '#' in '" + value + "'");
			}
			else if (idx == value.length() - 1) {
				throw new IllegalArgumentException("No ordinal after '#' in '" + value + "'");
			}
			DayOfWeek dayOfWeek = parseDayOfWeek(value.substring(0, idx));
			int ordinal = Integer.parseInt(value.substring(idx + 1));

			TemporalAdjuster adjuster = TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek);
			return new QuartzCronField(Type.DAY_OF_WEEK, Type.DAY_OF_MONTH, adjuster, value);
		}
		throw new IllegalArgumentException("No 'L' or '#' found in '" + value + "'");
	}


	private static DayOfWeek parseDayOfWeek(String value) {
		int dayOfWeek = Integer.parseInt(value);
		if (dayOfWeek == 0) {
			dayOfWeek = 7; // cron is 0 based; java.time 1 based
		}
		try {
			return DayOfWeek.of(dayOfWeek);
		}
		catch (DateTimeException ex) {
			String msg = ex.getMessage() + " '" + value + "'";
			throw new IllegalArgumentException(msg, ex);
		}
	}

	/**
	 * Return a temporal adjuster that finds the nth-to-last day of the month.
	 * @param offset the negative offset, i.e. -3 means third-to-last
	 * @return a nth-to-last day-of-month adjuster
	 */
	private static TemporalAdjuster lastDayWithOffset(int offset) {
		Assert.isTrue(offset < 0, "Offset should be < 0");
		return temporal -> {
			Temporal lastDayOfMonth = TemporalAdjusters.lastDayOfMonth().adjustInto(temporal);
			return lastDayOfMonth.plus(offset, ChronoUnit.DAYS);
		};
	}

	/**
	 * Return a temporal adjuster that finds the last day-of-week, depending
	 * on the given locale.
	 * @param locale the locale to base the last day calculation on
	 * @return the last day-of-week adjuster
	 */
	private static TemporalAdjuster lastDayOfWeek(Locale locale) {
		Assert.notNull(locale, "Locale must not be null");
		TemporalField dayOfWeek = WeekFields.of(locale).dayOfWeek();
		return temporal -> temporal.with(dayOfWeek, 7);
	}

	/**
	 * Return a temporal adjuster that finds the weekday nearest to the given
	 * day-of-month. If {@code dayOfMonth} falls on a Saturday, the date is
	 * moved back to Friday; if it falls on a Sunday (or if {@code dayOfMonth}
	 * is 1 and it falls on a Saturday), it is moved forward to Monday.
	 * @param dayOfMonth the goal day-of-month
	 * @return the weekday-nearest-to adjuster
	 */
	private static TemporalAdjuster weekdayNearestTo(int dayOfMonth) {
		return temporal -> {
			int current = Type.DAY_OF_MONTH.get(temporal);
			int dayOfWeek = temporal.get(ChronoField.DAY_OF_WEEK);

			if ((current == dayOfMonth && dayOfWeek < 6) || // goal is a weekday
					(dayOfWeek == 5 && current == dayOfMonth - 1) || // goal is a Saturday, so Friday before
					(dayOfWeek == 1 && current == dayOfMonth + 1) || // goal is a Sunday, so Monday after
					(dayOfWeek == 1 && dayOfMonth == 1 && current == 3)) { // // goal is the 1st, so Monday 3rd
				return temporal;
			}
			int count = 0;
			while (count++ < CronExpression.MAX_ATTEMPTS) {
				temporal = Type.DAY_OF_MONTH.elapseUntil(temporal, dayOfMonth);
				current = Type.DAY_OF_MONTH.get(temporal);
				if (current == dayOfMonth) {
					dayOfWeek = temporal.get(ChronoField.DAY_OF_WEEK);

					if (dayOfWeek == 6) { // Saturday
						if (dayOfMonth != 1) {
							return temporal.minus(1, ChronoUnit.DAYS);
						}
						else {
							// exception for "1W" fields: execute on nearest Monday
							return temporal.plus(2, ChronoUnit.DAYS);
						}
					}
					else if (dayOfWeek == 7) { // Sunday
						return temporal.plus(1, ChronoUnit.DAYS);
					}
					else {
						return temporal;
					}
				}
			}
			return null;
		};
	}


	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public <T extends Temporal> T nextOrSame(T temporal) {
		T result = (T) this.adjuster.adjustInto(temporal);
		if (result instanceof Comparable) {
			Comparable<Temporal> comparable = (Comparable<Temporal>) result;
			if (comparable.compareTo(temporal) < 0) {
				// We ended up before the start, roll forward and try again
				temporal = this.rollOverType.rollForward(temporal);
				result = (T) this.adjuster.adjustInto(temporal);
			}
		}
		return result;
	}


	@Override
	public int hashCode() {
		return this.value.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof QuartzCronField)) {
			return false;
		}
		QuartzCronField other = (QuartzCronField) o;
		return type() == other.type() &&
				this.value.equals(other.value);
	}

	@Override
	public String toString() {
		return type() + " '" + this.value + "'";

	}

}

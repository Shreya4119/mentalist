package com.hrragassistant.backend.companies;

import java.time.LocalDate;

/** One company in a country's list: its name and the year end of its latest annual filing. */
public record DirectoryEntry(String lei, String name, String country, LocalDate lastPeriodEnd) {
}

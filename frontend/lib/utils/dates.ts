/**
 * Timezone-safe calendar date utilities.
 * Handles ISO date strings (YYYY-MM-DD) without timezone conversion shifts.
 */

const MONTH_NAMES = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
];

/**
 * Parses a YYYY-MM-DD string into year, month (1-12), and day components
 */
export function parseCalendarDate(dateStr?: string | null): { year: number; month: number; day: number } | null {
  if (!dateStr || typeof dateStr !== 'string') return null;
  const match = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) return null;
  return {
    year: parseInt(match[1], 10),
    month: parseInt(match[2], 10),
    day: parseInt(match[3], 10)
  };
}

/**
 * Formats a YYYY-MM-DD string as "MMM D, YYYY" (e.g. "Jun 14, 2026")
 */
export function formatCalendarDate(dateStr?: string | null): string {
  const parsed = parseCalendarDate(dateStr);
  if (!parsed) return '—';
  const monthName = MONTH_NAMES[parsed.month - 1] || '';
  return `${monthName} ${parsed.day}, ${parsed.year}`;
}

/**
 * Formats a date range cleanly: "Jun 10 → Jun 14, 2026" or "Jun 10, 2026 → Jun 14, 2027"
 */
export function formatDateRange(startStr?: string | null, dueStr?: string | null): string {
  const start = parseCalendarDate(startStr);
  const due = parseCalendarDate(dueStr);

  if (!start && !due) return '—';
  if (start && !due) return formatCalendarDate(startStr);
  if (!start && due) return `Due ${formatCalendarDate(dueStr)}`;

  if (start && due) {
    const startMonth = MONTH_NAMES[start.month - 1];
    const dueMonth = MONTH_NAMES[due.month - 1];

    if (start.year === due.year) {
      if (start.month === due.month && start.day === due.day) {
        return `${startMonth} ${start.day}, ${start.year}`;
      }
      return `${startMonth} ${start.day} → ${dueMonth} ${due.day}, ${start.year}`;
    }
    return `${startMonth} ${start.day}, ${start.year} → ${dueMonth} ${due.day}, ${due.year}`;
  }

  return '—';
}

/**
 * Validates a YYYY-MM-DD calendar date string
 */
export function isValidCalendarDate(dateStr?: string): boolean {
  if (!dateStr) return false;
  return /^\d{4}-\d{2}-\d{2}$/.test(dateStr);
}

/**
 * Returns today's date formatted as YYYY-MM-DD using local calendar day
 */
export function getTodayCalendarDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

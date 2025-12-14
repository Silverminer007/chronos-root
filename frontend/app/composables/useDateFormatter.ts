import {DateTime} from "luxon"

export function useDateFormatter() {
    function fromApi(iso: string): DateTime {
        return DateTime.fromISO(iso, {zone: "utc"}).toLocal().setLocale("de")
    }

    function formatTimeRange(start: string, end: string): string {
        const startDateTime = fromApi(start);
        const endDateTime = fromApi(end);

        if (startDateTime.hasSame(endDateTime, 'day')) {
            return `${startDateTime.toFormat('EEE D t')} - ${endDateTime.toFormat('HH:mm')} Uhr`;
        }
        return `${startDateTime.toFormat('EEE D t')} Uhr - ${endDateTime.toFormat('EEE D t')} Uhr`;
    }

    function formatDate(date: string): string {
        const dateTime = fromApi(date);

        return dateTime.toFormat('D');
    }

    return {
        formatTimeRange,
        formatDate
    }
}
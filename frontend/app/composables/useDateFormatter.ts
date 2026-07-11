import {DateTime} from "luxon"

export function useDateFormatter() {
    function formatTimeRange(start: string, end: string): string {
        const startDateTime = DateTime.fromISO(start);
        const endDateTime = DateTime.fromISO(end);

        if (startDateTime.hasSame(endDateTime, 'day')) {
            return `${startDateTime.toFormat('EEE D t')} - ${endDateTime.toFormat('HH:mm')} Uhr`;
        }
        return `${startDateTime.toFormat('EEE D t')} Uhr - ${endDateTime.toFormat('EEE D t')} Uhr`;
    }

    function formatDate(date: string): string {
        const dateTime = DateTime.fromISO(date);

        return dateTime.toFormat('D');
    }

    function formatTime(date: string): string {
        const dateTime = DateTime.fromISO(date);

        return `${dateTime.toFormat('t')} Uhr`;
    }

    function formatDateTime(date: string): string {
        const dateTime = DateTime.fromISO(date);

        return `${dateTime.toFormat('D t')} Uhr`;
    }

    return {
        formatTimeRange,
        formatDate,
        formatTime,
        formatDateTime
    }
}
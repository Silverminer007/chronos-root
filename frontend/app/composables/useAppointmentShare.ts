import type {Appointment} from "~/types";
import {useToast} from "primevue/usetoast";
import {useDateFormatter} from "~/composables/useDateFormatter";

export function useAppointmentShare() {
    const toast = useToast();
    const {formatDateTime} = useDateFormatter();

    function buildShareText(appointment: Appointment, url: string): string {
        const approvedCount = appointment.participants.filter(p => p.status === "APPROVED").length;
        const rejectedCount = appointment.participants.filter(p => p.status === "REJECTED").length;

        const lines: string[] = [
            `*${appointment.name}*`
        ];

        if (appointment.description) {
            lines.push(`Beschreibung: ${appointment.description}`);
        }

        lines.push(`Start: ${formatDateTime(appointment.start)}`);
        lines.push(`Ende: ${formatDateTime(appointment.end)}`);

        if (appointment.venue) {
            lines.push(`Ort: ${appointment.venue}`);
        }

        lines.push("");
        lines.push("*Bisherige Rückmeldungen*:");
        lines.push(`Zusagen: ${approvedCount}`);
        lines.push(`Absagen: ${rejectedCount}`);
        lines.push(`Teilnehmer insgesamt: ${appointment.participants.length}`);

        if (appointment.minimal_attendees) {
            lines.push(`Mindest Teilnehmer: ${appointment.minimal_attendees}`);
        }

        lines.push("");
        lines.push(`Termindetails & Rückmeldung geben: ${url}`);

        return lines.join("\n");
    }

    async function shareAppointment(appointment: Appointment) {
        const url = `${window.location.origin}/appointment/${appointment.id}`;
        const text = buildShareText(appointment, url);

        if (navigator.share) {
            try {
                await navigator.share({
                    title: appointment.name,
                    text
                });
            } catch (err) {
                if ((err as DOMException).name !== 'AbortError') {
                    toast.add({
                        severity: 'error',
                        summary: 'Fehler',
                        detail: 'Teilen fehlgeschlagen',
                        life: 3000
                    });
                }
            }
        } else {
            try {
                await navigator.clipboard.writeText(text);
                toast.add({
                    severity: 'success',
                    summary: 'Link kopiert',
                    detail: 'Der Termin-Link wurde in die Zwischenablage kopiert',
                    life: 3000
                });
            } catch {
                toast.add({
                    severity: 'error',
                    summary: 'Fehler',
                    detail: 'Link konnte nicht kopiert werden',
                    life: 3000
                });
            }
        }
    }

    return {
        buildShareText,
        shareAppointment
    }
}
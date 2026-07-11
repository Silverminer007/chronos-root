export const DURATION_CHIPS = [
  { label: '30 Min', minutes: 30 },
  { label: '1 Std', minutes: 60 },
  { label: '1,5 Std', minutes: 90 },
  { label: '2 Std', minutes: 120 },
  { label: '6 Std', minutes: 360 },
  { label: 'Ganztägig', minutes: 1440 },
  { label: '2 Tage', minutes: 2880 },
  { label: '14 Tage', minutes: 20160 },
]

export const DURATION_UNITS = [
  { label: 'Minuten', value: 'minutes' as const, multiplier: 1 },
  { label: 'Stunden', value: 'hours' as const, multiplier: 60 },
  { label: 'Tage', value: 'days' as const, multiplier: 1440 },
]

export function toDatetimeLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

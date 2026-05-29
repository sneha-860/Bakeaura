export function currency(value) {
  const amount = Number(value || 0);
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: amount % 1 === 0 ? 0 : 2
  }).format(amount);
}

export function formatDate(value) {
  if (!value) return 'Not available';
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

export function titleCase(value = '') {
  return String(value).replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function unwrapPage(payload) {
  if (Array.isArray(payload)) return { items: payload, totalPages: 1, number: 0 };
  return {
    items: payload?.content ?? [],
    totalPages: payload?.totalPages ?? 1,
    number: payload?.number ?? 0
  };
}

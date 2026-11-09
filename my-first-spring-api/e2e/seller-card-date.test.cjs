const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/seller.js'), 'utf8');
const context = vm.createContext({
  document: { addEventListener() {} },
  window: { addEventListener() {} },
  setTimeout() {},
});
vm.runInContext(script, context);

const now = new Date('2026-11-09T06:00:00Z');
const delivery = (readyByTime, extra = {}) => context.sellerDeliveryLabel({ readyByTime, ...extra }, now);
const close = (product, clock = now) => context.sellerOrdersCloseLabel(product, clock);

test('delivery labels use Kolkata calendar days and 12-hour time', () => {
  assert.equal(delivery('2026-11-09T20:30'), 'Today · 8:30 PM');
  assert.equal(delivery('2026-11-10T13:00'), 'Tomorrow · 1:00 PM');
  assert.equal(delivery('2026-11-11T01:05'), 'Wednesday, 11 Nov · 1:05 AM');
  assert.equal(delivery('2026-11-16T00:00'), 'Monday, 16 Nov · 12:00 AM');
  assert.equal(delivery('2026-11-17T12:00'), '17 Nov 2026 · 12:00 PM');
  assert.equal(delivery('2026-11-20T13:00'), '20 Nov 2026 · 1:00 PM');
  assert.equal(delivery('13:00', { availableDate: '2026-11-10' }), 'Tomorrow · 1:00 PM');
  assert.equal(delivery('13:00'), '1:00 PM');
});

test('instants and current day are compared in Kolkata across midnight', () => {
  const beforeKolkataMidnight = new Date('2026-11-09T18:29:00Z');
  const afterKolkataMidnight = new Date('2026-11-09T18:30:00Z');
  assert.equal(context.sellerDeliveryLabel({ readyByTime: '2026-11-09T18:30:00Z' }, beforeKolkataMidnight), 'Tomorrow · 12:00 AM');
  assert.equal(context.sellerDeliveryLabel({ readyByTime: '2026-11-09T18:30:00Z' }, afterKolkataMidnight), 'Today · 12:00 AM');
  assert.equal(delivery('2026-11-10T07:30:00+05:30'), 'Tomorrow · 7:30 AM');
});

test('cutoff labels use the effective delivery or occurrence date, including previous day', () => {
  assert.equal(close({ availableDate: '2026-11-09', orderWindowEnd: '19:00', readyByTime: '2026-11-09T20:30' }), 'today at 7:00 PM');
  assert.equal(close({ availableDate: '2026-11-10', cutoffTime: '12:00', readyByTime: '2026-11-10T13:00' }), 'tomorrow at 12:00 PM');
  assert.equal(close({ availableDate: '2026-11-11', cutoffTime: '19:00', readyByTime: '2026-11-11T20:00' }), 'Wednesday, 11 Nov at 7:00 PM');
  assert.equal(close({ availableDate: '2026-11-20', cutoffTime: '07:00', readyByTime: '2026-11-20T08:00' }), '20 Nov 2026 at 7:00 AM');
  assert.equal(close({ availableDate: '2026-11-10', cutoffTime: '23:30', readyByTime: '2026-11-10T00:30' }), 'today at 11:30 PM');
  assert.equal(close({ availableDate: '2026-11-09', cutoffTime: '23:30', readyByTime: '2026-11-09T00:30' }), 'today at 12:30 AM');
  assert.equal(close({ availableDate: '2026-11-10', cutoffTime: '19:00', readyByTime: '1:30 PM today' }), 'today at 7:00 PM');
  assert.equal(close({ availableDate: '2026-11-10', cutoffTime: '19:00', readyByTime: '1:30 PM tomorrow' }), 'tomorrow at 7:00 PM');
  assert.equal(close({ availableDate: '2026-11-10', cutoffTime: '19:00', readyByTime: '1:30 PM Tuesday' }), 'tomorrow at 7:00 PM');
  assert.equal(close({ availableDate: '2026-11-10', cutoffTime: '19:00', readyByTime: '1:30 PM Tuesday' }, new Date('2026-11-17T06:00:00Z')), '10 Nov 2026 at 7:00 PM');
  assert.equal(close({ recurring: true, availableDate: '2026-11-09', nextOccurrenceDate: '2026-11-11', orderWindowEnd: '19:00', cutoffTime: '18:00', readyByTime: '2026-11-11T20:00' }), 'Wednesday, 11 Nov at 7:00 PM');
  assert.equal(close({ cutoffTime: '19:00' }), 'at 7:00 PM');
});

test('missing and invalid timestamps are safe while legacy availability text is retained', () => {
  assert.equal(delivery(null), '--');
  assert.equal(delivery('2026-02-30T12:00'), '--');
  assert.equal(delivery('2026-11-09T25:00'), '--');
  assert.equal(delivery('99:99'), '--');
  assert.equal(delivery('13:00', { availableDate: 'invalid' }), '--');
  assert.equal(delivery('Usually ready in 1 day'), 'Usually ready in 1 day');
  assert.equal(delivery('1:30 PM Monday'), '1:30 PM Monday');
  assert.equal(close({}), '--');
  assert.equal(close({ availableDate: '2026-02-30', cutoffTime: '19:00' }), '--');
  assert.equal(close({ availableDate: '2026-11-09', cutoffTime: '25:00' }), '--');
});

test('both dashboard card variants use the delivery formatter', () => {
  context.isHomemadeProduct = p => p.homemade;
  context.foodEmoji = () => 'food';
  context.esc = String;
  const product = { id: 1, name: 'Food', readyByTime: '2026-11-10T13:00', availableDate: '2026-11-10', cutoffTime: '12:00' };
  const card = context.sdOfferingCardHtml(product, null);
  assert.match(card, /Orders close <span class="sd-tv">(?:today|tomorrow|[^<]+) at 12:00 PM/);
  assert.doesNotMatch(card, /2026-11-10T13:00/);
  const homemade = context.sdOfferingCardHtml({ ...product, homemade: true, cutoffTime: null }, null);
  assert.match(homemade, /Availability <span class="sd-tv">[^<]*1:00 PM/);
  assert.doesNotMatch(homemade, /2026-11-10T13:00/);
});

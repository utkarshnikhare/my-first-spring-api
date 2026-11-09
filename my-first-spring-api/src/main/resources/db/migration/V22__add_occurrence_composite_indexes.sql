CREATE INDEX IF NOT EXISTS idx_occurrences_schedule_date ON occurrences (schedule_id, occurrence_date);
CREATE INDEX IF NOT EXISTS idx_occurrences_date_status ON occurrences (occurrence_date, status);

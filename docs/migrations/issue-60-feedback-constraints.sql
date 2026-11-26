-- Run with psql -v ON_ERROR_STOP=1 after a private backup, before enabling customer feedback.
-- Existing columns and data remain compatible with older images. No ratings are rewritten.
BEGIN;
LOCK TABLE feedback IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM feedback WHERE foodRating NOT BETWEEN 1 AND 5
               OR overAllExperienceRating NOT BETWEEN 1 AND 5 OR courierRating NOT BETWEEN 1 AND 5
               OR foodRating IS NULL OR overAllExperienceRating IS NULL OR courierRating IS NULL
               OR delivery_id IS NULL) THEN
        RAISE EXCEPTION 'Existing feedback requires investigation; no data was changed';
    END IF;
    IF EXISTS (SELECT 1 FROM feedback GROUP BY delivery_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate delivery feedback requires investigation; no data was changed';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'feedback'::regclass AND conname = 'ck_feedback_ratings') THEN
        ALTER TABLE feedback ADD CONSTRAINT ck_feedback_ratings CHECK (
            foodRating BETWEEN 1 AND 5 AND overAllExperienceRating BETWEEN 1 AND 5
            AND courierRating BETWEEN 1 AND 5);
    END IF;
    -- Hibernate's existing OneToOne normally already supplies this constraint.
    IF NOT EXISTS (SELECT 1 FROM pg_index WHERE indrelid = 'feedback'::regclass
                   AND indisunique AND indpred IS NULL AND indnkeyatts = 1
                   AND indkey[0] = (SELECT attnum FROM pg_attribute
                                   WHERE attrelid = 'feedback'::regclass AND attname = 'delivery_id')) THEN
        CREATE UNIQUE INDEX uq_feedback_delivery ON feedback (delivery_id);
    END IF;
END $$;

COMMIT;

CREATE SEQUENCE season_number_seq;

SELECT setval(
    'season_number_seq',
    COALESCE((SELECT MAX(season_number) FROM seasons), 0) + 1,
    false
);

CREATE UNIQUE INDEX uq_seasons_single_active
    ON seasons (status)
    WHERE status = 'ACTIVE';

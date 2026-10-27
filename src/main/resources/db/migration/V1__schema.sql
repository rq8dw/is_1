-- Вспомогательные объекты хранятся в отдельных таблицах, чтобы новую организацию
-- можно было связать с уже существующими координатами/адресами.

CREATE TABLE coordinates
(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    x  DOUBLE PRECISION NOT NULL,
    y  DOUBLE PRECISION NOT NULL,
    CONSTRAINT coordinates_id_positive CHECK (id > 0),
    -- NaN и +Infinity в PostgreSQL больше любого числа, поэтому тоже отсекаются
    CONSTRAINT coordinates_x_max CHECK (x <= 549),
    CONSTRAINT coordinates_x_finite CHECK (x <> '-Infinity'::DOUBLE PRECISION),
    CONSTRAINT coordinates_y_finite CHECK (y NOT IN ('NaN'::DOUBLE PRECISION,
                                                     'Infinity'::DOUBLE PRECISION,
                                                     '-Infinity'::DOUBLE PRECISION))
);

CREATE TABLE address
(
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    zip_code VARCHAR(255) NOT NULL,
    CONSTRAINT address_id_positive CHECK (id > 0)
);

CREATE TABLE organization
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(255)             NOT NULL,
    coordinates_id      BIGINT                   NOT NULL
        CONSTRAINT organization_coordinates_fk REFERENCES coordinates (id) ON DELETE CASCADE,
    creation_date       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    official_address_id BIGINT
        CONSTRAINT organization_official_address_fk REFERENCES address (id) ON DELETE CASCADE,
    annual_turnover     BIGINT,
    employees_count     BIGINT                   NOT NULL,
    rating              REAL,
    full_name           VARCHAR(255)             NOT NULL,
    type                VARCHAR(32)              NOT NULL,
    postal_address_id   BIGINT
        CONSTRAINT organization_postal_address_fk REFERENCES address (id) ON DELETE CASCADE,
    CONSTRAINT organization_id_positive CHECK (id > 0),
    CONSTRAINT organization_name_not_empty CHECK (btrim(name) <> ''),
    CONSTRAINT organization_annual_turnover_positive CHECK (annual_turnover > 0),
    CONSTRAINT organization_employees_count_positive CHECK (employees_count > 0),
    CONSTRAINT organization_rating_positive CHECK (rating > 0 AND rating <> 'NaN'::REAL AND rating <> 'Infinity'::REAL),
    CONSTRAINT organization_type_valid CHECK (type IN ('GOVERNMENT', 'TRUST', 'PRIVATE_LIMITED_COMPANY'))
);

CREATE INDEX organization_coordinates_idx ON organization (coordinates_id);
CREATE INDEX organization_official_address_idx ON organization (official_address_id);
CREATE INDEX organization_postal_address_idx ON organization (postal_address_id);
CREATE INDEX organization_name_idx ON organization (name);
CREATE INDEX organization_full_name_idx ON organization (full_name);
CREATE INDEX organization_type_idx ON organization (type);

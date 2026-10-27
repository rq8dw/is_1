-- Специальные операции над организациями. Вызываются из слоя бизнес-логики
-- (ru.itmo.is.lab1.repository.SpecialOperationsRepository).

-- Порядковый номер типа в порядке объявления Java-enum OrganizationType.
CREATE OR REPLACE FUNCTION organization_type_rank(p_type VARCHAR)
    RETURNS INTEGER
    LANGUAGE sql
    IMMUTABLE
AS
$$
SELECT array_position(ARRAY ['GOVERNMENT', 'TRUST', 'PRIVATE_LIMITED_COMPANY']::VARCHAR[], p_type)
$$;

-- 1. Количество организаций, у которых type равен заданному.
CREATE OR REPLACE FUNCTION count_organizations_by_type(p_type VARCHAR)
    RETURNS BIGINT
    LANGUAGE plpgsql
    STABLE
AS
$$
BEGIN
    IF organization_type_rank(p_type) IS NULL THEN
        RAISE EXCEPTION 'Неизвестный тип организации: %', p_type USING ERRCODE = '22023';
    END IF;
    RETURN (SELECT count(*) FROM organization WHERE type = p_type);
END;
$$;

-- 2. Количество организаций, у которых fullName больше заданного (сравнение строк).
CREATE OR REPLACE FUNCTION count_organizations_full_name_greater(p_full_name VARCHAR)
    RETURNS BIGINT
    LANGUAGE plpgsql
    STABLE
AS
$$
BEGIN
    IF p_full_name IS NULL THEN
        RAISE EXCEPTION 'Значение fullName для сравнения не задано' USING ERRCODE = '22004';
    END IF;
    RETURN (SELECT count(*) FROM organization WHERE full_name > p_full_name);
END;
$$;

-- 3. Организации, у которых type меньше заданного (по порядку объявления enum).
CREATE OR REPLACE FUNCTION find_organizations_type_less_than(p_type VARCHAR)
    RETURNS SETOF organization
    LANGUAGE plpgsql
    STABLE
AS
$$
DECLARE
    v_rank INTEGER := organization_type_rank(p_type);
BEGIN
    IF v_rank IS NULL THEN
        RAISE EXCEPTION 'Неизвестный тип организации: %', p_type USING ERRCODE = '22023';
    END IF;
    RETURN QUERY
        SELECT o.*
        FROM organization o
        WHERE organization_type_rank(o.type) < v_rank
        ORDER BY o.id;
END;
$$;

-- 4. Добавить сотрудника в организацию. Возвращает новое число сотрудников.
CREATE OR REPLACE FUNCTION add_organization_employee(p_organization_id BIGINT)
    RETURNS BIGINT
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_count BIGINT;
BEGIN
    UPDATE organization
    SET employees_count = employees_count + 1
    WHERE id = p_organization_id
    RETURNING employees_count INTO v_count;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Организация с id = % не найдена', p_organization_id USING ERRCODE = 'P0002';
    END IF;
    RETURN v_count;
END;
$$;

-- 5. Поглощение: все сотрудники (и годовой оборот) поглощаемой организации переходят
--    к поглощающей, поглощаемая организация удаляется. Возвращает новое число сотрудников.
CREATE OR REPLACE FUNCTION absorb_organization(p_absorber_id BIGINT, p_absorbed_id BIGINT)
    RETURNS BIGINT
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_absorbed organization%ROWTYPE;
    v_count    BIGINT;
BEGIN
    IF p_absorber_id = p_absorbed_id THEN
        RAISE EXCEPTION 'Организация не может поглотить саму себя' USING ERRCODE = '22023';
    END IF;

    -- Блокируем обе строки в одном порядке, чтобы параллельные поглощения не взаимоблокировались
    PERFORM 1 FROM organization WHERE id IN (p_absorber_id, p_absorbed_id) ORDER BY id FOR UPDATE;

    SELECT * INTO v_absorbed FROM organization WHERE id = p_absorbed_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Поглощаемая организация с id = % не найдена', p_absorbed_id USING ERRCODE = 'P0002';
    END IF;

    UPDATE organization
    SET employees_count = employees_count + v_absorbed.employees_count,
        annual_turnover = CASE
                              WHEN annual_turnover IS NULL AND v_absorbed.annual_turnover IS NULL THEN NULL
                              ELSE coalesce(annual_turnover, 0) + coalesce(v_absorbed.annual_turnover, 0)
            END
    WHERE id = p_absorber_id
    RETURNING employees_count INTO v_count;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Поглощающая организация с id = % не найдена', p_absorber_id USING ERRCODE = 'P0002';
    END IF;

    DELETE FROM organization WHERE id = p_absorbed_id;
    RETURN v_count;
END;
$$;

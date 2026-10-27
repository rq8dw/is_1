-- «fullName больше заданного»: строки сравниваются побайтово (COLLATE "C"), а не по правилам локали БД.
-- На studs локаль ru_RU.UTF-8, в ней регистр и пробелы при сравнении почти не учитываются,
-- и результат зависел бы от настроек сервера. Порядок "C" для UTF-8 совпадает с порядком
-- кодовых точек Unicode, то есть с String.compareTo в Java (для символов из BMP).
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
    RETURN (SELECT count(*) FROM organization WHERE full_name COLLATE "C" > p_full_name COLLATE "C");
END;
$$;

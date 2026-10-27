package ru.itmo.is.lab1.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.is.lab1.domain.Coordinates;

import java.util.Collection;
import java.util.List;

public interface CoordinatesRepository extends JpaRepository<Coordinates, Long> {

    List<Coordinates> findAllByOrderById();

    /** Из переданных координат оставляет те, на которые больше не ссылается ни одна организация. */
    @Query("""
            select c.id from Coordinates c
            where c.id in :ids
              and not exists (select o.id from Organization o where o.coordinates = c)
            """)
    List<Long> findUnusedIds(@Param("ids") Collection<Long> ids);
}

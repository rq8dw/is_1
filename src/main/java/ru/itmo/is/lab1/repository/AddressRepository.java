package ru.itmo.is.lab1.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.is.lab1.domain.Address;

import java.util.Collection;
import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findAllByOrderById();

    /** Из переданных адресов оставляет те, на которые больше не ссылается ни одна организация. */
    @Query("""
            select a.id from Address a
            where a.id in :ids
              and not exists (
                  select o.id from Organization o
                  where o.officialAddress = a or o.postalAddress = a)
            """)
    List<Long> findUnusedIds(@Param("ids") Collection<Long> ids);
}

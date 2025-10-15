package charg.ing.stations.repository;


import charg.ing.stations.entity.StationStateOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;



@Repository
public interface StationStateOutboxRepository extends JpaRepository<StationStateOutbox, Long> {

    List<StationStateOutbox> findByPublishedFalseOrderByCreatedAtAsc();

    @Modifying
    @Query("UPDATE StationStateOutbox s SET s.published = true, s.publishedAt = :publishedAt WHERE s.id = :id")
    void markAsPublished(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Query("UPDATE StationStateOutbox o SET o.published = true, o.publishedAt = :publishedAt WHERE o.id IN :ids")
    void markAsPublished(@Param("ids") List<Long> ids, @Param("publishedAt") Instant publishedAt);
}
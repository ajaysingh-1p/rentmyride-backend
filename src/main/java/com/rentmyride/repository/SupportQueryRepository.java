package com.rentmyride.repository;

import com.rentmyride.entities.SupportQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportQueryRepository extends JpaRepository<SupportQuery, Long> {
    List<SupportQuery> findAllByOrderByCreatedAtDesc();
    List<SupportQuery> findByResolvedFalseOrderByCreatedAtDesc();
}

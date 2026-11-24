package com.hrragassistant.backend.companies;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyYearRepository extends JpaRepository<CompanyYear, java.util.UUID> {

    List<CompanyYear> findByLeiOrderByPeriodEndAsc(String lei);

    long deleteByLei(String lei);
}

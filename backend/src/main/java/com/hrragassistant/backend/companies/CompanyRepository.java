package com.hrragassistant.backend.companies;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, String> {

    List<Company> findAllByOrderByNameAsc();
}

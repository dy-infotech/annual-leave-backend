package com.dyinfotech.annualleavebackend.repository;

import com.dyinfotech.annualleavebackend.domain.BasisData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BasisDataRepository extends JpaRepository<BasisData, BasisData.BasisDataId> {
	// find only for a specific year (do not use findAll)
	java.util.List<BasisData> findByYear(String year);

}

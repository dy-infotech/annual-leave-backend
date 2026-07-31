package com.dyinfotech.annualleavebackend.repository.query;

import java.util.Collection;
import java.util.List;

public interface TeamRepositoryCustom {
	List<Long> findAllProjectManagerIds();
    List<Long> findAllProjectManagerIdsByEmployeeIds(Collection<Long> employeeIds);
}

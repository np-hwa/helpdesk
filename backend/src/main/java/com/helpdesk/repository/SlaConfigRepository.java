package com.helpdesk.repository;

import com.helpdesk.entity.SlaConfig;
import com.helpdesk.enums.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaConfigRepository extends JpaRepository<SlaConfig, Priority> {}

package com.refinements.visualizer.workspace.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface WorkspaceStateJpaRepository extends JpaRepository<WorkspaceStateEntity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkspaceStateEntity w where w.id = 1")
    Optional<WorkspaceStateEntity> lockSingleton();
}

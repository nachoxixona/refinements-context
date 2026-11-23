package com.refinements.visualizer.workspace;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceStateRepository extends JpaRepository<WorkspaceState, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkspaceState w where w.id = 1")
    Optional<WorkspaceState> lockSingleton();
}

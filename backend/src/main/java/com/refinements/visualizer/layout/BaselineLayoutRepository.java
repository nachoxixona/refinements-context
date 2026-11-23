package com.refinements.visualizer.layout;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BaselineLayoutRepository extends JpaRepository<BaselineLayout, Short> {

    default BaselineLayout getSingleton() {
        return findById(BaselineLayout.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("baseline_layout row 1 is missing"));
    }
}

package com.shortform.backend.repository;

import com.shortform.backend.domain.entity.BgmTrack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BgmTrackRepository extends JpaRepository<BgmTrack, Long> {

    List<BgmTrack> findByIsActiveTrueOrderByIsTrendingDescNameAsc();

    List<BgmTrack> findByCategoryAndIsActiveTrue(String category);
}

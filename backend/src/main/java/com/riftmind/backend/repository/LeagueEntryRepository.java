package com.riftmind.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.riftmind.backend.model.LeagueEntry;

/** Accès à la table {@code league_entry}. La clé est le couple (PUUID, file). */
public interface LeagueEntryRepository extends JpaRepository<LeagueEntry, LeagueEntry.Key> {

    /**
     * Rangs d'un joueur, sans ordre garanti : trier avec {@code RankedQueue} (l'ordre alphabétique placerait la Flex
     * avant la SoloQ). Utilise la clé primaire {@code (puuid, queue_type)}.
     */
    List<LeagueEntry> findByPuuid(String puuid);
}

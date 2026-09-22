package com.vertice.api.plan.exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /**
     * The starter set plus {@code ownerId}'s private exercises, optionally narrowed to one muscle
     * group ({@code 0} = any) and a name substring ({@code ''} = any; the caller escapes LIKE
     * wildcards). Order (R47-R50): own rows by name; then starter rows filed under the filtered
     * group as primary, in catalog order; then the remaining starter rows by name.
     *
     * <p>Native because the ordering needs a correlated {@code catalog_order} lookup, which JPQL
     * cannot express here. {@code 0}/{@code ''} stand in for "no filter" because Postgres cannot
     * type a bare {@code :param IS NULL}.
     */
    @Query(nativeQuery = true, value = """
            SELECT e.id, e.name, e.description, e.video_url, e.owner_id
            FROM exercises e
            WHERE (e.owner_id IS NULL OR e.owner_id = :ownerId)
              AND (:muscleGroupId = 0 OR EXISTS (SELECT 1
                                                 FROM exercise_muscle_groups g
                                                 WHERE g.exercise_id = e.id
                                                   AND g.muscle_group_id = :muscleGroupId))
              AND (:search = '' OR e.name ILIKE '%' || :search || '%' ESCAPE '\\')
            ORDER BY (e.owner_id IS NULL) ASC,
                     CASE WHEN e.owner_id IS NULL AND :muscleGroupId <> 0
                          THEN (SELECT g.catalog_order
                                FROM exercise_muscle_groups g
                                WHERE g.exercise_id = e.id
                                  AND g.muscle_group_id = :muscleGroupId
                                  AND g.is_primary)
                     END ASC NULLS LAST,
                     e.name ASC
            """)
    List<Exercise> findVisible(@Param("ownerId") Long ownerId,
                               @Param("muscleGroupId") long muscleGroupId,
                               @Param("search") String search);
}

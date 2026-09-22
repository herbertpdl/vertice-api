package com.vertice.api.plan.exercise;

import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "video_url")
    private String videoUrl;

    /** {@code null} = shared starter set; otherwise private to this trainer. */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    // Batched so mapping a list of exercises does not load each exercise's groups one by one.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExerciseMuscleGroup> muscleGroups = new ArrayList<>();

    public boolean isStarter() {
        return owner == null;
    }

    /** Starter rows are visible to everyone; a private row only to the trainer who owns it. */
    public boolean isVisibleTo(CallerIdentity caller) {
        return isStarter() || isOwnedBy(caller);
    }

    public boolean isOwnedBy(CallerIdentity caller) {
        return owner != null && caller.role() == Role.TRAINER && owner.getId().equals(caller.userId());
    }

    /**
     * Links {@code groups} as trainer-filed groups: none is primary and none has a catalog order
     * (only starter rows seeded by V24 carry those). Expects the collection to be empty — on an
     * update, clear it and flush first (see {@code ExerciseService}).
     */
    public void addMuscleGroups(List<MuscleGroup> groups) {
        for (MuscleGroup group : groups) {
            ExerciseMuscleGroup link = new ExerciseMuscleGroup();
            link.setExercise(this);
            link.setMuscleGroup(group);
            link.setPrimary(false);
            link.setCatalogOrder(null);
            muscleGroups.add(link);
        }
    }
}

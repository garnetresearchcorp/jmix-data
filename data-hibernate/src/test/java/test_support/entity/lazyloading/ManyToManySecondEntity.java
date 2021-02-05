package test_support.entity.lazyloading;

import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.Store;
import test_support.entity.BaseEntity;

import javax.persistence.*;
import java.util.List;
import java.util.Set;

@Table(name = "TEST_MANY_TO_MANY_SECOND_ENTITY")
@JmixEntity
@Entity(name = "test_ManyToManySecondEntity")
public class ManyToManySecondEntity extends BaseEntity {
    @Column(name = "NAME")
    protected String name;

    @JoinTable(name = "TEST_MANY_TO_MANY_FIRST_ENTITY_MANY_TO_MANY_SECOND_ENTITY_LINK",
            joinColumns = @JoinColumn(name = "MANY_TO_MANY_SECOND_ENTITY_ID"),
            inverseJoinColumns = @JoinColumn(name = "MANY_TO_MANY_FIRST_ENTITY_ID"))
    @ManyToMany(fetch = FetchType.LAZY)
    protected Set<ManyToManyFirstEntity> manyToManyFirstEntities;

    public Set<ManyToManyFirstEntity> getManyToManyFirstEntities() {
        return manyToManyFirstEntities;
    }

    public void setManyToManyFirstEntities(Set<ManyToManyFirstEntity> manyToManyFirstEntities) {
        this.manyToManyFirstEntities = manyToManyFirstEntities;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
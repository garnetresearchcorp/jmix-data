/*
 * Copyright 2021 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jmix.hibernate.impl.metadata;

import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.core.metamodel.model.MetaProperty;
import io.jmix.core.metamodel.model.Range;
import io.jmix.hibernate.impl.HibernateDataProperties;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.Column;
import java.lang.reflect.AnnotatedElement;

import static java.lang.String.format;

@Component("hibernate_SoftDeletionMetadataEnhancer")
public class SoftDeletionMetadataEnhancer implements MetadataEnhancer {

    private static final String IS_NOT_DELETED_CONDITION = "%s is null";
    private static final String AND_IS_NOT_DELETED_CONDITION = "%s AND " + IS_NOT_DELETED_CONDITION;

    @Autowired
    private MetadataTools metadataTools;

    @Autowired
    private Metadata jmixMetadata;

    @Autowired
    private HibernateDataProperties dataProperties;

    @Override
    public void enhance(MetadataImplementor metadata) {
        if (dataProperties.isSoftDeletionEnabled()) {
            addSoftDeletionFilters(metadata);
        }
    }

    private void addSoftDeletionFilters(MetadataImplementor metadata) {
        for (PersistentClass entityBinding : metadata.getEntityBindings()) {
            if (isSoftDeletable(entityBinding)) {
                if (entityBinding instanceof RootClass) {
                    addSoftDeletionFilter((RootClass) entityBinding);
                } else {
                    Class<?> mappedClass = entityBinding.getMappedClass();
                    String deletedDateProperty = metadataTools.findDeletedDateProperty(mappedClass);
                    if (jmixMetadata.getClass(mappedClass).getOwnProperties().stream()
                            .anyMatch(p -> p.getName().equals(deletedDateProperty))) {
                        throw new IllegalStateException(format(
                                "Soft deletion property '%s' is not supported on inherited classes (class: %s)",
                                deletedDateProperty,
                                mappedClass.getName())
                        );
                    }
                }
            }
            applyFilterToCollections(entityBinding);
        }
    }

    private void applyFilterToCollections(PersistentClass entityBinding) {
        MetaClass metaClass = jmixMetadata.getClass(entityBinding.getMappedClass());
        for (MetaProperty metaProperty : metaClass.getProperties()) {
            Range range = metaProperty.getRange();
            if (range.isClass()) {
                MetaClass propertyClass = range.asClass();
                if (metadataTools.isSoftDeletable(propertyClass.getJavaClass())) {
                    if (entityBinding.hasProperty(metaProperty.getName())) {
                        String columnName = getSoftDeletionPropertyColumn(propertyClass.getJavaClass());
                        if (columnName != null) {
                            Property property = entityBinding.getProperty(metaProperty.getName());
                            if (property.getValue() instanceof Bag) {
                                Bag bag = (Bag) property.getValue();
                                if (isManyToMany(metaProperty)) {
                                    applyIgnoreNotFound(bag);
                                    addManyToManyFilter(columnName, bag);
                                } else {
                                    addFilter(columnName, bag);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyIgnoreNotFound(Bag bag) {
        if (bag.getElement() instanceof ManyToOne) {
            ManyToOne manyToOne = (ManyToOne) bag.getElement();
            manyToOne.setIgnoreNotFound(true);
        }
    }

    private void addFilter(String columnName, Filterable bag) {
        if (bag instanceof Collection) {
            Collection collection = (Collection) bag;
            if (StringUtils.isNotEmpty(collection.getWhere())) {
                collection.setWhere(format(
                        AND_IS_NOT_DELETED_CONDITION,
                        collection.getWhere(), columnName));
            } else {
                collection.setWhere(format(IS_NOT_DELETED_CONDITION, columnName));
            }
        }
    }

    private void addManyToManyFilter(String columnName, Filterable bag) {
        if (bag instanceof Collection) {
            Collection collection = (Collection) bag;
            if (StringUtils.isNotEmpty(collection.getManyToManyWhere())) {
                collection.setManyToManyWhere(format(
                        AND_IS_NOT_DELETED_CONDITION,
                        collection.getManyToManyWhere(), columnName));
            } else {
                collection.setManyToManyWhere(format(IS_NOT_DELETED_CONDITION, columnName));
            }
        }
    }

    private boolean isManyToMany(MetaProperty metaProperty) {
        return Range.Cardinality.MANY_TO_MANY == metaProperty.getRange().getCardinality();
    }

    private void addSoftDeletionFilter(RootClass entityBinding) {
        Class<?> mappedClass = entityBinding.getMappedClass();
        String columnName = getSoftDeletionPropertyColumn(mappedClass);
        if (columnName != null) {
            if (StringUtils.isNotEmpty(entityBinding.getWhere())) {
                entityBinding.setWhere(format(
                        AND_IS_NOT_DELETED_CONDITION,
                        entityBinding.getWhere(), columnName));
            } else {
                entityBinding.setWhere(format(IS_NOT_DELETED_CONDITION, columnName));
            }

        }
    }

    private String getSoftDeletionPropertyColumn(Class mappedClass) {
        String deleteProperty = metadataTools.findDeletedDateProperty(mappedClass);
        if (deleteProperty != null) {
            MetaClass metaClass = jmixMetadata.getClass(mappedClass);
            AnnotatedElement annotatedElement = metaClass.findProperty(deleteProperty).getAnnotatedElement();
            Column annotation = annotatedElement.getAnnotation(Column.class);
            if (annotation != null) {
                return annotation.name();
            }
        }
        return null;
    }

    private boolean isSoftDeletable(PersistentClass entityBinding) {
        return metadataTools.isSoftDeletable(entityBinding.getMappedClass());
    }
}
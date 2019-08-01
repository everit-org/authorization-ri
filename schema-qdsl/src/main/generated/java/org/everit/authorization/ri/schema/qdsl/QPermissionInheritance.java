/*
 * Copyright (C) 2011 Everit Kft. (http://www.everit.biz)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.authorization.ri.schema.qdsl;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.Generated;
import com.querydsl.core.types.Path;

import com.querydsl.sql.ColumnMetadata;
import java.sql.Types;




/**
 * QPermissionInheritance is a Querydsl query type for QPermissionInheritance
 */
@Generated("com.querydsl.sql.codegen.MetaDataSerializer")
public class QPermissionInheritance extends com.querydsl.sql.RelationalPathBase<QPermissionInheritance> {

    private static final long serialVersionUID = 1141793126;

    public static final QPermissionInheritance permissionInheritance = new QPermissionInheritance("authr_permission_inheritance");

    public class PrimaryKeys {

        public final com.querydsl.sql.PrimaryKey<QPermissionInheritance> permissionInheritancePK = createPrimaryKey(childResourceId, parentResourceId);

    }

    public final NumberPath<Long> childResourceId = createNumber("childResourceId", Long.class);

    public final NumberPath<Long> parentResourceId = createNumber("parentResourceId", Long.class);

    public final PrimaryKeys pk = new PrimaryKeys();

    public QPermissionInheritance(String variable) {
        super(QPermissionInheritance.class, forVariable(variable), "org.everit.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public QPermissionInheritance(String variable, String schema, String table) {
        super(QPermissionInheritance.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public QPermissionInheritance(String variable, String schema) {
        super(QPermissionInheritance.class, forVariable(variable), schema, "authr_permission_inheritance");
        addMetadata();
    }

    public QPermissionInheritance(Path<? extends QPermissionInheritance> path) {
        super(path.getType(), path.getMetadata(), "org.everit.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public QPermissionInheritance(PathMetadata metadata) {
        super(QPermissionInheritance.class, metadata, "org.everit.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(childResourceId, ColumnMetadata.named("child_resource_id").withIndex(2).ofType(Types.BIGINT).withSize(19).notNull());
        addMetadata(parentResourceId, ColumnMetadata.named("parent_resource_id").withIndex(1).ofType(Types.BIGINT).withSize(19).notNull());
    }

}


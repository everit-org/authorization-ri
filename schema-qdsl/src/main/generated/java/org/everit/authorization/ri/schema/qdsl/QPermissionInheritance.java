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

import static com.mysema.query.types.PathMetadataFactory.*;

import com.mysema.query.types.path.*;

import com.mysema.query.types.PathMetadata;
import javax.annotation.Generated;
import com.mysema.query.types.Path;

import com.mysema.query.sql.ColumnMetadata;




/**
 * QPermissionInheritance is a Querydsl query type for QPermissionInheritance
 */
@Generated("com.mysema.query.sql.codegen.MetaDataSerializer")
public class QPermissionInheritance extends com.mysema.query.sql.RelationalPathBase<QPermissionInheritance> {

    private static final long serialVersionUID = 1141793126;

    public static final QPermissionInheritance permissionInheritance = new QPermissionInheritance("authr_permission_inheritance");

    public class PrimaryKeys {

        public final com.mysema.query.sql.PrimaryKey<QPermissionInheritance> permissionInheritancePK = createPrimaryKey(childResourceId, parentResourceId);

    }

    public class ForeignKeys {

        public final com.mysema.query.sql.ForeignKey<org.everit.resource.ri.schema.qdsl.QResource> childResourceFK = createForeignKey(childResourceId, "resource_id");

        public final com.mysema.query.sql.ForeignKey<org.everit.resource.ri.schema.qdsl.QResource> parentResourceFK = createForeignKey(parentResourceId, "resource_id");

    }

    public final NumberPath<Long> childResourceId = createNumber("childResourceId", Long.class);

    public final NumberPath<Long> parentResourceId = createNumber("parentResourceId", Long.class);

    public final PrimaryKeys pk = new PrimaryKeys();

    public final ForeignKeys fk = new ForeignKeys();

    public QPermissionInheritance(String variable) {
        super(QPermissionInheritance.class, forVariable(variable), "org.everit.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public QPermissionInheritance(String variable, String schema, String table) {
        super(QPermissionInheritance.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public QPermissionInheritance(Path<? extends QPermissionInheritance> path) {
        super(path.getType(), path.getMetadata(), "org.everit.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public QPermissionInheritance(PathMetadata<?> metadata) {
        super(QPermissionInheritance.class, metadata, "org.everit.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(childResourceId, ColumnMetadata.named("child_resource_id").ofType(-5).withSize(19).notNull());
        addMetadata(parentResourceId, ColumnMetadata.named("parent_resource_id").ofType(-5).withSize(19).notNull());
    }

}


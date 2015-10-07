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
 * QPermission is a Querydsl query type for QPermission
 */
@Generated("com.mysema.query.sql.codegen.MetaDataSerializer")
public class QPermission extends com.mysema.query.sql.RelationalPathBase<QPermission> {

    private static final long serialVersionUID = -195477308;

    public static final QPermission permission = new QPermission("authr_permission");

    public class PrimaryKeys {

        public final com.mysema.query.sql.PrimaryKey<QPermission> permissionPK = createPrimaryKey(action, authorizedResourceId, targetResourceId);

    }

    public class ForeignKeys {

        public final com.mysema.query.sql.ForeignKey<org.everit.resource.ri.schema.qdsl.QResource> targetResourceFK = createForeignKey(targetResourceId, "resource_id");

        public final com.mysema.query.sql.ForeignKey<org.everit.resource.ri.schema.qdsl.QResource> authorizedResourceFK = createForeignKey(authorizedResourceId, "resource_id");

    }

    public final StringPath action = createString("action");

    public final NumberPath<Long> authorizedResourceId = createNumber("authorizedResourceId", Long.class);

    public final NumberPath<Long> targetResourceId = createNumber("targetResourceId", Long.class);

    public final PrimaryKeys pk = new PrimaryKeys();

    public final ForeignKeys fk = new ForeignKeys();

    public QPermission(String variable) {
        super(QPermission.class, forVariable(variable), "org.everit.authorization.ri", "authr_permission");
        addMetadata();
    }

    public QPermission(String variable, String schema, String table) {
        super(QPermission.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public QPermission(Path<? extends QPermission> path) {
        super(path.getType(), path.getMetadata(), "org.everit.authorization.ri", "authr_permission");
        addMetadata();
    }

    public QPermission(PathMetadata<?> metadata) {
        super(QPermission.class, metadata, "org.everit.authorization.ri", "authr_permission");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(action, ColumnMetadata.named("action_").ofType(12).withSize(255).notNull());
        addMetadata(authorizedResourceId, ColumnMetadata.named("authorized_resource_id").ofType(-5).withSize(19).notNull());
        addMetadata(targetResourceId, ColumnMetadata.named("target_resource_id").ofType(-5).withSize(19).notNull());
    }

}


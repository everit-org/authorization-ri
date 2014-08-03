/**
 * This file is part of Everit - Authorization RI Querydsl Schema.
 *
 * Everit - Authorization RI Querydsl Schema is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Authorization RI Querydsl Schema is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Authorization RI Querydsl Schema.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.authorization.ri.schema.qdsl;

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

    private static final long serialVersionUID = 607352790;

    public static final QPermission permission = new QPermission("authr_permission");

    public class PrimaryKeys {

        public final com.mysema.query.sql.PrimaryKey<QPermission> permissionPK = createPrimaryKey(action, authorizedResourceId, targetResourceId);

    }

    public class ForeignKeys {

        public final com.mysema.query.sql.ForeignKey<org.everit.osgi.resource.ri.schema.qdsl.QResource> targetResourceFK = createForeignKey(targetResourceId, "resource_id");

        public final com.mysema.query.sql.ForeignKey<org.everit.osgi.resource.ri.schema.qdsl.QResource> authorizedResourceFK = createForeignKey(authorizedResourceId, "resource_id");

    }

    public final StringPath action = createString("action");

    public final NumberPath<Long> authorizedResourceId = createNumber("authorizedResourceId", Long.class);

    public final NumberPath<Long> targetResourceId = createNumber("targetResourceId", Long.class);

    public final PrimaryKeys pk = new PrimaryKeys();

    public final ForeignKeys fk = new ForeignKeys();

    public QPermission(String variable) {
        super(QPermission.class, forVariable(variable), "org.everit.osgi.authorization.ri", "authr_permission");
        addMetadata();
    }

    public QPermission(String variable, String schema, String table) {
        super(QPermission.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public QPermission(Path<? extends QPermission> path) {
        super(path.getType(), path.getMetadata(), "org.everit.osgi.authorization.ri", "authr_permission");
        addMetadata();
    }

    public QPermission(PathMetadata<?> metadata) {
        super(QPermission.class, metadata, "org.everit.osgi.authorization.ri", "authr_permission");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(action, ColumnMetadata.named("action_").ofType(12).withSize(255).notNull());
        addMetadata(authorizedResourceId, ColumnMetadata.named("authorized_resource_id").ofType(-5).withSize(19).notNull());
        addMetadata(targetResourceId, ColumnMetadata.named("target_resource_id").ofType(-5).withSize(19).notNull());
    }

}


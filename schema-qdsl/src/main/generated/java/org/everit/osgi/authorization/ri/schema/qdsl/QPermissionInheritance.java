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
 * QPermissionInheritance is a Querydsl query type for QPermissionInheritance
 */
@Generated("com.mysema.query.sql.codegen.MetaDataSerializer")
public class QPermissionInheritance extends com.mysema.query.sql.RelationalPathBase<QPermissionInheritance> {

    private static final long serialVersionUID = 1022978836;

    public static final QPermissionInheritance permissionInheritance = new QPermissionInheritance("authr_permission_inheritance");

    public class PrimaryKeys {

        public final com.mysema.query.sql.PrimaryKey<QPermissionInheritance> permissionInheritancePK = createPrimaryKey(childResourceId, parentResourceId);

    }

    public class ForeignKeys {

        public final com.mysema.query.sql.ForeignKey<org.everit.osgi.resource.ri.schema.qdsl.QResource> childResourceFK = createForeignKey(childResourceId, "resource_id");

        public final com.mysema.query.sql.ForeignKey<org.everit.osgi.resource.ri.schema.qdsl.QResource> parentResourceFK = createForeignKey(parentResourceId, "resource_id");

    }

    public final NumberPath<Long> childResourceId = createNumber("childResourceId", Long.class);

    public final NumberPath<Long> parentResourceId = createNumber("parentResourceId", Long.class);

    public final PrimaryKeys pk = new PrimaryKeys();

    public final ForeignKeys fk = new ForeignKeys();

    public QPermissionInheritance(String variable) {
        super(QPermissionInheritance.class, forVariable(variable), "org.everit.osgi.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public QPermissionInheritance(String variable, String schema, String table) {
        super(QPermissionInheritance.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public QPermissionInheritance(Path<? extends QPermissionInheritance> path) {
        super(path.getType(), path.getMetadata(), "org.everit.osgi.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public QPermissionInheritance(PathMetadata<?> metadata) {
        super(QPermissionInheritance.class, metadata, "org.everit.osgi.authorization.ri", "authr_permission_inheritance");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(childResourceId, ColumnMetadata.named("child_resource_id").ofType(-5).withSize(19).notNull());
        addMetadata(parentResourceId, ColumnMetadata.named("parent_resource_id").ofType(-5).withSize(19).notNull());
    }

}


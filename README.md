authorization-ri
================

Reference implentation of [Authorization API][1] based on
[Modularized Persistence][2].

## Component

The module contains one Declarative Services component. The component can
be instantiated multiple times via Configuration Admin. The component
registers three OSGi services:

 - AuthorizationManager, to be able to manage permissions and permission
   inheritance
 - PermissionChecker, to check permissions
 - AuthorizationQdslUtil to generate authorization-predicates for existing
   Querydsl based database queries.

## Database structure

### Permission table

 - authorized_resource_id: Resource id of a user, group, role, etc.
   A resource that can be authorized to do actions on something.
 - target_resource_id: Resource id of a book, document, etc. A resource
   that can be used to run authorized actions on.
 - action: edit, view, delete, etc. Any activity that needs permission.

All three fields are part of the composite primary key of the table. The
two resource ids are foreign keys that point to the resource table.

### Permission inheritance table

 - parent_resource_id: Resource id of a role, user group, etc.
 - child_resource_id: Resource id of a user, role, user group, etc.

The child resource inherits all rights from the parent resource. Inheritance
works transitively. E.g.: Multi-level role or user group hierarchy can be
designed.

The two fields are part of the composite primary key of the table. Both
fields are foreign keys where the referenced field is the primary key of
resource table.

## Caching

The component needs two caches to be able to work.

 - Permission cache: Stores the records of the permission table. The cache
   also stores those permissions, that were queried but the permission is
   not granted.
  - Permission inheritance cache: Stores the content of the
    permission_inheritance table as it is.

NoOp cache can be used, however, the tests show that with a no-operation
cache the checkPermission function works at least twenty times slower.

## Performance

With the simplest cache implementation (ConcurrentHashMap), and a simple
three level permission inheritance graph, the permission checker can answer
a 1.000 times in a millisecond on an average notebook. This might be worse a
little bit with large set of data. The cache size should be at least as big
as many records are used frequently from the database frequently on one node.

[1]: https://github.com/everit-org/authorization-api
[2]: http://everitorg.wordpress.com/2014/06/18/modularized-persistence/

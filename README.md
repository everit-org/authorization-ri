authorization-ri
================

Reference implentation of [Authorization API][1] based on
[Modularized Persistence][2].

## Component

The module contains one Declarative Services component. The component can
be instantiated multiple times via Configuration Admin. The component
registers three OSGi services:

 - __AuthorizationManager:__ Managing permissions and permission
   inheritances
 - __PermissionChecker:__ Checking permissions, getting the scope of the
   authorization and the resource id of the system
 - __AuthorizationQdslUtil:__ Generating predicates for existing Querydsl
   based database queries

## God mode

There is a resource id that identifies the system. It is created when
the authorization-ri component is first started and stored in
property-manager. It is possible to retrieve the resource id of the
system from the PermissionChecker OSGi service.

When the system resource is used, all permission checks return
true (even if the target resource id does not exist).

Programmers, who develop custom permission check query extensions, should
always take care of checking if the authorized resource id is the resource
id of the system.

## Database structure

### Permission table

 - __authorized_resource_id:__ Resource id of a user, group, role, etc.
   A resource that can be authorized to do actions on something.
 - __target_resource_id:__ Resource id of a book, document, etc. A resource
   that can be used to run authorized actions on.
 - __action:__ edit, view, delete, etc. Any activity that needs permission.

All three fields are part of the composite primary key of the table. The
two resource ids are foreign keys that reference the resource table.

### Permission inheritance table

 - __parent_resource_id:__ Resource id of a role, user group, etc.
 - __child_resource_id:__ Resource id of a user, role, user group, etc.

The child resource inherits all rights from the parent resource. Inheritance
works transitively. E.g.: Multi-level role or user group hierarchy can be
designed.

The two fields are part of the composite primary key of the table. Both
fields are foreign keys that reference the resource table.

## Caching

The component needs two caches to be able to work.

 - __Permission cache:__ Stores the records of the permission table. The cache
   also stores those permissions, that were queried but the permission is
   not granted.
 - __Permission inheritance cache:__ Stores the content of the
   permission_inheritance table as it is.

NoOp cache can be used, however, the tests show that with a no-operation
cache the _checkPermission_ function works at least twenty times slower.

## Performance

With the simplest cache implementation (ConcurrentHashMap), and a simple
three level permission inheritance graph, the permission checker can answer
a 1.000 times in a millisecond on an average notebook. This might be worse a
little bit with large set of data and with a real transactional cache.
The cache size should be at least as big as many records are used frequently
from the database frequently on one node.

[![Analytics](https://ga-beacon.appspot.com/UA-15041869-4/everit-org/authorization-ri)](https://github.com/igrigorik/ga-beacon)

[1]: https://github.com/everit-org/authorization-api
[2]: http://everitorg.wordpress.com/2014/06/18/modularized-persistence/

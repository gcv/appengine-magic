The appengine-magic library attempts to abstract away the infrastructural nuts
and bolts of writing a Clojure application for the Google App Engine platform.

The development environment of Google App Engine for Java expects pre-compiled
classes, and generally does not fit well with Clojure's interactive development
model. appengine-magic attempts to make REPL-based development of App Engine
applications as natural as any other Clojure program.

1. Programs using appengine-magic just need to include appengine-magic as a
   Leiningen dev-dependency.
2. appengine-magic takes a Ring handler and makes it available as a servlet for
   App Engine deployment.
3. appengine-magic is also a Leiningen plugin, and adds several tasks which
   simplify preparing for App Engine deployment.

Using appengine-magic still requires familiarity with Google App Engine. This
README file tries to describe everything you need to know to use App Engine with
Clojure, but does not explain the details of App Engine semantics. Please refer
to Google's official documentation for details.



## Dependencies

* Clojure 1.2.0
* Leiningen 1.3.1
* Google App Engine SDK 1.3.7
* swank-clojure 1.2.1 (optional)



## Overview

To use appengine-magic effectively, you need the following:

1. The `appengine-magic` jar available on the project classpath.
2. A Ring handler for your main application. You may use any Ring-compatible
   framework to make it. If your application does not yet have a `core.clj`
   file, then the `lein appengine-new` task creates one for you with a simple
   "hello world" Ring handler.
3. A var defined by passing the Ring handler to the
   `appengine-magic.core/def-appengine-app` macro. This makes the application
   available both to interactive REPL development, and to App Engine itself.
4. An entry point servlet. REPL development does not use it, but the standard
   App Engine SDK `dev_appserver.sh` mode and production deployment both
   do. This servlet must be AOT-compiled into a class file. This servlet
   defaults to the name `app_servlet.clj`, and the `lein appengine-new` task
   creates one for your project. The servlet must refer to the var defined by
   `def-appengine-app`.
5. Web application resources. This primarily includes web application
   descriptors. `lein appengine-new` generates those and places them in the
   `resources/war/WEB-INF` directory. You should also place all static files
   that your application uses in `resources/war`.



## Getting Started


### Project setup

You need a copy of the Google App Engine SDK installed
somewhere. appengine-magic cannot replace its `dev_appserver.sh` and `appcfg.sh`
functionality.

1. `lein new` <project-name>
2. Optional: `rm src/<project-name>/core.clj` to clean out the default
   `core.clj` file created by Leiningen. You need to do this so that
   appengine-magic can create a default file which correctly invokes the
   `def-appengine-app` macro.
3. Edit `project.clj`:
   - add `:namespaces [<project>.app_servlet]` (or use the equivalent `:aot` directive)
   - add `[appengine-magic "0.2.1"]` to your `dev-dependencies`
4. `lein deps`. This fetches appengine-magic, and makes its Leiningen plugin
   tasks available.
5. `lein appengine-new`. This sets up four files for your project: `core.clj`
   (which has a sample Ring handler and uses the `def-appengine-app` macro),
   `app_servlet.clj` (the entry point for the application),
   `resources/war/WEB-INF/web.xml` (a servlet descriptor), and
   `resources/war/WEB-INF/appengine-web.xml` (an App Engine application
   descriptor). These files should contain reasonable starting defaults for your
   application.

The default `.gitignore` file produced by Leiningen works well with the
resulting project, but do take a careful look at it. In particular, you should
avoid checking in `resources/war/WEB-INF/lib` or
`resources/war/WEB-INF/classes`: let Leiningen take care of managing those
directories.


### Development process

Launch `lein swank` or `lein repl`, whichever you normally use. Once you have a
working REPL, compile your application's `core.clj` (or whatever other entry
point file you use).

The key construct provided by appengine-magic is the
`appengine-magic.core/def-appengine-app` macro. It takes a Ring handler and
defines a new `<project-name>-app` var. If you want to rename this var, remember
to update `app_servlet.clj`. That's it: you may now write your application using
any framework which produces a Ring-compatible handler. Then, just pass the
resulting Ring handler to `def-appengine-app`.

To test your work interactively, you can control a Jetty instance from the REPL
using `appengine-magic.core/start` and `appengine-magic.core/stop`. Examples
(assuming you are in your application's `core` namespace, your application is
named `foo`, and you aliased `appengine-magic.core` to `ae`):

    (ae/start foo-app)
    (ae/stop)
    (ae/start foo-app :port 8095)
    (ae/stop)

Recompiling the functions which make up your Ring handler should produce
instantaneous results.


### Testing with dev_appserver.sh

1. `lein appengine-prepare`. This AOT-compiles the entry point servlet, then
   copies the necessary classes and library dependencies to your application's
   `resources/war/WEB-INF/classes` and `resources/war/WEB-INF/lib` directories.
2. Run `dev_appserver.sh` with a path to your application's `resources/war`
   directory.


### Static resources

Just put all static files into your application's `resources/war` directory. If
you put a file called `index.html` there, it will become a default welcome file.


### Deployment to App Engine

1. First of all, be careful. You must manually maintain the version field in
   `appengine-web.xml` and you should understand its implications. Refer to
   Google App Engine documentation for more information.
2. `lein appengine-prepare` prepares the `resources/war` directory with the latest
   classes and libraries for deployment.
3. When you are ready to deploy, just run `appcfg.sh update` with a path to your
   application's `resources/war` directory.



## App Engine Services

appengine-magic provides convenience wrappers for using App Engine services from
Clojure.


### User service

The `appengine-magic.services.user` namespace (suggested alias: `ae-user`)
provides the following functions for handling users.

- `current-user`: returns the `com.google.appengine.api.users.User` for the
  currently logged-in user.
- `user-logged-in?`
- `user-admin?`
- `login-url` (optional keyword: `:destination`): returns the Google
  authentication servlet URL, and forwards the user to the optional destination.
- `logout-url` (optional keyword: `:destination`): performs logout, and forwards
  the user to the optional destination.


### Memcache service

The `appengine-magic.services.memcache` namespace (suggested alias:
`ae-memcache`) provides the following functions for the App Engine memcache. See
App Engine documentation for detailed explanations of the underlying Java API.

- `statistics`: returns the current memcache statistics.
- `clear-all!`: wipes the entire cache for all namespaces.
- `contains? <key>` (optional keyword: `:namespace`): checks if the given key
  exists in the cache.
- `delete! <key>` (optional keywords: `:namespace`, `:millis-no-readd`): removes
  the key from the cache, optionally refraining from adding it for the given
  number of milliseconds. If the key argument is sequential, deletes all the
  named keys.
- `get <key>` (optional keyword: `:namespace`): returns the value for the given
  key, but if the key argument is sequential, returns a map of key-value pairs
  for each supplied key.
- `put <key> <value>` (optional keywords: `:namespace`, `:expiration`,
  `:policy`): saves the given value under the given key; expiration is an
  instance of `com.google.appengine.api.memcache.Expiration`; policy is one of
  `:always` (the default), `:add-if-not-present`, or `:replace-only`.
- `put-map <key-value-map>` (optional keywords: `:namespace`, `:expiration`,
  `:policy`): writes the key-value-map into the cache. Other keywords same as
  for `put`.
- `increment <key> <delta>` (optional keywords: `:namespace`, `:initial`):
  atomically increments long integer values in the cache; if key is sequential,
  it increments all keys by the given delta.
- `increment-map <key-delta-map>` (optional keywords: `:namespace`, `:initial`):
  atomically increments long integer values by deltas given in the argument map.


### Datastore

The `appengine-magic.services.datastore` namespace (suggested alias: `ae-ds`)
provides a fairly complete interface for the App Engine datastore.

A few simple examples:

    (ae-ds/defentity Author [^:key name, birthday])
    (ae-ds/defentity Book [^:key isbn, title, author])

    ;; Writes three authors to the datastore.
    (let [will (Author. "Shakespeare, William" nil)
          geoff (Author. "Chaucer, Geoffrey" "1343")
          oscar (Author. "Wilde, Oscar" "1854-10-16")]
      ;; First, just write Will, without a birthday.
      (ae-ds/save! will)
      ;; Now overwrite Will with an entity containing a birthday, and also
      ;; write the other two authors.
      (ae-ds/save! [(assoc will :birthday "1564"), geoff, oscar]))

    ;; Retrieves two authors and writes book entites.
    (let [will (first (ae-ds/query :kind Author :filter (= :name "Shakespeare, William")))
          geoff (first (ae-ds/query :kind Author :filter [(= :name "Chaucer, Geoffrey")
                                                          (= :birthday "1343")]))]
      (ae-ds/save! (Book. "0393925870" "The Canterbury Tales" geoff))
      (ae-ds/save! (Book. "143851557X" "Troilus and Criseyde" geoff))
      (ae-ds/save! (Book. "0393039854" "The First Folio" will)))

    ;; Retrieves all Chaucer books in the datastore, sorting by descending title and
    ;; then by ISBN.
    (let [geoff (ae-ds/retrieve Author "Chaucer, Geoffrey")]
      (ae-ds/query :kind Book
                   :filter (= :author geoff)
                   :sort [[title :dsc] :isbn]))

    ;; Deletes all books by Chaucer.
    (let [geoff (ae-ds/retrieve Author "Chaucer, Geoffrey")]
      (ae-ds/delete! (ae-ds/query :kind Book :filter (= :author geoff))))

- `defentity` (optional keyword: `:kind`): defines an entity record type
  suitable for storing in the App Engine datastore. These entities work just
  like Clojure records. Internally, they implement an additional protocol,
  EntityProtocol, which provides the `save!` method. When defining an entity,
  you may specify `^:key` metadata on any one field of the record, and the
  datastore will use this as the primary key. Omitting the key will make the
  datastore assign an automatic primary key to the entity. Specifying the
  optional `:kind` keyword (a string value) causes App Engine to save the entity
  under the given "kind" name — like a datastore table. This allows kinds to
  remain disjoint from entity record types.
- `new*`: instantiates a datastore entity record. You may also use standard
  Clojure conventions to instantiate entity records, but creating entities
  destined for entity groups requires using `new*`. To put the new entity into a
  group, use the `:parent` keyword with the parent entity. Instantiating an
  entity does not automatically write it to the datastore.
- `get-key-object`: this returns the primary Key object of the given entity. For
  a newly-instantiated entity lacking an explicit primary key, this method
  returns nil. Entities properly brought under entity groups using `new*` will
  have hierarchical keys. You should rarely need to use this explicitly.
- `save!`: calling this method on an entity writes it to the datastore, using
  the primary key returned by calling `get-key-object` on the entity. May be
  called on a sequence of entities.
- `delete!`: removes an entity. May be called on a sequence of entities.
- `retrieve <entity-record-type> <primary-key>` (optional keywords: `:parent`,
  `:kind`): this is a low-level entity retrieval function. It returns a record
  of the given type with the given primary key value. If the target entity
  belongs to an entity group, specify the parent using the optional keyword. If
  the target entity was stored with a different kind from the entity record
  type, specify the actual kind using the optional keyword.
- `query` (optional keywords: `:kind`, `:ancestor`, `:filter`, `:sort`,
  `:keys-only?`, `:count-only?`, `:in-transaction?`, `:limit`, `:offset`,
  `:prefetch-size`, `:chunk-size`, `:entity-record-type`): runs a query with the
  given parameters.
  * `:kind`: primarily identifies the App Engine entity kind. If given as an
    entity record type (recommended), the query returns a sequence of entity
    records of that type. If given as a string, it then checks to see if
    `:entity-record-type` is given, and uses that type if so; otherwise, the
    query returns generic `EntityBase` records.
  * `:filter`: one filter clause, or a list of clauses. Each consists of a
    symbol specifying the filter operation, a property name, and a target
    property value. See example.
  * `:sort`: one sort criterion, or a list of criteria. Each specified criterion
    defaults to ascending sort order, but may also sort in descending order.
- `with-transaction <body>`: wraps the body in a transaction. Can be
  nested. (Keep the limitations of App Engine's transaction system in mind when
  using this.)
- `init-datastore-service`: not normally needed. Only use this method if you
  want to modify the the read consistency and implicit transaction policies of
  the datastore service.


## Limitations

When using the interactive REPL environment, some App Engine services are more
limited than in `dev_appserver.sh` or in deployment. Because the App Engine
SDK's jars are a mess, and many are not available in Maven repositories,
providing the same functionality in an interactive Clojure environment is tricky
and error-prone. In particular, the administration console, `/_ah/admin` is not
available in the REPL environment.

The following Google services are not yet tested in the REPL environment:

- Blobstore
- Images
- Mail
- Multitenancy
- OAuth
- Task queues
- URL fetch
- XMPP

They may still work, but appengine-magic does not provide convenient Clojure
interfaces for them, and may lack mappings for any necessary supporting URLs.



## Warning

Google App Engine maintains a whitelist of permitted classes in Java's standard
library. Other classes will cause your application to fail to deploy. Examples
include threads and sockets. If you use those in your application, it will not
work. This means that you cannot use Clojure's agents or futures. In addition,
if one of your dependencies uses those, your application will also not work. For
example, `clojure.java.io` (and its fore-runner, duck-streams from
`clojure-contrib`), uses `java.net.Socket`, a forbidden class.

Whenever you add a new dependency, no matter how innocuous, you should make sure
your app still works. `dev_appserver.sh` is a good place to start, but you must
also test in the main App Engine. The two do not always load classes the same
way.



## License

appengine-magic is distributed under the MIT license.

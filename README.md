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

Please read the project's HISTORY file to learn what changed in recent releases.



## Current Status

The code on this branch adds experimental support for App Engine SDK 1.7.4 and
Leiningen 2.0. A stable older version is available
[at the v0.5.0 tag](https://github.com/gcv/appengine-magic/tree/v0.5.0).


### TODO for a stable 0.5.1 release

* fix the strange startup error related to the blob store
* fix the `_ah/admin` console — some links do not work
* retest all supported App Engine services — server push (at least) is broken



## Dependencies

* Clojure 1.4.0
* Leiningen 2.0
* Google App Engine SDK 1.7.4



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
   `war/WEB-INF/` directory. You should also place all static files that your
   application uses in `war/`.

Here is a sample `core.clj`, using
[Compojure](https://github.com/weavejester/compojure) (other Ring-compatible
frameworks, such as [Moustache](https://github.com/cgrand/moustache), also
work):

```clojure
(ns simple-example.core
  (:use compojure.core)
  (:require [appengine-magic.core :as ae]))

(defroutes simple-example-app-handler
  (GET "/" req
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body "Hello, world!"})
  (GET "/hello/:name" [name]
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (format "Hello, %s!" name)})
  (ANY "*" _
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body "not found"}))

(ae/def-appengine-app simple-example-app #'simple-example-app-handler)
```

If you wish to emit HTML or XML from your application, you should use a
specialized Clojure server-side templating library, e.g.,
[Enlive](https://github.com/cgrand/enlive) or
[Hiccup](https://github.com/weavejester/hiccup). None of the appengine-magic
examples rely on these libraries.



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
3. Edit `project.clj`: add `[appengine-magic "0.5.1-SNAPSHOT"]` to both your
   `:dependencies` and `:plugins`.
4. `lein deps`. This fetches appengine-magic, and makes its Leiningen plugin
   tasks available. If you already have the App Engine SDK installed locally,
   and do not wish to wait for Maven to download it again as a dependency, you
   may optionally run the provided `install-artifacts.sh` script first.
5. `lein appengine-new`. This sets up four files for your project: `core.clj`
   (which has a sample Ring handler and uses the `def-appengine-app` macro),
   `app_servlet.clj` (the entry point for the application),
   `war/WEB-INF/web.xml` (a servlet descriptor), and
   `war/WEB-INF/appengine-web.xml` (an App Engine application descriptor). These
   files should contain reasonable starting defaults for your application.

With regard to AOT-compilation, if your project needs it, then you must include
`<project>.app_servlet` in Leiningen's `:aot` directive. Otherwise, omit the
`:aot` directive altogether. The `lein appengine-prepare` task will take care of
AOT-compiling the entry point servlet and cleaning up afterwards.

The default `.gitignore` file produced by Leiningen works well with the
resulting project, but do take a careful look at it. In particular, you should
avoid checking in `war/WEB-INF/lib/` or `war/WEB-INF/classes/`: let Leiningen
take care of managing those directories.


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
using `appengine-magic.core/start` and `appengine-magic.core/stop`. In addition,
a convenience function, `appengine-magic.core/serve`, will either start or restart
a running instance. Examples (assuming you are in your application's `core`
namespace and your application is named `foo`):

```clojure
(require '[appengine-magic.core :as ae])

;; recommended: use this to start or restart an app
(ae/serve foo-app)

;; or use these lower-level functions
(ae/start foo-app)
(ae/stop)
(ae/start foo-app :port 8095)
(ae/stop)
```

Recompiling the functions which make up your Ring handler should produce
instantaneous results.

If you use SLIME, then the `swank.core/break` function works even inside a Ring
handler.


### Testing with dev_appserver.sh

1. `lein appengine-prepare`. This AOT-compiles the entry point servlet, makes a
   jar of your application, and copies it, along with all your library
   dependencies, to your application's `war/WEB-INF/lib/` directories.
2. Run `dev_appserver.sh` with a path to your application's `war/` directory.


### Static files

Just put all static files into your application's `war/` directory. If you put a
file called `index.html` there, it will become a default welcome file.


### Classpath resources

Put all classpath resources you expect to need at runtime in `resources/`. You
can then access them using the `appengine-magic.core/open-resource-stream`,
which returns a `java.io.BufferedInputStream` instance.

You may also use `appengine-magic.core/resource-url` to find a classpath
resource's internal URL. This URL will not be externally visible (it will not be
an HTTP URL), but you may use it to refer to classpath resources from within the
application's code.

Do not use direct methods like `java.io.File` or
`ClassLoader/getSystemClassLoader` to access classpath resources; they do not
work consistently across all App Engine environments.


### Deployment to App Engine

1. First of all, be careful. You must manually maintain the version field in
   `appengine-web.xml` and you should understand its implications. Refer to
   Google App Engine documentation for more information.
2. `lein appengine-prepare` prepares the `war/` directory with the latest
   classes and libraries for deployment.
3. When you are ready to deploy, just run `appcfg.sh update` with a path to your
   application's `war/` directory.


### Checking the runtime environment

- `appengine-magic.core/appengine-environment-type`: returns a keyword
  corresponding to the current environment: `:production`, `:dev-appserver`, and
  `:interactive`. Useful if you want to, e.g., return more detailed error
  messages and stack traces in non-production mode.
- `appengine-magic.core/appengine-app-id`: returns the ID of the running
  application.
- `appengine-magic.core/appengine-app-version`: returns the current deployed
  version string.
- `appengine-magic.core/appengine-base-url`: returns a string with the base
  hostname of the current application, e.g., `http://my-app.appspot.com`. In
  production, this always points to the `appspot.com` domain. In interactive
  mode, this always points to `localhost`, but also includes the correct
  port. The `:https?` keyword determines if the schema in the URL should be
  `https://`, but is ignored in interactive mode. This function does not work in
  `dev_appserver.sh` at all (it is difficult from within the application
  environment to determine the correct port).


### Automatic testing code

The `clojure.test` system works well for testing `appengine-magic` applications,
but all tests must bootstrap App Engine services in order to run. The
`appengine-magic.testing` namespace provides several functions usable as
`clojure.test` fixtures to help you do so. The easiest way to get started is:

```clojure
(use 'clojure.test)
(require '[appengine-magic.testing :as ae-testing])

(use-fixtures :each (ae-testing/local-services :all))
```

Then, write `deftest` forms normally; you can use App Engine services just as you
would in application code.


### File uploads and multipart forms

A Ring application requires the use of middleware to convert the request body
into something useful in the request map. Ring comes with
`ring.middleware.multipart-params/wrap-multipart-params` which does this;
unfortunately, this middleware uses classes restricted in App Engine. To deal
with this, `appengine-magic` has its own middleware.

`appengine-magic.multipart-params/wrap-multipart-params` works just like the
Ring equivalent, except file upload parameters become maps with a `:bytes` key
(instead of `:tempfile`). This key contains a byte array with the upload data.

A full Compojure example (includes features from the Datastore service):

```clojure
(use 'compojure.core
     '[appengine-magic.multipart-params :only [wrap-multipart-params]])

(require '[appengine-magic.core :as ae]
         '[appengine-magic.services.datastore :as ds])

(ds/defentity Image [^{:tag :key} name, content-type, data])

(defroutes upload-images-demo-app-handler
  ;; HTML upload form
  (GET "/upload" _
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (str "<html><body>"
                   "<form action=\"/done\" "
                   "method=\"post\" enctype=\"multipart/form-data\">"
                   "<input type=\"file\" name=\"file-upload\">"
                   "<input type=\"submit\" value=\"Submit\">"
                   "</form>"
                   "</body></html>")})
  ;; handles the uploaded data
  (POST "/done" _
        (wrap-multipart-params
         (fn [req]
           (let [img (get (:params req) "file-upload")
                 img-entity (Image. (:filename img)
                                    (:content-type img)
                                    (ds/as-blob (:bytes img)))]
             (ds/save! img-entity)
             {:status 200
              :headers {"Content-Type" "text/plain"}
              :body (with-out-str
                      (println (:params req)))}))))
  ;; hit this route to retrieve an uploaded file
  (GET ["/img/:name", :name #".*"] [name]
       (let [img (ds/retrieve Image name)]
         (if (nil? img)
             {:status 404}
             {:status 200
              :headers {"Content-Type" (:content-type img)}
              :body (.getBytes (:data img))}))))

(ae/def-appengine-app upload-images-demo-app #'upload-images-demo-app-handler)
```

Please note that you do not need to use this middleware with the Blobstore
service. App Engine takes care decoding the upload in its internal handlers, and
the upload callbacks do not contain multipart data.


### Managing multiple environments

Most web applications use several environments internally: production, plus
various staging and development installations. App Engine supports multiple
versions in its `appengine-web.xml` file, but does nothing to help deal with
installing to different full environments. Since different versions of App
Engine applications share the same blobstore and datastore, distinguishing
between production and staging using only versions is dangerous.

`appengine-magic` has a mechanism to help deal with multiple environments. The
Leiningen `appengine-update` task replaces the use of `appcfg.sh update`, and a
new entry in `project.clj` manages applications and versions.

1. Rename your `WEB-INF/application-web.xml` file to
   `WEB-INF/application-web.xml.tmpl`. For safety reasons, `appengine-update`
   will not run if a normal `application-web.xml` exists. For clarity, you
   should blank out the contents of the `<application>` and `<version>` tags of
   the template file (but leave the tags in place).
2. Add a new entry to `project.clj`: `:appengine-app-versions`. This entry is a
   map from application name to application version. Example:

    ```clojure
     :appengine-app-versions {"myapp-production" "2010-11-25 11:15"
                              "myapp-staging"    "2010-11-27 22:05"
                              "myapp-dev1"       "2830"
                              "myapp-dev2"       "2893"}
    ```
   The `myapp-` key strings correspond to App Engine applications, registered
   and managed through the App Engine console. The value strings are the
   versions `appengine-update` will install if invoked on that application.
3. Add a new entry to `project.clj`: `:appengine-sdk`. The App Engine SDK
   location is necessary to execute the actual production deployment. This value
   can be just a string, representing a path. Alternatively, for teams whose
   members keep the App Engine SDK in different locations, this value can be a
   map from username to path string. Examples:

    ```clojure
     :appengine-sdk "/opt/appengine-java-sdk"
     :appengine-sdk {"alice"   "/opt/appengine-java-sdk"
                     "bob"     "/Users/bob/lib/appengine-java-sdk"
                     "charlie" "/home/charlie/appengine/sdk/current"}
    ```
   If the APPENGINE_HOME environment variable is set, its value will
   be used if no :appengine-sdk entry is found in the project.clj
   file.
4. Run `lein appengine-update <application>`, where the argument is an
   application name from the `:appengine-app-versions` map.

If you use this mechanism, be aware that `dev_appserver.sh` will no longer work
(since your project no longer defines a simple `appengine-web.xml` file). To run
that process, use `lein appengine-dev-appserver <application>`.

You may also force a specific version string if you pass it as an optional
argument to `appengine-update`: `lein appengine-update <application> <version>`.



## App Engine Services

appengine-magic provides convenience wrappers for using App Engine services from
Clojure. Most of these API calls will work when invoked from the REPL, but only
if an application is running — that is, it was launched using
`appengine-magic.core/start`.


### User service

The `appengine-magic.services.user` namespace provides the following functions
for handling users.

- `current-user`: returns the `com.google.appengine.api.users.User` for the
  currently logged-in user.
- `user-logged-in?`
- `user-admin?`
- `login-url` (optional keyword: `:destination`): returns the Google
  authentication servlet URL, and forwards the user to the optional destination.
- `logout-url` (optional keyword: `:destination`): performs logout, and forwards
  the user to the optional destination.


### Memcache service

The `appengine-magic.services.memcache` namespace provides the following
functions for the App Engine memcache. See App Engine documentation for detailed
explanations of the underlying Java API.

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
- `put! <key> <value>` (optional keywords: `:namespace`, `:expiration`,
  `:policy`): saves the given value under the given key; expiration is an
  instance of `com.google.appengine.api.memcache.Expiration`; policy is one of
  `:always` (the default), `:add-if-not-present`, or `:replace-only`.
- `put-map! <key-value-map>` (optional keywords: `:namespace`, `:expiration`,
  `:policy`): writes the key-value-map into the cache. Other keywords same as
  for `put`.
- `increment! <key> <delta>` (optional keywords: `:namespace`, `:initial`):
  atomically increments long integer values in the cache; if key is sequential,
  it increments all keys by the given delta.
- `increment-map! <key-delta-map>` (optional keywords: `:namespace`, `:initial`):
  atomically increments long integer values by deltas given in the argument map.


### Datastore

The `appengine-magic.services.datastore` namespace provides a fairly complete
interface for the App Engine datastore.

A few simple examples:

```clojure
(require '[appengine-magic.services.datastore :as ds])

(ds/defentity Author [^{:tag :key} name, birthday])
(ds/defentity Book [^{:tag :key} isbn, title, author])

;; Writes three authors to the datastore.
(let [will (Author. "Shakespeare, William" nil)
      geoff (Author. "Chaucer, Geoffrey" "1343")
      oscar (Author. "Wilde, Oscar" "1854-10-16")]
  ;; First, just write Will, without a birthday.
  (ds/save! will)
  ;; Now overwrite Will with an entity containing a birthday, and also
  ;; write the other two authors.
  (ds/save! [(assoc will :birthday "1564"), geoff, oscar]))

;; Retrieves two authors and writes book entites.
(let [will (first (ds/query :kind Author :filter (= :name "Shakespeare, William")))
      geoff (first (ds/query :kind Author :filter [(= :name "Chaucer, Geoffrey")
                                                   (= :birthday "1343")]))]
  (ds/save! (Book. "0393925870" "The Canterbury Tales" geoff))
  (ds/save! (Book. "143851557X" "Troilus and Criseyde" geoff))
  (ds/save! (Book. "0393039854" "The First Folio" will)))

;; Retrieves all Chaucer books in the datastore, sorting by descending title and
;; then by ISBN.
(let [geoff (ds/retrieve Author "Chaucer, Geoffrey")]
  (ds/query :kind Book
            :filter (= :author geoff)
            :sort [[title :dsc] :isbn]))

;; Deletes all books by Chaucer.
(let [geoff (ds/retrieve Author "Chaucer, Geoffrey")]
  (ds/delete! (ds/query :kind Book :filter (= :author geoff))))
```

The next example (which uses Compojure) demonstrates the use of entity groups
and transactions.

```clojure
(use '[clojure.pprint :only [pprint]]
     'compojure.core)
(require '[appengine-magic.core :as ae]
         '[appengine-magic.services.datastore :as ds])

(ds/defentity Parent [^{:tag :key} name, children])
(ds/defentity Child [^{:tag :key} name])

(defroutes entity-group-example-app-handler
  (GET  "/" [] {:headers {"Content-Type" "text/plain"} :body "started"})
  (POST "/new/:parent-name/:child-name" [parent-name child-name]
        (let [parent (or (ds/retrieve Parent parent-name)
                         ;; Note the use of ds/save! here. Unless an entity has
                         ;; been saved to the datastore, children cannot join
                         ;; the entity group.
                         (ds/save! (Parent. parent-name [])))
              ;; Note the use of ds/new* here: it is required so that a :parent
              ;; entity may be specified in the instantiation of a child entity.
              child (ds/new* Child [child-name] :parent parent)]
          ;; Updating the parent and the child together occurs in a transaction.
          (ds/with-transaction
            (ds/save! (assoc parent
                        :members (conj (:children parent) child-name)))
            (ds/save! child))
          {:headers {"Content-Type" "text/plain"}
           :body "done"}))
  (GET  "/parents" []
        (let [parents (ds/query :kind Parent)]
          {:headers {"Content-Type" "text/plain"}
           :body (str (with-out-str (pprint parents))
                      "\n"
                      (with-out-str (pprint (map ds/get-key-object parents))))}))
  (GET  "/children" []
        (let [children (ds/query :kind Child)]
          {:headers {"Content-Type" "text/plain"}
           :body (str (with-out-str (pprint children))
                      "\n"
                      (with-out-str (pprint (map ds/get-key-object children))))}))
  (ANY  "*" [] {:status 404 :body "not found" :headers {"Content-Type" "text/plain"}}))
```

- `defentity` (optional keywords: `:kind`, `:before-save`, `:after-load`):
  defines an entity record type suitable for storing in the App Engine
  datastore. These entities work just like Clojure records. Internally, they
  implement an additional protocol, EntityProtocol, which provides the `save!`
  method. When defining an entity, you may specify `^{:tag :key}` metadata on any one
  field of the record, and the datastore will use this as the primary key.
  Omitting the key will make the datastore assign an automatic primary key to
  the entity. Specifying the optional `:kind` keyword (a string value) causes
  App Engine to save the entity under the given "kind" name — like a datastore
  table. This allows kinds to remain disjoint from entity record types. The
  `:before-save` and `:after-load` keywords allow specifying a hook for
  transforming the entity on its way into the datastore before it is saved, or
  on its way out of the datastore after it is read.
- `new*`: instantiates a datastore entity record. You may also use standard
  Clojure conventions to instantiate entity records, but creating entities
  destined for entity groups requires using `new*`. To put the new entity into a
  group, use the `:parent` keyword with the parent entity. Instantiating an
  entity does not automatically write it to the datastore. `new*` accepts either
  a vector of slot values or a map of slots.
- `get-key-object`: this returns the primary Key object of the given entity. For
  a newly-instantiated entity lacking an explicit primary key, this method
  returns nil. Entities properly brought under entity groups using `new*` will
  have hierarchical keys. You should rarely need to use this explicitly.
- `key-str`: this utility function returns the string representation of a Key
  object. The Key object may be given directly, or as the encoded result of call
  to `com.google.appengine.api.datastore.KeyFactory/keyToString`. In addition,
  the Key may be constructed by passing `key-str` the type (or kind string) of
  the object and its ID. This function is probably most useful for generating
  human-readable keys for storing entities in maps or memcache.
- `key-id`: this utility function returns the numeric identifier of the numeric
  key of the given entity.
- `key-name`: this utility function returns the string identifier of the string
  key of the given entity.
- `key-kind`: this utility function returns the kind, as a string, of the given
  entity.
- `save!`: calling this method on an entity writes it to the datastore, using
  the primary key returned by calling `get-key-object` on the entity. May be
  called on a sequence of entities.
- `delete!`: removes an entity. May be called on a sequence of entities.
- `retrieve <entity-record-type> <primary-key>` (optional keywords: `:parent`,
  `:kind`): this is a low-level entity retrieval function. It returns a record
  of the given type with the given primary key value. If the target entity
  belongs to an entity group, specify the parent using the optional keyword. If
  the target entity was stored with a different kind from the entity record
  type, specify the actual kind using the optional keyword. This function
  returns `nil` if the given key of the given kind does not exist.
- `exists? <entity-record-type> <primary-key>` (optional keywords the same as
  for `retrieve`): used exactly like `retrieve`, but returns `true` if the given
  entity exists and `false` otherwise.
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
- Type conversion functions: these help cast your data into a Java type which
  receives special treatment from App Engine.
  * `as-blob`: casts a byte array to `com.google.appengine.api.datastore.Blob`.
  * `as-short-blob`: casts a byte array to
    `com.google.appengine.api.datastore.ShortBlob`.
  * `as-blob-key`: casts a string to
    `com.google.appengine.api.blobstore.BlobKey`.
  * `as-text`: casts a string to `com.google.appengine.api.datastore.Text`.
  * `as-link`: casts a string to `com.google.appengine.api.datastore.Link`.

The Clojure interface to the Datastore has an additional feature: any entity
field may be marked with the `^:clj` metadata tag:

```clojure
(ds/defentity TestEntity [^{:tag :key} test-id, ^:clj some-table])
```

The values of fields marked with the `^:clj` tag will go into the datastore as
strings produced by Clojure's `prn-str` function, and they will be retrieved as
Clojure objects read by `read-string`. In other words, `^:clj` fields will be
serialized and retrieved using Clojure's reader. This is quite helpful for
dealing with types which the datastore does not support: specifically maps (not
even `java.util.HashMap` works) and sets (not even `java.util.HashSet`
works). Keep in mind, however, that these fields are stored as instances of
`com.google.appengine.api.datastore.Text`, which the datastore does not index.


### Blobstore

The `appengine-magic.services.blobstore` namespace helps with the App Engine
Blobstore service, designed for hosting large files. Note that the production
App Engine only enables the Blobstore service for applications with billing
enabled.

Using the Blobstore generally requires three components: an upload session, an
HTTP `multipart/form-data` file upload (usually initiated through an HTML form),
and an upload callback.

1. Your application must first initiate an upload session; this gives it a URL
   to use for the corresponding HTTP POST request.
2. Your application must provide a proper upload form, with the `action`
   pointing to the URL of the upload session, the `method` set to `post`, and
   `enctype` set to `multipart/form-data`; each uploaded file must have a `name`
   attribute.
3. Your application must provide an upload callback URL. App Engine will make an
   HTTP POST request to that URL once the file upload completes. This callback's
   request will contain information about the uploaded files. The callback
   should save this data in some way that makes sense for the application. The
   callback implementation must end with an invocation of the
   `callback-complete` function. Do not attempt to return a Ring response map
   from an upload handler.
4. A Ring handler which serves up a blob must end with an invocation of the
   `serve` function. Do not attempt to return a Ring response map from a
   blob-serving handler.

NB: In the REPL environment and in `dev_appserver.sh`, using the Blobstore
writes entities into the datastore: `__BlobInfo__` and
`__BlobUploadSession__`. This does not happen in the production environment.

- `upload-url <success-path>`: initializes an upload session and returns its
  URL. `success-path` is the URL of the upload callback.
- `delete! <blob-keys>`: deletes the given blobs by their keys.
- `serve <ring-request-map> <blob-key>`: modifies the given Ring request map to
  serve up the given blob.
- `callback-complete <ring-request-map> <destination>`: redirects the uploading
  HTTP client to the given destination.
- `uploaded-blobs <ring-request-map>`: returns a map of form upload name fields
  to blob keys.

This is confusing, but a Compojure example will help.

```clojure
(use 'compojure.core)

(require '[appengine-magic.core :as ae]
         '[appengine-magic.services.datastore :as ds]
         '[appengine-magic.services.blobstore :as blobs])

(ds/defentity UploadedFile [^{:tag :key} blob-key])

(defroutes upload-demo-app-handler
  ;; HTML upload form; note the upload-url call
  (GET "/upload" _
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (str "<html><body>"
                   "<form action=\""
                   (blobs/upload-url "/done")
                   "\" method=\"post\" enctype=\"multipart/form-data\">"
                   "<input type=\"file\" name=\"file1\">"
                   "<input type=\"file\" name=\"file2\">"
                   "<input type=\"file\" name=\"file3\">"
                   "<input type=\"submit\" value=\"Submit\">"
                   "</form>"
                   "</body></html>")})
  ;; success callback
  (POST "/done" req
       (let [blob-map (blobs/uploaded-blobs req)]
         (ds/save! [(UploadedFile. (.getKeyString (blob-map "file1")))
                    (UploadedFile. (.getKeyString (blob-map "file2")))
                    (UploadedFile. (.getKeyString (blob-map "file3")))])
         (blobs/callback-complete req "/list")))
  ;; a list of all uploaded files with links
  (GET "/list" _
       {:status 200
        :headers {"Content-Type" "text/html"}
        :body (apply str `["<html><body>"
                           ~@(map #(format " <a href=\"/serve/%s\">file</a>"
                                           (:blob-key %))
                                  (ds/query :kind UploadedFile))
                           "</body></html>"])})
  ;; serves the given blob by key
  (GET "/serve/:blob-key" {{:strs [blob-key]} :params :as req}
       (blobs/serve req blob-key)))

(ae/def-appengine-app upload-demo-app #'upload-demo-app-handler)
```

Note that the Blobstore API primarily allows for browser-driven file
uploads. appengine-magic includes a hack which allows an application to upload a
blob without a browser.

- `upload-hack <contents> <success-path>`: upload contents into the
  blobstore. When the upload completes, App Engine will make a request to the
  `<success-path>` URL, just like in a regular blobstore upload. This callback
  should record the blob key of the uploaded data. `<contents>` is either a
  single map, or a vector of maps, each with the following keys:
  * `:field`: the name of the imitation form field; used as keys in the result
    of `uploaded-blobs`.
  * `:filename`
  * `:bytes`: byte array of the uploaded data.


### Mail service

The `appengine-magic.services.mail` namespace provides helper functions for
sending and receiving mail in an App Engine application.

To send an mail message, construct it using `make-message` and `make-attachment`
functions, and send it using the `send` function.

To receive incoming mail, first read and understand the relevant section in
(Google's official
documentation)[http://code.google.com/appengine/docs/java/mail/receiving.html]. You
need to modify your application's `appengine-web.xml`, and you should add a
security constraint for `/_ah/mail/*` URLs in your `web.xml`. In your
application add a Ring handler for POST methods for URLs which begin with
`/_ah/mail`.

- `make-attachment <filename> <bytes>`: constructs an attachment object for a
  file with the given filename and consisting of the given bytes.
- `make-message`: this function has many keyword parameters, and constructs a
  message object. The parameters are self-explanatory: `:from`, `:to` (takes a
  string or a vector), `:subject`, `:cc` (takes a string or a vector), `:bcc`
  (takes a string or a vector), `:reply-to` (takes a string or a vector),
  `:text-body`, `:html-body`, and `:attachments` (takes a vector).
- `send <msg>`: sends the given message.
- `parse-message <ring-request-map>`: returns a Clojure record of type
  `appengine-magic.services.mail.MailMessage`. Call this function inside the
  POST handler for `/_ah/mail/*`, and it will return the message sent in the
  given HTTP request.

NB: With Compojure, the only route which seems to work in the production App
Engine for handling mail is `/_ah/mail/*`.

```clojure
(use 'compojure.core)

(require '[appengine-magic.core :as ae]
         '[appengine-magic.services.mail :as mail])

(defroutes mail-demo-app-handler
  ;; sending
  (GET "/mail" _
       (let [att1 (mail/make-attachment "hello.txt" (.getBytes "hello world"))
             att2 (mail/make-attachment "jk.txt" (.getBytes "just kidding"))
             msg (mail/make-message :from "one@example.com"
                                    :to "two@example.com"
                                    :cc ["three@example.com" "four@example.com"]
                                    :subject "Test message."
                                    :text-body "Sent from appengine-magic."
                                    :attachments [att1 att2])]
         (mail/send msg)
         {:status 200
          :headers {"Content-Type" "text/plain"}
          :body "sent"}))
  ;; receiving
  (POST "/_ah/mail/*" req
       (let [msg (mail/parse-message req)]
         ;; use the resulting MailMessage object
         {:status 200})))

(ae/def-appengine-app mail-demo-app #'mail-demo-app-handler)
```


### Task Queues service

The `appengine-magic.services.task-queues` namespace has helper functions for
using task queues. As always, read [Google's documentation on task
queues](http://code.google.com/appengine/docs/java/taskqueue/overview.html), in
particular the sections on configuring `queue.xml`, and on securing task URLs in
`web.xml`. In addition, [the section on scheduled
tasks](http://code.google.com/appengine/docs/java/config/cron.html) (`cron.xml`)
is useful.

Use the `add!` function to add a new task to a queue, and provide a callback URL
which implements the actual work performed by the task.

- `add! :url <callback-url>` (optional keywords: `:queue`, `:task-name`,
  `:join-current-transaction?`, `:params`, `:headers`, `:payload`, `:method`,
  `:countdown-ms`, `:eta-ms`, `:eta`). The `:url` keyword is required. This
  function returns a task handle object.
  * `:queue`: name of the queue to use; if omitted, uses the system default
    queue. If provided, the queue must be defined in `queue.xml`.
  * `:task-name`: an optional name for the task.
  * `:join-current-transaction?`: defaults to false. If true, and if this occurs
    inside a datastore transaction context, then only adds this task to the
    queue if the transaction commits successfully.
  * `:params`: a map of form parameter key-value pairs for the callback. Do not
    combine with the `:payload` keyword.
  * `:headers`: a map of extra HTTP headers sent to the callback.
  * `:payload`: provides data for the callback. Can be a string, a vector of the
    form `[<string> <charset>]`, or a vector of the form `[<byte-array>
    <content-type>]`.
  * `:method`: supports `:post`, `:delete`, `:get`, `:head`, and `:put`. Default
    is `:post`.
  * `:countdown-ms`, `:eta-ms`, and `:eta`: scheduling parameters. Only one of
    these may be used at a time. `:countdown-ms` schedules a task for the given
    number of milliseconds from the time the `add!` function ran. `:eta-ms`
    schedules a task for the given number of milliseconds from the beginning of
    the epoch. `:eta` schedules execution for the time given by the a
    `java.util.Date` object.
- `purge!` (optional keyword: `:queue`). Removes all tasks from the given queue.
  * `:queue`: name of the queue to use; if omitted, uses the system default
    queue.
- `delete! <task>` (optional keyword: `:queue`). Deletes the given task from the
  given queue. The task may be specified by its name or by its handle object.
  * `:queue`: name of the queue to use; if omitted, uses the system default
    queue.


### URL Fetch service

`appengine-magic.services.url-fetch` lets App Engine applications send arbitrary
HTTP requests to external services.

- `fetch <url>` (optional keywords: `:method`, `:headers`, `:payload`,
  `:allow-truncate`, `:follow-redirects`, `:deadline`).
  * `:method`: `:get` (default), `:post`, `:delete`, `:head`, or `:put`.
  * `:headers`: a map from header name (string) to value (string).
  * `:payload`: a Java byte array.
  * `:allow-truncate`: if true, allow App Engine to truncate a large response
    without an error; if false, throws an exception instead.
  * `:follow-redirects`: if true (default), follows request redirects.
  * `:deadline`: deadline for the requst, in seconds, expressed as a double.
  * `:async?`: if true, returns a future-like object. May block when derefed if
  it has not yet finished loading.


### Images service

With `appengine-magic.services.images`, an application can (1) apply simple
transformations to images, either in the blobstore or saved in byte arrays, and
(2) access blobstore images through a CDN, with limited resizing capability.

- `get-image <image-arg>`: if `image-arg` is a string or a blob key, returns an
  image reference to this blob; if `image-arg` is a byte array, returns an image
  corresponding to this byte array.
- `serving-url <blob-key>`: returns a URL pointing directly at a blob image in a
  Google content delivery network.
  * `:size`: some resized versions of the given blob are available.
  * `:crop?`: some sizes can be cropped instead of resized.
- `transform <image-arg> <transforms>`: applies one or more transformations to
  an image and returns the result as an instance of
  `com.google.appengine.api.images.Image`. `Image/getImageData` returns an array
  of bytes, useful as a response body. The `image-arg` argument can be an
  instance of `Image`, or a string blob key reference, or a byte array. The
  `transforms` argument is a vector of transformation objects, created using the
  transformation functions below.Keyword arguments:
  * `:async?`: if true, makes the `transform` function return a future-like
    object.
  * `:quality`: a value from 1 to 100.
  * `:format`: the output format, either `:jpeg` (alternatively `:jpg`) or
    `:png`.
- Transformation functions:
  * `crop* <left-x> <top-y> <right-x> <bottom-y>`: crops an image, each argument
    is a fractional value from 0.0 to 1.0.
  * `im-feeling-lucky*`: tries to automatically correct color and contrast; does
    nothing in the development environment.
  * `resize* <width> <height>`
  * `rotate* <degrees-clockwise>`
  * `horizontal-flip*`
  * `vertical-flip*`


### Channel service

App Engine has an implementation of server push through its Channel service
(`appengine-magic.services.channel`). Using it requires a combination of
client-side JavaScript event callbacks, and channel management on the
server.

Conceptually, the server maintains one or more channels associated with a client
ID (this is a small number; it is probably safest to assume only one channel per
ID). The server opens a channel, which generates a channel token. This token
must be passed to the connecting client; the client then uses the token to
receive messages from the server.

- `create-channel <client-id>`: creates a new channel and returns a token;
  JavaScript code will use this token to connect to the server.
- `make-message <client-id> <message-string>`: makes a message object destined
  for all channels associated with the given client ID.
- `send <message-object>`: sends the given message object.
- `send <client-id> <message-string>`: sends the given string to the given
  client.
- `parse-presence <ring-request-map>`: returns a ClientStatus record, containing
  two fields: `:status` and `:id`. If the client just connected, the `:status`
  is `:connected`; otherwise `:disconnected`.

NB: The current version of the Channel service does not help with channel
bookkeeping. It probably cleans up idle channels internally, but does not inform
the application of this. The application is responsible for keeping track of
active channels.

The client needs to load the JavaScript code at `/_ah/channel/jsapi`:

```html
<script src="/_ah/channel/jsapi" type="text/javascript"></script>
```

Once this library loads, the client must initiate a request in which the server
can return the channel ID. Once this is done, the rest of the client API looks
like this:

```javascript
// read this from a normal server response
var channel_token = ...;

// open a "socket" to the server
var channel = new goog.appengine.Channel(channel_token);
var socket = channel.open();

// implement these callbacks to take action when an event occurs
socket.onopen = function(evt) { var data = evt.data; ... };
socket.onmessage = function(evt) { var data = evt.data; ... };
socket.onerror = function(evt) { var data = evt.data; ... };
socket.onclose = function(evt) { var data = evt.data; ... };
```

NB: The development implementations of the Channel service just poll the server
for updates, and merely emulate server push. If you watch a browser request
console, you'll see the polling requests.



## Limitations


### Incomplete features

The following Google services are not yet tested in the REPL environment:

- Anything added in App Engine SDK 1.6.0
- Anything added in App Engine SDK 1.7.0
- Asynchronous Memcache API requests (from App Engine SDK 1.6.0)
- Pull queues (from App Engine SDK 1.5.0)
- Deferred API (from App Engine SDK 1.4.3)
- Remote API (from App Engine SDK 1.4.3)
- Files API (from App Engine SDK 1.4.3)
- Adding and removing multiple tasks from queues in a single request
- Datastore async queries
- Datastore cursors
- Compositing in the Images API
- Multitenancy (namespaces)
- Metadata queries (in the datastore API)
- Capabilities
- OAuth
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



## Contributors

Many thanks to:

* Brian Gruber
* Marko Kocić
* Conrad Barski
* Yuri Niyazov
* Alex Bolodurin
* Stefan Kamphausen
* Masashi Iizuka
* Dave Lambert
* Brian Rowe
* Tobias Raeder
* Mark Rathwell
* Jieren Chen
* Sridhar Ratnakumar
* Justin Barton
* Shawn Lewis
* Christoph Mewes
* Avishai Ish-Shalom



## License

appengine-magic is distributed under the MIT license.

## 0.4.0 (???)

* Added `appengine-magic.services.datastore/key-id`,
  `appengine-magic.services.datastore/key-name`, and
  `appengine-magic.services.datastore/key-kind`.
* Added `appengine-magic.core/appengine-app-id` and
  `appengine-magic.core/appengine-app-version` helper functions.
* **Breaking change:** moved the web application from `resources/` to `war/`. To
  adapt your application to this layout, just rename your application's
  `resources/` directory to `war/`. If you use classpath resources (with the
  `appengine-magic.core/open-resource-stream` function), move them all back to
  `resources/`.
* Added `appengine-magic.services.datastore/key-str`, to get a better handle on
  entity records' IDs.
* **Breaking change:** the result object returned by the `fetch` function of the
  URL Fetch service no longer converts header names to keywords.
* Made it possible to store datastore entities in memcache during interactive
  REPL development.
* Added a hack to allow uploads to the Blobstore service from an application,
  without involving a web browser.
* Modified the Datastore service to optionally support storing values of
  arbitrary readbly-printable Clojure types.
* Added the Images service.
* **Breaking change:** removed `appengine-magic.services.url-fetch/fetch-async`
  function and replaced its functionality with a new `:async?` keyword to
  `appengine-magic.services.url-fetch/fetch`.
* Added the Channel service.
* Made `swank.core/break` work inside Ring handlers (using middleware included
  from Ring).
* Added the ability to directly execute App Engine API calls from the REPL.
* Enabled the use of the administrative console (`/_ah/admin`), including the
  datastore viewer, from the interactive REPL environment (thanks to Yuri
  Niyazov).
* Added two new Leiningen tasks: `appengine-update` and
  `appengine-dev-appserver`. They allow maintaining multiple App Engine
  production environments with different versions (e.g., production and
  staging).
* Added support for Google App Engine SDK 1.3.8 through a custom Maven
  repository.


## 0.3.2 (2010-12-29)

* Added support for VimClojure (thanks to Alex Bolodurin).


## 0.3.1 (2010-11-30)

* Added `as-*` type conversion functions to the Datastore service.
* **Breaking change:** `appengine-magic.services.blobstore/uploaded-blobs` now
  takes a Ring request map parameter (instead of an `HttpServletRequest`). This
  is for consistency with the rest of the services API.
* Added a version of multipart-params middleware which works in App Engine
  (thanks to Conrad Barski).


## 0.3.0 (2010-11-23)

* Added `appengine-magic.core/appengine-environment-type`.
* Deprecated including the `:aot` directive in `project.clj` files.
* Added `exists?` function to the Datastore service.
* Added the Task Queues service.
* Added the Mail service.
* Added the Blobstore service.
* Speeded up production deployment by packaging compiled application .class
  files into a .jar. Eliminated the WEB-INF/classes/ directory.
* Added support for general classpath resources with the
  `appengine-magic.core/open-resource-stream` function.
* **Breaking change:** moved the web application from `resources/war/` to
  `resources/`. This provides better support for using general classpath
  resources.
* Breaking change: renamed all destructive Memcache service operations to have a
  trailing ! character; e.g., `appengine-magic.services/put` became
  `appengine-magic.services/put!`.


## 0.2.1 (2010-10-20)

* Improved the User service.
* Fixed a bug with project paths containing spaces.
* Added the URL Fetch service (thanks to Brian Gruber).
* Added testing infrastructure.
